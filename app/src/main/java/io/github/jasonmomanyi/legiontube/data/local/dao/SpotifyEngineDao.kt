/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 *
 * Flow is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 */

package io.github.jasonmomanyi.legiontube.data.local.dao

import androidx.room.*
import io.github.jasonmomanyi.legiontube.data.recommendation.engines.spotify.SpotifyCachedRecommendation
import io.github.jasonmomanyi.legiontube.data.recommendation.engines.spotify.SpotifyEmbedding
import io.github.jasonmomanyi.legiontube.data.recommendation.engines.spotify.SpotifyInteraction

/**
 * Room DAO for all Spotify Algorithm Engine database operations.
 *
 * All queries are designed for indexed, low-latency access.
 * Write operations use REPLACE to handle upserts cleanly.
 */
@Dao
interface SpotifyEngineDao {

    // ── Interactions ──

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertInteraction(interaction: SpotifyInteraction)

    @Query("SELECT * FROM spotify_interactions WHERE userId = :userId AND trackId = :trackId LIMIT 1")
    suspend fun getInteraction(userId: String, trackId: String): SpotifyInteraction?

    @Query("SELECT * FROM spotify_interactions WHERE userId = :userId ORDER BY weight DESC")
    suspend fun getAllInteractions(userId: String = SpotifyInteraction.LOCAL_USER_ID): List<SpotifyInteraction>

    @Query("SELECT COUNT(*) FROM spotify_interactions WHERE userId = :userId")
    suspend fun getInteractionCount(userId: String = SpotifyInteraction.LOCAL_USER_ID): Int

    @Query("""
        SELECT * FROM spotify_interactions 
        WHERE userId = :userId 
        ORDER BY weight DESC 
        LIMIT :limit
    """)
    suspend fun getTopInteractions(
        userId: String = SpotifyInteraction.LOCAL_USER_ID,
        limit: Int = 500
    ): List<SpotifyInteraction>

    /**
     * Delete oldest interactions beyond the cap to prevent unbounded growth.
     * Keeps the [keepCount] most recently interacted items.
     */
    @Query("""
        DELETE FROM spotify_interactions 
        WHERE userId = :userId AND id NOT IN (
            SELECT id FROM spotify_interactions 
            WHERE userId = :userId 
            ORDER BY lastInteractedAt DESC 
            LIMIT :keepCount
        )
    """)
    suspend fun evictOldInteractions(
        userId: String = SpotifyInteraction.LOCAL_USER_ID,
        keepCount: Int = SpotifyInteraction.MAX_INTERACTIONS
    )

    @Query("DELETE FROM spotify_interactions")
    suspend fun deleteAllInteractions()

    // ── Embeddings ──

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEmbedding(embedding: SpotifyEmbedding)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEmbeddings(embeddings: List<SpotifyEmbedding>)

    @Query("SELECT * FROM spotify_embeddings WHERE trackId = :trackId LIMIT 1")
    suspend fun getEmbedding(trackId: String): SpotifyEmbedding?

    @Query("SELECT * FROM spotify_embeddings")
    suspend fun getAllEmbeddings(): List<SpotifyEmbedding>

    @Query("SELECT COUNT(*) FROM spotify_embeddings")
    suspend fun getEmbeddingCount(): Int

    @Query("DELETE FROM spotify_embeddings")
    suspend fun deleteAllEmbeddings()

    // ── Cached Recommendations ──

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCachedRecommendations(recs: List<SpotifyCachedRecommendation>)

    @Query("""
        SELECT * FROM spotify_cached_recs 
        ORDER BY score DESC 
        LIMIT :limit
    """)
    suspend fun getCachedRecommendations(limit: Int = 30): List<SpotifyCachedRecommendation>

    @Query("SELECT COUNT(*) FROM spotify_cached_recs")
    suspend fun getCachedRecommendationCount(): Int

    @Query("DELETE FROM spotify_cached_recs")
    suspend fun deleteAllCachedRecommendations()

    /**
     * Delete cached recs older than [maxAgeMs] milliseconds.
     */
    @Query("DELETE FROM spotify_cached_recs WHERE cachedAt < :cutoffTimestamp")
    suspend fun evictStaleCachedRecs(cutoffTimestamp: Long)

    // ── Composite queries for training ──

    /**
     * Get all unique track IDs that have interactions, ordered by weight.
     * Used to build the item index during ALS training.
     */
    @Query("""
        SELECT DISTINCT trackId FROM spotify_interactions 
        WHERE userId = :userId AND weight > 0 
        ORDER BY weight DESC
    """)
    suspend fun getInteractedTrackIds(
        userId: String = SpotifyInteraction.LOCAL_USER_ID
    ): List<String>
}
