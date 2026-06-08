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
import io.github.jasonmomanyi.legiontube.data.recommendation.InteractionType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Non-blocking interaction tracker for the Spotify Algorithm Engine.
 *
 * Records all user-video interactions (play, skip, like, watch progress)
 * into the Room database for later consumption by [SpotifyALSTrainer].
 *
 * Design constraints:
 * - All writes are fire-and-forget on a background dispatcher.
 * - Never blocks the main thread or the caller coroutine.
 * - Automatically evicts oldest interactions beyond [SpotifyInteraction.MAX_INTERACTIONS].
 * - Weight is recomputed on every interaction update.
 */
class SpotifyInteractionTracker(
    private val dao: SpotifyEngineDao
) {
    companion object {
        private const val TAG = "SpotifyTracker"
    }

    private val writeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Record a user interaction with a video.
     *
     * This is safe to call from any coroutine context — it dispatches
     * the actual write to a fire-and-forget background scope.
     */
    fun recordInteraction(
        video: Video,
        type: InteractionType,
        percentWatched: Float = 0f
    ) {
        if (video.id.isBlank()) return

        writeScope.launch {
            try {
                val existing = dao.getInteraction(
                    SpotifyInteraction.LOCAL_USER_ID,
                    video.id
                )

                val updated = if (existing != null) {
                    updateExistingInteraction(existing, type, percentWatched)
                } else {
                    createNewInteraction(video, type, percentWatched)
                }

                dao.upsertInteraction(updated)

                // Evict old interactions if we've exceeded the cap
                val count = dao.getInteractionCount()
                if (count > SpotifyInteraction.MAX_INTERACTIONS) {
                    dao.evictOldInteractions(
                        keepCount = SpotifyInteraction.MAX_INTERACTIONS
                    )
                    Log.d(TAG, "Evicted old interactions, kept ${SpotifyInteraction.MAX_INTERACTIONS}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to record interaction for ${video.id}", e)
                // Never throw — fire-and-forget
            }
        }
    }

    /**
     * Get the total number of tracked interactions.
     */
    suspend fun getInteractionCount(): Int {
        return try {
            dao.getInteractionCount()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get interaction count", e)
            0
        }
    }

    /**
     * Get all interactions as a list, ordered by weight descending.
     * Used by [SpotifyALSTrainer] to build the training matrix.
     */
    suspend fun getAllInteractions(): List<SpotifyInteraction> {
        return try {
            dao.getAllInteractions()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get all interactions", e)
            emptyList()
        }
    }

    /**
     * Get top N interactions by weight.
     */
    suspend fun getTopInteractions(limit: Int = 500): List<SpotifyInteraction> {
        return try {
            dao.getTopInteractions(limit = limit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get top interactions", e)
            emptyList()
        }
    }

    /**
     * Clear all interaction data (for engine reset).
     */
    suspend fun clearAll() {
        try {
            dao.deleteAllInteractions()
            Log.i(TAG, "All interactions cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear interactions", e)
        }
    }

    // ── Private helpers ──

    private fun createNewInteraction(
        video: Video,
        type: InteractionType,
        percentWatched: Float
    ): SpotifyInteraction {
        val now = System.currentTimeMillis()
        val playCount = if (type == InteractionType.CLICK || type == InteractionType.WATCHED) 1 else 0
        val skipCount = if (type == InteractionType.SKIPPED) 1 else 0
        val likeCount = if (type == InteractionType.LIKED) 1 else 0
        val watchPct = if (type == InteractionType.WATCHED) percentWatched else 0f

        return SpotifyInteraction(
            trackId = video.id,
            channelId = video.channelId,
            title = video.title,
            thumbnailUrl = video.thumbnailUrl,
            channelName = video.channelName,
            duration = video.duration,
            playCount = playCount,
            skipCount = skipCount,
            likeCount = likeCount,
            totalWatchPercent = watchPct,
            weight = computeWeight(playCount, skipCount, likeCount, watchPct),
            lastInteractedAt = now,
            createdAt = now
        )
    }

    private fun updateExistingInteraction(
        existing: SpotifyInteraction,
        type: InteractionType,
        percentWatched: Float
    ): SpotifyInteraction {
        val newPlayCount = existing.playCount + when (type) {
            InteractionType.CLICK, InteractionType.WATCHED -> 1
            else -> 0
        }
        val newSkipCount = existing.skipCount + when (type) {
            InteractionType.SKIPPED -> 1
            else -> 0
        }
        val newLikeCount = existing.likeCount + when (type) {
            InteractionType.LIKED -> 1
            else -> 0
        }
        // Keep the maximum watch percent ever achieved
        val newWatchPct = when (type) {
            InteractionType.WATCHED -> maxOf(existing.totalWatchPercent, percentWatched)
            else -> existing.totalWatchPercent
        }

        return existing.copy(
            playCount = newPlayCount,
            skipCount = newSkipCount,
            likeCount = newLikeCount,
            totalWatchPercent = newWatchPct,
            weight = computeWeight(newPlayCount, newSkipCount, newLikeCount, newWatchPct),
            lastInteractedAt = System.currentTimeMillis()
        )
    }

    /**
     * Compute the implicit feedback weight for an interaction.
     *
     * Formula inspired by the Spotify/Implicit library approach:
     *   weight = playCount * 0.5 + watchPercent * 2.0 + likeBonus - skipPenalty
     *
     * Higher weight = stronger positive signal.
     */
    private fun computeWeight(
        playCount: Int,
        skipCount: Int,
        likeCount: Int,
        watchPercent: Float
    ): Float {
        val playSignal = playCount * 0.5f
        val watchSignal = watchPercent * 2.0f
        val likeBonus = likeCount * 1.5f
        val skipPenalty = skipCount * 0.3f

        return (playSignal + watchSignal + likeBonus - skipPenalty).coerceAtLeast(0f)
    }
}
