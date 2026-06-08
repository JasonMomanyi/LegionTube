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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

/**
 * Annoy-inspired in-memory vector index for nearest-neighbor search.
 *
 * Loads item embeddings from Room and builds a brute-force cosine
 * similarity index optimized for the typical on-device scale
 * (< 2000 items where brute force with precomputed norms is faster
 * than tree-based approaches).
 *
 * Query latency target: < 10ms for 50 nearest neighbors at 2000 items.
 *
 * For larger scales (> 5000 items), this could be extended with
 * random projection trees (full Annoy implementation), but that
 * complexity is unnecessary at current scale.
 */
class SpotifyVectorIndex(
    private val dao: SpotifyEngineDao
) {
    companion object {
        private const val TAG = "SpotifyVectorIndex"
    }

    // In-memory index: trackId → (factors, precomputed norm)
    private var index: Map<String, IndexEntry> = emptyMap()

    @Volatile
    var isReady: Boolean = false
        private set

    val size: Int get() = index.size

    /**
     * Rebuild the index from persisted embeddings.
     * Call this after each ALS training cycle completes.
     */
    suspend fun rebuild() = withContext(Dispatchers.Default) {
        try {
            val embeddings = withContext(Dispatchers.IO) {
                dao.getAllEmbeddings()
            }

            if (embeddings.isEmpty()) {
                Log.w(TAG, "No embeddings found, index is empty")
                index = emptyMap()
                isReady = false
                return@withContext
            }

            val newIndex = mutableMapOf<String, IndexEntry>()
            for (embedding in embeddings) {
                val factors = SpotifyALSTrainer.deserializeFactors(embedding.factors)
                if (factors.isEmpty()) continue
                newIndex[embedding.trackId] = IndexEntry(
                    factors = factors,
                    norm = if (embedding.norm > 0f) embedding.norm else l2Norm(factors)
                )
            }

            index = newIndex
            isReady = newIndex.isNotEmpty()
            Log.i(TAG, "Vector index rebuilt with ${newIndex.size} entries")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to rebuild vector index", e)
            isReady = false
        }
    }

    /**
     * Build the index directly from an [ALSModelResult] without going
     * through the database. Used for immediate availability after training.
     */
    fun buildFromModel(model: ALSModelResult) {
        try {
            val newIndex = mutableMapOf<String, IndexEntry>()
            for ((trackId, factors) in model.itemFactors) {
                if (factors.isEmpty()) continue
                newIndex[trackId] = IndexEntry(
                    factors = factors,
                    norm = l2Norm(factors)
                )
            }
            index = newIndex
            isReady = newIndex.isNotEmpty()
            Log.i(TAG, "Vector index built from model with ${newIndex.size} entries")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to build index from model", e)
        }
    }

    /**
     * Find the N nearest neighbors to a given track by cosine similarity.
     *
     * @param trackId  Seed track to find neighbors for.
     * @param n        Number of neighbors to return.
     * @param exclude  Track IDs to exclude (e.g., already played).
     * @return List of (trackId, similarity score) pairs, highest first.
     */
    fun findNearest(
        trackId: String,
        n: Int = 50,
        exclude: Set<String> = emptySet()
    ): List<Pair<String, Float>> {
        if (!isReady) return emptyList()

        val seedEntry = index[trackId] ?: return emptyList()
        val results = mutableListOf<Pair<String, Float>>()

        for ((candidateId, candidateEntry) in index) {
            if (candidateId == trackId) continue
            if (candidateId in exclude) continue

            val similarity = cosineSimilarity(
                seedEntry.factors, seedEntry.norm,
                candidateEntry.factors, candidateEntry.norm
            )
            results.add(candidateId to similarity)
        }

        // Sort by similarity descending and take top N
        results.sortByDescending { it.second }
        return results.take(n)
    }

    /**
     * Find nearest neighbors using a raw user factor vector.
     * Used for personalized "For You" recommendations.
     *
     * @param userFactors  User's latent factor vector from ALS.
     * @param n            Number of recommendations.
     * @param exclude      Track IDs to exclude.
     * @return List of (trackId, predicted score) pairs, highest first.
     */
    fun findNearestToVector(
        userFactors: FloatArray,
        n: Int = 30,
        exclude: Set<String> = emptySet()
    ): List<Pair<String, Float>> {
        if (!isReady || userFactors.isEmpty()) return emptyList()

        val userNorm = l2Norm(userFactors)
        if (userNorm == 0f) return emptyList()

        val results = mutableListOf<Pair<String, Float>>()

        for ((trackId, entry) in index) {
            if (trackId in exclude) continue

            // Use dot product as predicted preference (standard in ALS)
            val dotProduct = dotProduct(userFactors, entry.factors)
            results.add(trackId to dotProduct)
        }

        results.sortByDescending { it.second }
        return results.take(n)
    }

    /**
     * Check if a track has an embedding in the index.
     */
    fun hasTrack(trackId: String): Boolean = trackId in index

    /**
     * Get the embedding factors for a track, or null.
     */
    fun getFactors(trackId: String): FloatArray? = index[trackId]?.factors

    // ── Math utilities ──

    private fun cosineSimilarity(
        a: FloatArray, normA: Float,
        b: FloatArray, normB: Float
    ): Float {
        if (normA == 0f || normB == 0f) return 0f
        val dot = dotProduct(a, b)
        return dot / (normA * normB)
    }

    private fun dotProduct(a: FloatArray, b: FloatArray): Float {
        val len = minOf(a.size, b.size)
        var sum = 0f
        for (i in 0 until len) {
            sum += a[i] * b[i]
        }
        return sum
    }

    private fun l2Norm(v: FloatArray): Float {
        var sum = 0f
        for (x in v) sum += x * x
        return sqrt(sum)
    }

    private data class IndexEntry(
        val factors: FloatArray,
        val norm: Float
    ) {
        override fun equals(other: Any?): Boolean =
            this === other || (other is IndexEntry && factors.contentEquals(other.factors))
        override fun hashCode(): Int = factors.contentHashCode()
    }
}
