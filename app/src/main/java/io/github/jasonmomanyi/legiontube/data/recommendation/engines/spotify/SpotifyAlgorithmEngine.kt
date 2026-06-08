/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 *
 * Flow is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * The Spotify Algorithm Engine is the intellectual property of the Flow project.
 * Inspired by spotify-recommender-systems (anthonyli358) and the Implicit library.
 */

package io.github.jasonmomanyi.legiontube.data.recommendation.engines.spotify

import android.content.Context
import android.util.Log
import io.github.jasonmomanyi.legiontube.data.local.AppDatabase
import io.github.jasonmomanyi.legiontube.data.local.dao.SpotifyEngineDao
import io.github.jasonmomanyi.legiontube.data.model.Video
import io.github.jasonmomanyi.legiontube.data.recommendation.InteractionType
import io.github.jasonmomanyi.legiontube.data.recommendation.engines.base.RecommendationEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Spotify Algorithm Engine — Collaborative Filtering Recommendation Engine.
 *
 * A second recommendation engine that runs alongside [LegionTubeNeuroEngine],
 * providing two core features:
 *
 * 1. **"For You"** — Personalized recommendations using ALS matrix factorization
 *    on implicit feedback (play counts, watch percent, skips).
 *
 * 2. **"Infinite Autoplay Radio"** — An endless queue of contextually similar
 *    tracks generated via cosine similarity in the latent factor space.
 *
 * Architecture:
 * ```
 * User Interaction → SpotifyInteractionTracker → Room DB
 *                                                    ↓
 *                          SpotifyALSTrainer (background worker)
 *                                                    ↓
 *                          SpotifyVectorIndex (in-memory NN index)
 *                                ↓                        ↓
 *                    For You Recs              Infinite Radio Queue
 * ```
 *
 * The engine is **off by default** and can be toggled in Settings.
 * When disabled, it performs zero work and consumes zero resources.
 *
 * @see <a href="https://github.com/anthonyli358/spotify-recommender-systems">Reference implementation</a>
 */
