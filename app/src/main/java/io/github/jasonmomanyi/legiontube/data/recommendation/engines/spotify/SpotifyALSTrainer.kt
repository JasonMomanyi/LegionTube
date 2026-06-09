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
import org.json.JSONArray
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Alternating Least Squares (ALS) matrix factorization trainer.
 *
 * Implements implicit feedback collaborative filtering entirely on-device.
 * Inspired by the Implicit library's ALS approach and the Spotify
 * recommender systems reference implementation.
 *
 * Algorithm overview:
 *   1. Build a sparse user-item interaction matrix from [SpotifyInteractionTracker] data
 *   2. Initialize random user/item factor matrices of dimension K
 *   3. Alternately fix items and solve for users, then fix users and solve for items
 *   4. Extract embeddings (item factor vectors) and persist to Room DB
 *
 * All computation runs on [Dispatchers.Default] to avoid blocking I/O threads.
 *
 * @param dao        Room DAO for reading interactions and writing embeddings
 * @param config     Engine configuration (factor count, regularization, iterations)
 */
class SpotifyALSTrainer(
    private val dao: SpotifyEngineDao,
    private val config: SpotifyEngineConfig = SpotifyEngineConfig()
) {
    // TAG moved to companion object below

    /**
     * Run a full ALS training cycle.
     *
     * @return [ALSModelResult] on success, or null if training failed or
     *         there were insufficient interactions.
     */
    suspend fun train(): ALSModelResult? = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()

        try {
            // 1. Load interactions
            val interactions = withContext(Dispatchers.IO) {
                dao.getAllInteractions()
            }

            if (interactions.size < config.minInteractionsForTraining) {
                Log.i(TAG, "Insufficient interactions (${interactions.size} < ${config.minInteractionsForTraining}), skipping training")
                return@withContext null
            }

            Log.i(TAG, "Starting ALS training with ${interactions.size} interactions, K=${config.numFactors}")

            // 2. Build item index (trackId → matrix column index)
            val trackIds = interactions.map { it.trackId }.distinct()
            val trackIdToIndex = trackIds.withIndex().associate { (index, id) -> id to index }
            val numItems = trackIds.size

            if (numItems < 2) {
                Log.w(TAG, "Need at least 2 unique items, got $numItems")
                return@withContext null
            }

            // 3. Build sparse confidence matrix (single user in our case)
            //    C_ui = 1 + α * r_ui  where r_ui is the interaction weight
            //    P_ui = 1 if r_ui > 0, 0 otherwise (preference)
            val alpha = 40.0f  // confidence scaling factor
            val weights = FloatArray(numItems) { 0f }
            for (interaction in interactions) {
                val idx = trackIdToIndex[interaction.trackId] ?: continue
                weights[idx] = interaction.weight
            }
            val confidence = FloatArray(numItems) { i -> 1f + alpha * weights[i] }
            val preference = FloatArray(numItems) { i -> if (weights[i] > 0f) 1f else 0f }

            val k = config.numFactors
            val lambda = config.regularization

            // 4. Initialize factor matrices with small random values
            val rng = Random(42)
            var userFactors = FloatArray(k) { (rng.nextFloat() - 0.5f) * 0.1f }
            val itemFactors = Array(numItems) { FloatArray(k) { (rng.nextFloat() - 0.5f) * 0.1f } }

            // 5. Alternating optimization
            for (iteration in 1..config.iterations) {
                // Fix items, solve for user factors
                userFactors = solveUser(itemFactors, confidence, preference, k, lambda)

                // Fix user, solve for each item's factors
                for (i in 0 until numItems) {
                    itemFactors[i] = solveItem(userFactors, confidence[i], preference[i], k, lambda)
                }

                if (iteration % 5 == 0 || iteration == config.iterations) {
                    val loss = computeLoss(userFactors, itemFactors, confidence, preference, lambda)
                    Log.d(TAG, "Iteration $iteration/$${config.iterations}, loss=${"%.4f".format(loss)}")
                }
            }

            // 6. Build result
            val itemFactorMap = mutableMapOf<String, FloatArray>()
            for ((trackId, index) in trackIdToIndex) {
                itemFactorMap[trackId] = itemFactors[index]
            }

            // 7. Persist embeddings to Room
            val embeddings = itemFactorMap.map { (trackId, factors) ->
                SpotifyEmbedding(
                    trackId = trackId,
                    factors = serializeFactors(factors),
                    norm = l2Norm(factors),
                    updatedAt = System.currentTimeMillis()
                )
            }

            withContext(Dispatchers.IO) {
                dao.deleteAllEmbeddings()
                // Insert in batches to avoid SQLite variable limit
                embeddings.chunked(100).forEach { batch ->
                    dao.upsertEmbeddings(batch)
                }
            }

            val elapsed = System.currentTimeMillis() - startTime
            Log.i(TAG, "ALS training complete: ${interactions.size} interactions, " +
                    "$numItems items, ${config.numFactors} factors in ${elapsed}ms")

            // 8. Help GC reclaim intermediate arrays
            @Suppress("UNUSED_VALUE")
            var _gc: Any? = confidence
            _gc = preference
            _gc = null

            ALSModelResult(
                userFactors = userFactors,
                itemFactors = itemFactorMap,
                interactionCount = interactions.size,
                trainingTimeMs = elapsed
            )
        } catch (e: Exception) {
            Log.e(TAG, "ALS training failed", e)
            null
        }
    }

    /**
     * Solve for the user factor vector given fixed item factors.
     *
     * Closed-form solution: x_u = (Y^T C^u Y + λI)^{-1} Y^T C^u p(u)
     *
     * For a single user, this simplifies to a K×K linear system.
     */
    private fun solveUser(
        itemFactors: Array<FloatArray>,
        confidence: FloatArray,
        preference: FloatArray,
        k: Int,
        lambda: Float
    ): FloatArray {
        val numItems = itemFactors.size

        // Build A = Y^T C^u Y + λI  (K×K matrix, stored as flat array)
        val a = FloatArray(k * k)
        // Build b = Y^T C^u p(u)    (K vector)
        val b = FloatArray(k)

        for (i in 0 until numItems) {
            val yi = itemFactors[i]
            val ci = confidence[i]
            val pi = preference[i]

            for (r in 0 until k) {
                b[r] += yi[r] * ci * pi
                for (c in 0 until k) {
                    a[r * k + c] += yi[r] * yi[c] * ci
                }
            }
        }

        // Add regularization
        for (i in 0 until k) {
            a[i * k + i] += lambda
        }

        // Solve Ax = b using Cholesky decomposition
        return solveLinearSystem(a, b, k)
    }

    /**
     * Solve for a single item's factor vector given fixed user factors.
     *
     * For single-user implicit feedback, this simplifies significantly:
     * x_i = (x_u x_u^T c_i + λI)^{-1} (x_u c_i p_i)
     */
    private fun solveItem(
        userFactors: FloatArray,
        ci: Float,
        pi: Float,
        k: Int,
        lambda: Float
    ): FloatArray {
        // A = x_u x_u^T * c_i + λI
        val a = FloatArray(k * k)
        val b = FloatArray(k)

        for (r in 0 until k) {
            b[r] = userFactors[r] * ci * pi
            for (c in 0 until k) {
                a[r * k + c] = userFactors[r] * userFactors[c] * ci
            }
            a[r * k + r] += lambda
        }

        return solveLinearSystem(a, b, k)
    }

    /**
     * Solve Ax = b using Cholesky decomposition (LLᵀ).
     * Falls back to regularized pseudo-inverse if Cholesky fails.
     */
    private fun solveLinearSystem(a: FloatArray, b: FloatArray, k: Int): FloatArray {
        try {
            // Cholesky decomposition: A = L L^T
            val l = FloatArray(k * k)
            for (i in 0 until k) {
                for (j in 0..i) {
                    var sum = 0f
                    for (kk in 0 until j) {
                        sum += l[i * k + kk] * l[j * k + kk]
                    }
                    if (i == j) {
                        val diag = a[i * k + i] - sum
                        if (diag <= 0f) throw ArithmeticException("Matrix not positive definite")
                        l[i * k + j] = sqrt(diag)
                    } else {
                        l[i * k + j] = (a[i * k + j] - sum) / l[j * k + j]
                    }
                }
            }

            // Forward substitution: Ly = b
            val y = FloatArray(k)
            for (i in 0 until k) {
                var sum = 0f
                for (j in 0 until i) {
                    sum += l[i * k + j] * y[j]
                }
                y[i] = (b[i] - sum) / l[i * k + i]
            }

            // Back substitution: L^T x = y
            val x = FloatArray(k)
            for (i in k - 1 downTo 0) {
                var sum = 0f
                for (j in i + 1 until k) {
                    sum += l[j * k + i] * x[j]
                }
                x[i] = (y[i] - sum) / l[i * k + i]
            }

            return x
        } catch (e: ArithmeticException) {
            // Fallback: add more regularization and retry with simple iteration
            Log.w(TAG, "Cholesky failed, using fallback solver")
            val x = FloatArray(k) { 0.01f }
            repeat(10) {
                for (i in 0 until k) {
                    var sum = b[i]
                    for (j in 0 until k) {
                        if (j != i) sum -= a[i * k + j] * x[j]
                    }
                    val diag = a[i * k + i]
                    if (diag != 0f) x[i] = sum / diag
                }
            }
            return x
        }
    }

    /**
     * Compute weighted ALS loss for monitoring convergence.
     */
    private fun computeLoss(
        userFactors: FloatArray,
        itemFactors: Array<FloatArray>,
        confidence: FloatArray,
        preference: FloatArray,
        lambda: Float
    ): Float {
        var loss = 0f
        val k = userFactors.size

        for (i in itemFactors.indices) {
            var pred = 0f
            for (j in 0 until k) {
                pred += userFactors[j] * itemFactors[i][j]
            }
            val diff = preference[i] - pred
            loss += confidence[i] * diff * diff
        }

        // Regularization terms
        var userReg = 0f
        for (j in 0 until k) userReg += userFactors[j] * userFactors[j]
        loss += lambda * userReg

        for (factors in itemFactors) {
            var itemReg = 0f
            for (j in 0 until k) itemReg += factors[j] * factors[j]
            loss += lambda * itemReg
        }

        return loss
    }

    // ── Serialization helpers ──

    private fun serializeFactors(factors: FloatArray): String {
        val arr = JSONArray()
        for (f in factors) arr.put(f.toDouble())
        return arr.toString()
    }

    private fun l2Norm(v: FloatArray): Float {
        var sum = 0f
        for (x in v) sum += x * x
        return sqrt(sum)
    }

    companion object {
        private const val TAG = "SpotifyALSTrainer"

        /**
         * Deserialize a JSON factor string back to FloatArray.
         * Used by [SpotifyVectorIndex] when loading embeddings.
         */
        fun deserializeFactors(json: String): FloatArray {
            return try {
                val arr = JSONArray(json)
                FloatArray(arr.length()) { i -> arr.getDouble(i).toFloat() }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to deserialize factors", e)
                floatArrayOf()
            }
        }
    }
}
