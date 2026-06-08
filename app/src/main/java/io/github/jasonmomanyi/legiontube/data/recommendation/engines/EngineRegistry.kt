/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 *
 * Flow is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 */

package io.github.jasonmomanyi.legiontube.data.recommendation.engines

import android.content.Context
import android.util.Log
import io.github.jasonmomanyi.legiontube.data.model.Video
import io.github.jasonmomanyi.legiontube.data.recommendation.InteractionType
import io.github.jasonmomanyi.legiontube.data.recommendation.engines.base.RecommendationEngine
import io.github.jasonmomanyi.legiontube.data.recommendation.engines.spotify.SpotifyAlgorithmEngine

/**
 * Central registry that manages all recommendation engines.
 *
 * Routes API calls to active engines and handles blending results
 * when multiple engines are enabled simultaneously.
 *
 * Currently registered engines:
 * - **LegionTubeNeuroEngine** (built-in, always active for home feed ranking)
 * - **SpotifyAlgorithmEngine** (opt-in, provides "For You" and infinite radio)
 */
class EngineRegistry private constructor(private val context: Context) {

    companion object {
        private const val TAG = "EngineRegistry"

        @Volatile
        private var instance: EngineRegistry? = null

        fun getInstance(context: Context): EngineRegistry {
            return instance ?: synchronized(this) {
                instance ?: EngineRegistry(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    private val engines = mutableMapOf<String, RecommendationEngine>()

    /**
     * Register and initialize the Spotify Algorithm Engine.
     *
     * @param enabled Whether the engine should start enabled.
     */
    suspend fun registerSpotifyEngine(enabled: Boolean = false) {
        try {
            val engine = SpotifyAlgorithmEngine.getInstance(context)
            engine.initialize()
            engine.setEnabled(enabled)
            engines[engine.engineId] = engine
            Log.i(TAG, "Registered engine: ${engine.displayName} (enabled=$enabled)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register Spotify engine", e)
        }
    }

    /**
     * Get the Spotify Algorithm Engine instance, or null if not registered.
     */
    fun getSpotifyEngine(): SpotifyAlgorithmEngine? {
        return engines["spotify_algo"] as? SpotifyAlgorithmEngine
    }

    /**
     * Get all registered engines.
     */
    fun getAllEngines(): List<RecommendationEngine> = engines.values.toList()

    /**
     * Get only currently enabled engines.
     */
    fun getActiveEngines(): List<RecommendationEngine> =
        engines.values.filter { it.isEnabled }

    /**
     * Record an interaction across all active engines.
     * Non-blocking, fire-and-forget.
     */
    suspend fun recordInteraction(
        video: Video,
        type: InteractionType,
        percentWatched: Float = 0f
    ) {
        for (engine in getActiveEngines()) {
            try {
                engine.recordInteraction(video, type, percentWatched)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to record interaction on ${engine.engineId}", e)
            }
        }
    }

    /**
     * Get "For You" recommendations from all active engines, blended.
     *
     * If multiple engines return results, they are interleaved with
     * the Spotify engine taking priority for its items.
     */
    suspend fun getForYouRecommendations(limit: Int = 30): List<Video> {
        val allResults = mutableListOf<Video>()
        val seenIds = mutableSetOf<String>()

        for (engine in getActiveEngines()) {
            try {
                val recs = engine.getForYouRecommendations(limit)
                for (video in recs) {
                    if (video.id !in seenIds) {
                        allResults.add(video)
                        seenIds.add(video.id)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get recs from ${engine.engineId}", e)
            }
        }

        return allResults.take(limit)
    }

    /**
     * Get infinite radio queue. Uses the first engine that can produce results.
     */
    suspend fun getInfiniteRadioQueue(
        seedTrackId: String,
        exclude: Set<String> = emptySet(),
        limit: Int = 50
    ): List<Video> {
        for (engine in getActiveEngines()) {
            try {
                val queue = engine.getInfiniteRadioQueue(seedTrackId, exclude, limit)
                if (queue.isNotEmpty()) return queue
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get radio queue from ${engine.engineId}", e)
            }
        }
        return emptyList()
    }

    /**
     * Check if any engine can provide infinite radio.
     */
    fun isInfiniteRadioAvailable(): Boolean {
        return getActiveEngines().any { engine ->
            engine is SpotifyAlgorithmEngine && engine.isRadioAvailable()
        }
    }

    /**
     * Shutdown all engines gracefully.
     */
    suspend fun shutdownAll() {
        for (engine in engines.values) {
            try {
                engine.shutdown()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to shutdown ${engine.engineId}", e)
            }
        }
        engines.clear()
    }
}
