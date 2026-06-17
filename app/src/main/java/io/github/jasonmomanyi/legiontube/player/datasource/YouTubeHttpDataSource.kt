package io.github.jasonmomanyi.legiontube.player.datasource

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import io.github.jasonmomanyi.legiontube.network.AppProxyManager
import okhttp3.OkHttpClient

/**
 * YouTube-specific HttpDataSource optimized for streaming performance.
 * 
 * Key optimizations:
 * - Longer timeouts (30s read) to handle YouTube's variable latency
 * - Proper YouTube headers to avoid bot detection
 * - Range parameter handling for DASH manifests
 * - Cross-protocol redirect support
 */
@UnstableApi
class YouTubeHttpDataSource private constructor(
    private val userAgent: String,
    private val defaultRequestProperties: Map<String, String>
) : BaseDataSource(true), HttpDataSource {

    private var dataSource: DataSource? = null
    private var currentUri: Uri? = null

    class Factory : HttpDataSource.Factory {
        private val requestProperties = HashMap<String, String>()
        private var userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"

        override fun createDataSource(): HttpDataSource {
            return YouTubeHttpDataSource(userAgent, requestProperties)
        }

        override fun setDefaultRequestProperties(defaultRequestProperties: MutableMap<String, String>): HttpDataSource.Factory {
            requestProperties.clear()
            requestProperties.putAll(defaultRequestProperties)
            return this
        }
    }

    companion object {
        private val sharedHttpClient by lazy {
            AppProxyManager.applyTo(OkHttpClient.Builder())
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .retryOnConnectionFailure(true)
                .build()
        }
    }

    @UnstableApi
    override fun open(dataSpec: DataSpec): Long {
        currentUri = dataSpec.uri
        
        // Sanitize URI for YouTube to avoid conflicts with ExoPlayer's range handling
        val sanitizedUri = if (isYouTubeUri(dataSpec.uri)) {
            removeConflictingQueryParameters(dataSpec.uri)
        } else {
            dataSpec.uri
        }
        
        val enhancedDataSpec = dataSpec.buildUpon()
            .setUri(sanitizedUri)
            .build()

        // Optimized timeouts for YouTube streaming
        // YouTube can have variable latency, especially during peak hours
        // Longer timeouts prevent premature failures on slow networks
        val factory = OkHttpDataSource.Factory(sharedHttpClient)

        if (isYouTubeUri(dataSpec.uri)) {
            addYouTubeHeaders(factory)
        }

        dataSource = factory.createDataSource()
        return dataSource!!.open(enhancedDataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        return dataSource?.read(buffer, offset, length) ?: C.RESULT_END_OF_INPUT
    }

    override fun close() {
        dataSource?.close()
        dataSource = null
    }

    override fun getUri(): Uri? = currentUri
    
    override fun getResponseCode(): Int = (dataSource as? HttpDataSource)?.responseCode ?: -1
    
    override fun getResponseHeaders(): Map<String, List<String>> = 
        (dataSource as? HttpDataSource)?.responseHeaders ?: emptyMap()
    
    override fun clearAllRequestProperties() {}
    override fun clearRequestProperty(name: String) {}
    override fun setRequestProperty(name: String, value: String) {}

    private fun isYouTubeUri(uri: Uri): Boolean {
        val host = uri.host ?: return false
        return host.contains("youtube.com") || 
               host.contains("googlevideo.com") ||
               host.contains("ytimg.com")
    }

    /**
     * Remove query parameters that conflict with ExoPlayer's range handling.
     * ExoPlayer adds its own Range headers for DASH playback, and YouTube's
     * 'range' query parameter can cause conflicts.
     */
    private fun removeConflictingQueryParameters(uri: Uri): Uri {
        val urlString = uri.toString()
        val rangeParamRegex = Regex("[?&]range=[^&]+")
        val sanitizedUrl = rangeParamRegex.replace(urlString) { match ->
            if (match.value.startsWith("?")) "?" else ""
        }
        // If we replaced a "?" with "?", it means range was the first parameter.
        // We might end up with "url?&other" instead of "url?other", so let's clean that up.
        val cleanedUrl = sanitizedUrl.replace("?&", "?").trimEnd('?')
        return Uri.parse(cleanedUrl)
    }

    /**
     * Add headers that YouTube expects/requires for video streaming.
     * These help avoid bot detection and ensure proper CDN routing.
     */
    private fun addYouTubeHeaders(factory: OkHttpDataSource.Factory) {
        val headers = mapOf(
            "Origin" to "https://www.youtube.com",
            "Referer" to "https://www.youtube.com/",
            "Sec-Fetch-Dest" to "empty",
            "Sec-Fetch-Mode" to "cors",
            "Sec-Fetch-Site" to "cross-site",
            // Accept-Encoding helps with CDN optimization
            "Accept-Encoding" to "identity",
            // Accept header for video content
            "Accept" to "*/*"
        )
        factory.setDefaultRequestProperties(headers)
    }
}