class SpotifyAlgorithmEngine(
    private val appContext: Context
) : RecommendationEngine {

    companion object {
        private const val TAG = "SpotifyAlgoEngine"

        @Volatile
        private var instance: SpotifyAlgorithmEngine? = null

        fun getInstance(context: Context): SpotifyAlgorithmEngine {
            return instance ?: synchronized(this) {
                instance ?: SpotifyAlgorithmEngine(context.applicationContext).also {
                    instance = it
                }
            }
        }

        fun getInstanceOrNull(): SpotifyAlgorithmEngine? = instance
    }

    override val engineId: String = "spotify_algo"
    override val displayName: String = "Spotify Algorithm"

    @Volatile
    override var isEnabled: Boolean = false
        private set

    // ── Module instances ──
    private lateinit var dao: SpotifyEngineDao
    private lateinit var tracker: SpotifyInteractionTracker
    private lateinit var trainer: SpotifyALSTrainer
    private lateinit var vectorIndex: SpotifyVectorIndex
    private lateinit var infiniteRadio: SpotifyInfiniteRadio
    private lateinit var fallbacks: SpotifyFallbacks

    private val config = SpotifyEngineConfig()
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val trainMutex = Mutex()

    // Last successful model (retained as fallback if next training fails)
    @Volatile
    private var lastModel: ALSModelResult? = null

    @Volatile
    private var isInitialized = false

    @Volatile
    private var isTraining = false

    @Volatile
    private var lastError: String? = null

    // ═══════════════════════════════════════════════════
    // PUBLIC API — RecommendationEngine interface
    // ═══════════════════════════════════════════════════

    override suspend fun initialize() {
        if (isInitialized) return

        try {
            val db = AppDatabase.getDatabase(appContext)
            dao = db.spotifyEngineDao()
            tracker = SpotifyInteractionTracker(dao)
            trainer = SpotifyALSTrainer(dao, config)
            vectorIndex = SpotifyVectorIndex(dao)
            infiniteRadio = SpotifyInfiniteRadio(vectorIndex, dao, config)
            fallbacks = SpotifyFallbacks(dao, config)

            // Try to load existing embeddings into the vector index
            vectorIndex.rebuild()

            isInitialized = true
            lastError = null
            Log.i(TAG, "Engine initialized (embeddings=${vectorIndex.size})")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize engine", e)
            lastError = "Initialization failed: ${e.message}"
        }
    }

    /**
     * Enable or disable the engine.
     * When enabling for the first time, triggers an initial model training.
     */
    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        if (enabled && isInitialized && !vectorIndex.isReady) {
            // First activation — trigger initial training
            engineScope.launch {
                triggerRetrain()
            }
        }
        Log.i(TAG, "Engine ${if (enabled) "enabled" else "disabled"}")
    }

    override suspend fun getForYouRecommendations(limit: Int): List<Video> {
        if (!isEnabled || !isInitialized) return emptyList()

        return try {
            val model = lastModel

            if (model != null && vectorIndex.isReady) {
                // Use ALS model: score all items by user factor dot product
                val scored = vectorIndex.findNearestToVector(
                    userFactors = model.userFactors,
                    n = limit
                )

                if (scored.isNotEmpty()) {
                    // Get interaction metadata to build Video objects
                    val interactions = withContext(Dispatchers.IO) {
                        dao.getAllInteractions()
                    }
                    val interactionMap = interactions.associateBy { it.trackId }

                    val videos = scored.mapNotNull { (trackId, _) ->
                        interactionMap[trackId]?.let { interaction ->
                            Video(
                                id = trackId,
                                title = interaction.title,
                                channelName = interaction.channelName,
                                channelId = interaction.channelId,
                                thumbnailUrl = interaction.thumbnailUrl,
                                duration = interaction.duration,
                                viewCount = 0,
                                uploadDate = ""
                            )
                        }
                    }

                    if (videos.isNotEmpty()) {
                        // Cache for fallback
                        fallbacks.cacheRecommendations(videos, "als")
                        return videos
                    }
                }
            }

            // Fallback path
            fallbacks.getForYouFallback(limit)
        } catch (e: Exception) {
            Log.e(TAG, "getForYouRecommendations failed", e)
            lastError = "Recommendations failed: ${e.message}"
            try { fallbacks.getForYouFallback(limit) } catch (_: Exception) { emptyList() }
        }
    }

    override suspend fun getInfiniteRadioQueue(
        seedTrackId: String,
        exclude: Set<String>,
        limit: Int
    ): List<Video> {
        if (!isEnabled || !isInitialized) return emptyList()

        return try {
            val radioTracks = infiniteRadio.generateQueue(seedTrackId, exclude, limit)

            if (radioTracks.isNotEmpty()) {
                radioTracks
            } else {
                // Fallback to popularity-based radio
                fallbacks.getRadioFallback(seedTrackId, exclude, limit)
            }
        } catch (e: Exception) {
            Log.e(TAG, "getInfiniteRadioQueue failed", e)
            try { fallbacks.getRadioFallback(seedTrackId, exclude, limit) } catch (_: Exception) { emptyList() }
        }
    }

    override suspend fun recordInteraction(
        video: Video,
        type: InteractionType,
        percentWatched: Float
    ) {
        if (!isEnabled || !isInitialized) return

        try {
            tracker.recordInteraction(video, type, percentWatched)
        } catch (e: Exception) {
            Log.e(TAG, "recordInteraction failed", e)
            // Never throw — fire and forget
        }
    }

    override suspend fun shutdown() {
        try {
            engineScope.cancel()
            isInitialized = false
            Log.i(TAG, "Engine shut down")
        } catch (e: Exception) {
            Log.e(TAG, "Shutdown error", e)
        }
    }

    // ═══════════════════════════════════════════════════
    // TRAINING API
    // ═══════════════════════════════════════════════════

    /**
     * Trigger a model retrain. Safe to call from any context.
     *
     * If training is already in progress, this is a no-op.
     * If training fails, the last successful model is retained.
     */
    suspend fun triggerRetrain() {
        if (!isInitialized) return
        if (!trainMutex.tryLock()) {
            Log.d(TAG, "Training already in progress, skipping")
            return
        }

        try {
            isTraining = true
            Log.i(TAG, "Starting model retrain...")

            val newModel = trainer.train()

            if (newModel != null) {
                lastModel = newModel
                vectorIndex.buildFromModel(newModel)
                lastError = null

                Log.i(TAG, "Model retrain complete: " +
                        "${newModel.interactionCount} interactions, " +
                        "${newModel.itemFactors.size} items, " +
                        "${newModel.trainingTimeMs}ms")

                // Generate and cache fresh recommendations
                val freshRecs = getForYouRecommendations(30)
                if (freshRecs.isNotEmpty()) {
                    fallbacks.cacheRecommendations(freshRecs, "als")
                }
            } else {
                Log.w(TAG, "Training returned null (insufficient data or error)")
                if (lastModel == null) {
                    lastError = "Not enough watch history to generate recommendations"
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Model retrain failed", e)
            lastError = "Training failed: ${e.message}"
            // lastModel is retained from previous successful run
        } finally {
            isTraining = false
            trainMutex.unlock()
        }
    }

    /**
     * Bootstrap the engine from existing watch history.
     * Called when the user first enables the engine.
     */
    suspend fun bootstrapFromHistory(watchHistory: List<Video>) {
        if (!isInitialized) return
        if (watchHistory.isEmpty()) return

        Log.i(TAG, "Bootstrapping from ${watchHistory.size} watch history entries")

        for (video in watchHistory) {
            tracker.recordInteraction(
                video = video,
                type = InteractionType.WATCHED,
                percentWatched = 0.7f // Assume reasonable completion for history
            )
        }

        // Trigger training after bootstrap
        triggerRetrain()
    }

    /**
     * Reset the infinite radio session.
     */
    fun resetRadioSession() {
        if (isInitialized) {
            infiniteRadio.resetSession()
        }
    }

    /**
     * Check if infinite radio can generate content.
     */
    fun isRadioAvailable(): Boolean {
        return isEnabled && isInitialized && infiniteRadio.isAvailable()
    }

    // ═══════════════════════════════════════════════════
    // DIAGNOSTICS API
    // ═══════════════════════════════════════════════════

    /**
     * Get a snapshot of the engine's current state for diagnostics.
     */
    suspend fun getState(): SpotifyEngineState {
        return try {
            SpotifyEngineState(
                isEnabled = isEnabled,
                isModelReady = vectorIndex.isReady,
                totalInteractions = if (isInitialized) tracker.getInteractionCount() else 0,
                embeddingCount = vectorIndex.size,
                lastTrainedAt = lastModel?.trainedAt ?: 0L,
                lastTrainingDurationMs = lastModel?.trainingTimeMs ?: 0L,
                isTraining = isTraining,
                lastError = lastError
            )
        } catch (e: Exception) {
            SpotifyEngineState(lastError = e.message)
        }
    }

    /**
     * Clear all engine data and reset to fresh state.
     */
    suspend fun resetEngine() {
        if (!isInitialized) return

        try {
            withContext(Dispatchers.IO) {
                dao.deleteAllInteractions()
                dao.deleteAllEmbeddings()
                dao.deleteAllCachedRecommendations()
            }
            lastModel = null
            vectorIndex.rebuild()
            infiniteRadio.resetSession()
            lastError = null
            Log.i(TAG, "Engine reset complete")
        } catch (e: Exception) {
            Log.e(TAG, "Engine reset failed", e)
        }
    }
}
