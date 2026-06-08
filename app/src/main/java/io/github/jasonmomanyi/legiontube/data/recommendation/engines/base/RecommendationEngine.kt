/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 *
 * Flow is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 */

package io.github.jasonmomanyi.legiontube.data.recommendation.engines.base

import io.github.jasonmomanyi.legiontube.data.model.Video
import io.github.jasonmomanyi.legiontube.data.recommendation.InteractionType

/**
 * Common interface for all recommendation engines in Flow.
 *
 * Engines are independently toggleable and run asynchronously.
 * The [EngineRegistry] orchestrates which engines are active and
 * routes API calls to them.
 */
interface RecommendationEngine {

    /** Unique identifier for this engine (e.g. "flow_neuro", "spotify_algo"). */
    val engineId: String

    /** Human-readable display name shown in Settings. */
    val displayName: String

    /** Whether this engine is currently enabled by the user. */
    val isEnabled: Boolean

    /**
     * Initialize the engine: load persisted state, warm caches, etc.
     * Must be safe to call multiple times (idempotent).
     */
    suspend fun initialize()

    /**
     * Generate personalized "For You" recommendations.
     *
     * @param limit Maximum number of recommendations to return.
     * @return Ordered list of recommended videos, best first.
     *         Returns empty list (never throws) if unavailable.
     */
    suspend fun getForYouRecommendations(limit: Int = 30): List<Video>

    /**
     * Generate an infinite radio queue seeded from a track.
     *
     * @param seedTrackId   The video ID to seed similarity from.
     * @param exclude       Set of video IDs already played this session.
     * @param limit         Number of tracks to return in this batch.
     * @return Ordered list of similar tracks. Returns empty on failure.
     */
    suspend fun getInfiniteRadioQueue(
        seedTrackId: String,
        exclude: Set<String> = emptySet(),
        limit: Int = 50
    ): List<Video>

    /**
     * Record a user interaction for future model training.
     * This MUST be non-blocking and fire-and-forget.
     *
     * @param video           The video interacted with.
     * @param type            The type of interaction.
     * @param percentWatched  Fraction of the video watched (0.0–1.0).
     */
    suspend fun recordInteraction(
        video: Video,
        type: InteractionType,
        percentWatched: Float = 0f
    )

    /**
     * Gracefully shut down the engine: cancel pending work, flush state.
     */
    suspend fun shutdown()
}
