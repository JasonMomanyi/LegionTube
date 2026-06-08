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

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// ═══════════════════════════════════════════════════════════════
// Room Entities — stored in the app database
// ═══════════════════════════════════════════════════════════════

/**
 * Tracks every user-item interaction for collaborative filtering.
 *
 * Weight formula:
 *   weight = (playCount * 0.5 + totalWatchPercent * 2.0 - skipCount * 0.3).coerceAtLeast(0)
 *
 * The table is capped at [MAX_INTERACTIONS] rows via LRU eviction.
 */
@Entity(
    tableName = "spotify_interactions",
    indices = [
        Index("trackId"),
        Index("userId", "trackId", unique = true)
    ]
)
data class SpotifyInteraction(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userId: String = LOCAL_USER_ID,
    val trackId: String,
    val channelId: String,
    val title: String,
    val thumbnailUrl: String = "",
    val channelName: String = "",
    val duration: Int = 0,
    val playCount: Int = 1,
    val skipCount: Int = 0,
    val likeCount: Int = 0,
    val totalWatchPercent: Float = 0f,
    val weight: Float = 1.0f,
    val lastInteractedAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val LOCAL_USER_ID = "local_user"
        const val MAX_INTERACTIONS = 2000
    }
}

/**
 * Pre-computed latent factor embeddings for fast vector similarity lookups.
 * Written by [SpotifyALSTrainer] after each model training run.
 */
@Entity(
    tableName = "spotify_embeddings",
    indices = [Index("trackId", unique = true)]
)
data class SpotifyEmbedding(
    @PrimaryKey
    val trackId: String,
    /** JSON-serialized FloatArray of latent factors, e.g. "[0.12, -0.34, ...]" */
    val factors: String,
    /** Pre-computed L2 norm for fast cosine similarity. */
    val norm: Float,
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Cached recommendation results for ultra-fast reads (< 50ms).
 * Regenerated after each ALS training run.
 */
@Entity(
    tableName = "spotify_cached_recs",
    indices = [Index("cachedAt")]
)
data class SpotifyCachedRecommendation(
    @PrimaryKey
    val trackId: String,
    val title: String = "",
    val channelName: String = "",
    val channelId: String = "",
    val thumbnailUrl: String = "",
    val duration: Int = 0,
    val viewCount: Long = 0,
    val uploadDate: String = "",
    val score: Float,
    /** Origin of this recommendation: "als", "popularity", "fallback" */
    val source: String,
    val cachedAt: Long = System.currentTimeMillis()
)

// ═══════════════════════════════════════════════════════════════
// In-memory model data classes (not persisted via Room)
// ═══════════════════════════════════════════════════════════════

/**
 * Result of an ALS training run.
 */
data class ALSModelResult(
    /** User latent factor vector. For single-user mode this is one vector. */
    val userFactors: FloatArray,
    /** Map of trackId → latent factor vector. */
    val itemFactors: Map<String, FloatArray>,
    /** Number of interactions used for training. */
    val interactionCount: Int,
    /** Training duration in milliseconds. */
    val trainingTimeMs: Long,
    /** Timestamp when this model was trained. */
    val trainedAt: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ALSModelResult) return false
        return trainedAt == other.trainedAt && interactionCount == other.interactionCount
    }

    override fun hashCode(): Int = trainedAt.hashCode()
}

/**
 * A scored recommendation candidate.
 */
data class ScoredTrack(
    val trackId: String,
    val score: Float,
    val source: String
)

/**
 * Configuration for the Spotify Algorithm Engine.
 */
data class SpotifyEngineConfig(
    /** Number of latent factors (embedding dimensions). */
    val numFactors: Int = 32,
    /** ALS regularization parameter. */
    val regularization: Float = 0.1f,
    /** Number of ALS iterations. */
    val iterations: Int = 15,
    /** Minimum interactions required before training. */
    val minInteractionsForTraining: Int = 10,
    /** How often to retrain (in hours). */
    val retrainIntervalHours: Int = 4,
    /** Max interactions to keep in the database. */
    val maxInteractions: Int = SpotifyInteraction.MAX_INTERACTIONS,
    /** Number of nearest neighbors for radio queue. */
    val radioNeighborCount: Int = 50,
    /** Max tracks from same channel in radio queue sequence. */
    val radioMaxSameChannel: Int = 3
)

/**
 * Engine state snapshot for diagnostics / UI.
 */
data class SpotifyEngineState(
    val isEnabled: Boolean = false,
    val isModelReady: Boolean = false,
    val totalInteractions: Int = 0,
    val embeddingCount: Int = 0,
    val lastTrainedAt: Long = 0L,
    val lastTrainingDurationMs: Long = 0L,
    val isTraining: Boolean = false,
    val lastError: String? = null
)
