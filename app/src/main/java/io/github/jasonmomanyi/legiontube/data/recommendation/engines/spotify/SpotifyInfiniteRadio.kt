/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 *
 * Flow is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 */

package io.github.jasonmomanyi.legiontube.data.recommendation.engines.spotify

import android.util.Log
import io.github.jasonmomanyi.legiontube.data.local.dao.SpotifyEngineDao
import io.github.jasonmomanyi.legiontube.data.model.Video

/**
 * Generates an endless queue of similar tracks for "Infinite Autoplay Radio".
 *
 * Given a seed track (e.g., the last played video), this class uses
 * [SpotifyVectorIndex] to find the most similar tracks, then applies
 * diversity filtering and shuffling to produce an engaging radio queue.
 *
 * Features:
 * - Diversity filter: max N tracks from the same channel in sequence
 * - Tiered shuffling: top-10 candidates shuffled within tier for variety
 * - Session deduplication: never repeats a track in the same radio session
 * - Auto-refill: generates next batch when queue runs low
 */
class SpotifyInfiniteRadio(
    private val vectorIndex: SpotifyVectorIndex,
    private val dao: SpotifyEngineDao,
    private val config: SpotifyEngineConfig = SpotifyEngineConfig()
) {
    companion object {
        private const val TAG = "SpotifyInfiniteRadio"
    }

    // Session state
    private val sessionPlayed = mutableSetOf<String>()
    private var lastSeedTrackId: String? = null

    /**
     * Generate a radio queue seeded from a specific track.
     *
     * @param seedTrackId  The video ID to seed similarity from.
     * @param exclude      Additional IDs to exclude (already in player queue).
     * @param limit        Number of tracks to return.
     * @return Ordered list of recommended videos for the radio queue.
     */
    suspend fun generateQueue(
        seedTrackId: String,
        exclude: Set<String> = emptySet(),
        limit: Int = config.radioNeighborCount
    ): List<Video> {
        if (!vectorIndex.isReady) {
            Log.w(TAG, "Vector index not ready, returning empty queue")
            return emptyList()
        }

        lastSeedTrackId = seedTrackId
        sessionPlayed.add(seedTrackId)

        val allExclude = sessionPlayed + exclude

        try {
            // Get nearest neighbors from vector index
            val neighbors = vectorIndex.findNearest(
                trackId = seedTrackId,
                n = limit * 2, // fetch extra for diversity filtering
                exclude = allExclude
            )

            if (neighbors.isEmpty()) {
                Log.w(TAG, "No neighbors found for seed=$seedTrackId")
                return emptyList()
            }

            // Load interaction data for metadata
            val interactions = dao.getAllInteractions()
            val interactionMap = interactions.associateBy { it.trackId }

            // Convert to Videos with diversity filtering
            val candidates = neighbors.mapNotNull { (trackId, score) ->
                val interaction = interactionMap[trackId]
                if (interaction != null) {
                    Video(
                        id = trackId,
                        title = interaction.title,
                        channelName = interaction.channelName,
                        channelId = interaction.channelId,
                        thumbnailUrl = interaction.thumbnailUrl,
                        duration = interaction.duration,
                        viewCount = 0,
                        uploadDate = ""
                    ) to score
                } else {
                    // Track has embedding but no metadata — skip
                    null
                }
            }

            // Apply diversity filter
            val diversified = applyDiversityFilter(candidates, limit)

            // Apply tiered shuffling for variety
            val shuffled = applyTieredShuffle(diversified)

            // Track these as played in the session
            shuffled.forEach { sessionPlayed.add(it.id) }

            Log.i(TAG, "Generated ${shuffled.size} radio tracks from seed=$seedTrackId")
            return shuffled

        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate radio queue", e)
            return emptyList()
        }
    }

    /**
     * Get the next batch of tracks for the radio, using the last
     * played track as the new seed.
     */
    suspend fun getNextBatch(
        currentTrackId: String,
        limit: Int = 20
    ): List<Video> {
        return generateQueue(
            seedTrackId = currentTrackId,
            limit = limit
        )
    }

    /**
     * Reset the radio session (e.g., when user starts a new radio).
     */
    fun resetSession() {
        sessionPlayed.clear()
        lastSeedTrackId = null
        Log.d(TAG, "Radio session reset")
    }

    /**
     * Check if infinite radio can generate content.
     */
    fun isAvailable(): Boolean = vectorIndex.isReady && vectorIndex.size >= 5

    // ── Diversity filtering ──

    /**
     * Limit the number of consecutive tracks from the same channel.
     * This prevents radio from becoming a single-channel playlist.
     */
    private fun applyDiversityFilter(
        candidates: List<Pair<Video, Float>>,
        limit: Int
    ): List<Video> {
        val result = mutableListOf<Video>()
        val channelCounts = mutableMapOf<String, Int>()

        for ((video, _) in candidates) {
            if (result.size >= limit) break

            val channelKey = video.channelId.ifBlank { video.channelName }
            val currentCount = channelCounts[channelKey] ?: 0

            if (currentCount < config.radioMaxSameChannel) {
                result.add(video)
                channelCounts[channelKey] = currentCount + 1
            }
        }

        return result
    }

    /**
     * Shuffle within similarity tiers to add variety while keeping
     * overall quality high.
     *
     * Tier 1 (indices 0-9): Most similar — light shuffle
     * Tier 2 (indices 10-24): Moderate similarity — medium shuffle
     * Tier 3 (indices 25+): Discovery zone — heavy shuffle
     */
    private fun applyTieredShuffle(videos: List<Video>): List<Video> {
        if (videos.size <= 3) return videos

        val result = mutableListOf<Video>()

        val tier1 = videos.take(10).toMutableList()
        val tier2 = videos.drop(10).take(15).toMutableList()
        val tier3 = videos.drop(25).toMutableList()

        // Light shuffle tier 1 (swap ~30% of positions)
        if (tier1.size > 2) {
            repeat(tier1.size / 3) {
                val i = (0 until tier1.size).random()
                val j = (0 until tier1.size).random()
                val temp = tier1[i]
                tier1[i] = tier1[j]
                tier1[j] = temp
            }
        }

        tier2.shuffle()
        tier3.shuffle()

        result.addAll(tier1)
        result.addAll(tier2)
        result.addAll(tier3)

        return result
    }
}
