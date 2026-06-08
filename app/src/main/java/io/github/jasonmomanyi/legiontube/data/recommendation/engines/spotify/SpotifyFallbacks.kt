/*
 * Copyright (C) 2025-2026 LegionTube | JasonMomanyi
 *
 * This file is part of LegionTube (https://github.com/JasonMomanyi/LegionTube).
 *
 * LegionTube is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 */

package io.github.jasonmomanyi.legion.data.recommendation.engines.spotify

import android.util.Log
import io.github.jasonmomanyi.legiontube.data.local.dao.SpotifyEngineDao
import io.github.jasonmomanyi.legiontube.data.model.Video

/**
 * Fallback recommendation provider for cold-start and error scenarios.
 *
 * This class ensures the Spotify Algorithm Engine always returns
 * something useful, even when:
 * - The user is brand new (< 10 interactions → cold start)
 * - The ALS model hasn't been trained yet
 * - The vector index is empty or corrupt
 * - An unexpected exception occurs in the recommendation pipeline
 *
 * Fallback strategy (priority order):
 * 1. Cached recommendations from last successful training run
 * 2. Most-played tracks from interaction history (popularity fallback)
 * 3. Empty list (caller should fall back to YouTube related/trending)
 */
class SpotifyFallbacks(
    private val dao: SpotifyEngineDao,
    private val config: SpotifyEngineConfig = SpotifyEngineConfig()
) {
    companion object {
        private const val TAG = "SpotifyFallbacks"
        private const val CACHE_MAX_AGE_MS = 24 * 60 * 60 * 1000L // 24 hours
    }

    /**
     * Check if we're in a cold-start situation.
     */
    suspend fun isColdStart(): Boolean {
        return try {
            dao.getInteractionCount() < config.minInteractionsForTraining
        } catch (e: Exception) {
            true
        }
    }

    /**
     * Get fallback "For You" recommendations.
     *
     * @param limit Max results to return.
     * @return List of recommended videos from cache or popularity.
     */
    suspend fun getForYouFallback(limit: Int = 30): List<Video> {
        // Strategy 1: Try cached recommendations
        try {
            val cached = dao.getCachedRecommendations(limit)
            if (cached.isNotEmpty()) {
                Log.d(TAG, "Serving ${cached.size} cached recommendations")
                return cached.map { rec ->
                    Video(
                        id = rec.trackId,
                        title = rec.title,
                        channelName = rec.channelName,
                        channelId = rec.channelId,
                        thumbnailUrl = rec.thumbnailUrl,
                        duration = rec.duration,
                        viewCount = rec.viewCount,
                        uploadDate = rec.uploadDate
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read cached recommendations", e)
        }

        // Strategy 2: Popularity fallback — most-interacted tracks
        try {
            val topInteractions = dao.getTopInteractions(limit = limit)
            if (topInteractions.isNotEmpty()) {
                Log.d(TAG, "Serving ${topInteractions.size} popularity-based recommendations")
                return topInteractions.map { interaction ->
                    Video(
                        id = interaction.trackId,
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
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read interaction history for fallback", e)
        }

        // Strategy 3: Empty — caller should use YouTube trending/related
        Log.w(TAG, "All fallback strategies exhausted, returning empty")
        return emptyList()
    }

    /**
     * Get fallback tracks for infinite radio.
     * Uses most-played tracks similar to the seed track's channel.
     *
     * @param seedTrackId  The seed track for context.
     * @param exclude      Tracks to exclude.
     * @param limit        Max results.
     */
    suspend fun getRadioFallback(
        seedTrackId: String,
        exclude: Set<String> = emptySet(),
        limit: Int = 20
    ): List<Video> {
        try {
            val seedInteraction = dao.getInteraction(
                SpotifyInteraction.LOCAL_USER_ID,
                seedTrackId
            )

            val allInteractions = dao.getTopInteractions(limit = limit * 3)

            // Prefer same channel, then same genre signals
            val candidates = allInteractions
                .filter { it.trackId !in exclude && it.trackId != seedTrackId }
                .sortedWith(
                    compareByDescending<SpotifyInteraction> {
                        // Boost same-channel tracks
                        if (seedInteraction != null && it.channelId == seedInteraction.channelId) 1 else 0
                    }.thenByDescending { it.weight }
                )
                .take(limit)

            if (candidates.isNotEmpty()) {
                Log.d(TAG, "Radio fallback: ${candidates.size} tracks from interaction history")
                return candidates.map { interaction ->
                    Video(
                        id = interaction.trackId,
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
        } catch (e: Exception) {
            Log.e(TAG, "Radio fallback failed", e)
        }

        return emptyList()
    }

    /**
     * Cache recommendation results for future fallback use.
     * Called after each successful ALS training run.
     */
    suspend fun cacheRecommendations(recommendations: List<Video>, source: String = "als") {
        try {
            // Evict stale cache first
            val cutoff = System.currentTimeMillis() - CACHE_MAX_AGE_MS
            dao.evictStaleCachedRecs(cutoff)

            val cached = recommendations.mapIndexed { index, video ->
                SpotifyCachedRecommendation(
                    trackId = video.id,
                    title = video.title,
                    channelName = video.channelName,
                    channelId = video.channelId,
                    thumbnailUrl = video.thumbnailUrl,
                    duration = video.duration,
                    viewCount = video.viewCount,
                    uploadDate = video.uploadDate,
                    score = 1.0f - (index * 0.01f), // preserve ordering
                    source = source
                )
            }

            dao.deleteAllCachedRecommendations()
            dao.upsertCachedRecommendations(cached)
            Log.d(TAG, "Cached ${cached.size} recommendations (source=$source)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cache recommendations", e)
        }
    }
}
