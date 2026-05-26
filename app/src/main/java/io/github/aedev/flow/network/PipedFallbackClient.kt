package io.github.aedev.flow.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object PipedFallbackClient {
    private const val TAG = "PipedFallbackClient"

    // List of reliable public Piped API instances
    private val INSTANCES = listOf(
        "https://pipedapi.kavin.rocks",
        "https://pipedapi.lunar.icu",
        "https://pipedapi.smnz.de",
        "https://api.piped.projectsegfau.lt"
    )

    private var currentInstanceIndex = 0

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    data class PipedStreamResponse(
        val hlsUrl: String?,
        val dashUrl: String?,
        val audioUrls: List<String>,
        val videoUrls: List<String>,
        val duration: Long,
        val title: String,
        val uploader: String,
        val uploaderAvatar: String?,
        val thumbnailUrl: String?
    )

    /**
     * Attempts to fetch stream information from Piped API instances.
     * Automatically fails over to the next instance if one is down or rate-limited.
     */
    suspend fun getStreamInfo(videoId: String): PipedStreamResponse? = withContext(Dispatchers.IO) {
        val startIndex = currentInstanceIndex
        var attempts = 0

        while (attempts < INSTANCES.size) {
            val instance = INSTANCES[currentInstanceIndex]
            val url = "$instance/streams/$videoId"
            
            try {
                Log.d(TAG, "Trying Piped API instance: $instance for video: $videoId")
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrEmpty()) {
                        val json = JSONObject(body)
                        
                        if (json.has("error")) {
                            Log.w(TAG, "Piped API error from $instance: ${json.getString("error")}")
                            rotateInstance()
                            attempts++
                            continue
                        }

                        val hlsUrl = json.optString("hls", null)?.takeIf { it.isNotBlank() }
                        val dashUrl = json.optString("dash", null)?.takeIf { it.isNotBlank() }
                        
                        val audioUrls = mutableListOf<String>()
                        val audioStreamsArray = json.optJSONArray("audioStreams")
                        if (audioStreamsArray != null) {
                            for (i in 0 until audioStreamsArray.length()) {
                                val stream = audioStreamsArray.getJSONObject(i)
                                stream.optString("url", null)?.let { audioUrls.add(it) }
                            }
                        }

                        val videoUrls = mutableListOf<String>()
                        val videoStreamsArray = json.optJSONArray("videoStreams")
                        if (videoStreamsArray != null) {
                            for (i in 0 until videoStreamsArray.length()) {
                                val stream = videoStreamsArray.getJSONObject(i)
                                stream.optString("url", null)?.let { videoUrls.add(it) }
                            }
                        }

                        val title = json.optString("title", "Unknown")
                        val uploader = json.optString("uploader", "Unknown")
                        val uploaderAvatar = json.optString("uploaderAvatar", null)
                        val thumbnailUrl = json.optString("thumbnailUrl", null)
                        val duration = json.optLong("duration", 0L)

                        Log.i(TAG, "Successfully fetched stream from Piped API instance: $instance")
                        return@withContext PipedStreamResponse(
                            hlsUrl = hlsUrl,
                            dashUrl = dashUrl,
                            audioUrls = audioUrls,
                            videoUrls = videoUrls,
                            duration = duration,
                            title = title,
                            uploader = uploader,
                            uploaderAvatar = uploaderAvatar,
                            thumbnailUrl = thumbnailUrl
                        )
                    }
                } else {
                    Log.w(TAG, "HTTP ${response.code} from $instance")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to connect to $instance: ${e.message}")
            }
            
            // Failover to next instance
            rotateInstance()
            attempts++
        }
        
        Log.e(TAG, "All Piped API instances failed for video: $videoId")
        return@withContext null
    }

    private fun rotateInstance() {
        currentInstanceIndex = (currentInstanceIndex + 1) % INSTANCES.size
        Log.d(TAG, "Rotated Piped API to instance: ${INSTANCES[currentInstanceIndex]}")
    }
}
