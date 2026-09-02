package io.github.jasonmomanyi.legiontube

import android.app.Application
import android.content.Context
import android.util.Log
import io.github.jasonmomanyi.legiontube.notification.SubscriptionCheckWorker
import io.github.jasonmomanyi.legiontube.data.local.PlayerPreferences
import io.github.jasonmomanyi.legiontube.data.local.SubscriptionRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import io.github.jasonmomanyi.legiontube.data.repository.NewPipeDownloader
import io.github.jasonmomanyi.legiontube.data.repository.YouTubeRepository
import io.github.jasonmomanyi.legiontube.notification.NotificationHelper
import io.github.jasonmomanyi.legiontube.network.AppProxyManager
import io.github.jasonmomanyi.legiontube.utils.LegionTubeCrashHandler
import io.github.jasonmomanyi.legiontube.utils.PerformanceDispatcher
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization

import dagger.hilt.android.HiltAndroidApp
import coil.ImageLoader
import coil.ImageLoaderFactory
import javax.inject.Inject
import java.security.Security
import org.conscrypt.Conscrypt
import io.github.jasonmomanyi.legiontube.innertube.YouTube
import io.github.jasonmomanyi.legiontube.innertube.pages.NewPipeExtractor
import io.github.jasonmomanyi.legiontube.utils.AppLanguageManager
import io.github.jasonmomanyi.legiontube.utils.potoken.NewPipePoTokenProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import io.github.jasonmomanyi.legiontube.innertube.models.YouTubeLocale
import java.util.Locale
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor

@HiltAndroidApp
class LegionTubeApplication : Application(), ImageLoaderFactory {
    
    @Inject
    lateinit var imageLoader: ImageLoader

    override fun newImageLoader(): ImageLoader = imageLoader
    
    companion object {
        private const val TAG = "LegionTubeApplication"
        lateinit var appContext: Context
            private set
    }

    override fun attachBaseContext(base: Context) {
        val selectedLanguage = AppLanguageManager.loadSelectedLanguageTag(base)
        super.attachBaseContext(AppLanguageManager.wrapContext(base, selectedLanguage))
    }
    
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext

        val playerPreferences = PlayerPreferences(this)
        
        // Proxy is handled asynchronously via collectLatest below
        
        // Injects modern TLS/SSL certificates so OkHttp and Ktor don't crash on older Android versions
        if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.P) {
            try {
                Security.insertProviderAt(Conscrypt.newProvider(), 1)
                Log.d(TAG, "Conscrypt TLS provider installed successfully")
            } catch (e: Exception) {
                Log.w(TAG, "Conscrypt TLS provider installation warning: ${e.message}")
            }
        }

        // Install crash handler for real-time monitoring
        LegionTubeCrashHandler.install(this)
        
        try {
            val country = ContentCountry("US")
            val localization = Localization("en", "US")
            NewPipe.init(NewPipeDownloader.getInstance(this), localization, country)
            YoutubeStreamExtractor.setPoTokenProvider(io.github.jasonmomanyi.legiontube.utils.potoken.NewPipePoTokenProvider)
            Log.d(TAG, "NewPipe initialized successfully with en-US settings")
        } catch (e: Exception) {
            // Log error but don't crash the app
            Log.e(TAG, "Failed to initialize NewPipe", e)
        }

        try {
            io.github.jasonmomanyi.legiontube.utils.cipher.CipherDeobfuscator.initialize(this)
            Log.d(TAG, "CipherDeobfuscator initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize CipherDeobfuscator", e)
        }
        
        // Initialize notification channels
        NotificationHelper.createNotificationChannels(this)
        Log.d(TAG, "Notification channels created")
        
        /*
        try {
            // Initialize YoutubeDL
            com.yausername.youtubedl_android.YoutubeDL.getInstance().init(this)
            Log.d(TAG, "YoutubeDL initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize YoutubeDL", e)
        }
        */
        
        // Schedule periodic subscription checks for new videos
        applicationScope.launch {
            val savedIntervalMinutes = playerPreferences.subscriptionCheckIntervalMinutes.first()
            SubscriptionCheckWorker.schedulePeriodicCheck(this@LegionTubeApplication, intervalMinutes = savedIntervalMinutes.toLong())
        }
        
        // Schedule periodic update checks (every 12 hours) — github flavor only
        if (BuildConfig.UPDATER_ENABLED) {
            io.github.jasonmomanyi.legiontube.notification.UpdateCheckWorker.schedulePeriodicCheck(this)
        }
        
        Log.d(TAG, "Workers scheduled successfully")

        // Fetch and cache visitor data for the lifetime of the install.
        // The X-Goog-Visitor-Id header prevents YouTube from returning empty
        // search results on tablets and fresh Android 16 installs (Issue #223).
        applicationScope.launch {
            playerPreferences.proxyConfig.collectLatest { proxyConfig ->
                applyProxyConfig(proxyConfig)
            }
        }

        applicationScope.launch {
            try {
                val prefs = getSharedPreferences("flow_prefs", MODE_PRIVATE)
                val cached = prefs.getString("visitor_data", null)
                if (!cached.isNullOrEmpty()) {
                    YouTube.visitorData = cached
                    Log.d(TAG, "visitorData restored from prefs")
                } else {
                    YouTube.visitorData().onSuccess { data ->
                        if (!data.isNullOrEmpty()) {
                            prefs.edit().putString("visitor_data", data).apply()
                            YouTube.visitorData = data
                            Log.d(TAG, "visitorData fetched and cached")
                        }
                    }.onFailure { e ->
                        Log.w(TAG, "visitorData fetch failed: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "visitorData init error: ${e.message}")
            }
        }

        applicationScope.launch {
            combine(
                playerPreferences.appLanguage,
                playerPreferences.trendingRegion
            ) { lang, region ->
                val glCode = region.ifBlank { Locale.getDefault().country.ifEmpty { "US" } }
                val hlCode = lang.ifBlank { Locale.getDefault().language.ifEmpty { "en" } }
                YouTubeLocale(gl = glCode, hl = hlCode)
            }.collectLatest { newLocale ->
                YouTube.locale = newLocale
                Log.d(TAG, "Dynamic YouTube Locale updated: gl=${newLocale.gl}, hl=${newLocale.hl}")
            }
        }

        applicationScope.launch {
            var lastRegion: String? = null
            playerPreferences.trendingRegion.collectLatest { region ->
                if (lastRegion != null && lastRegion != region) {
                    Log.d(TAG, "Trending region changed from $lastRegion to $region. Invalidate visitor data.")
                    val prefs = getSharedPreferences("flow_prefs", MODE_PRIVATE)
                    prefs.edit().remove("visitor_data").apply()
                    YouTube.visitorData = null
                    
                    YouTube.visitorData().onSuccess { data ->
                        if (!data.isNullOrEmpty()) {
                            prefs.edit().putString("visitor_data", data).apply()
                            YouTube.visitorData = data
                            Log.d(TAG, "Fresh visitorData fetched for region: $region")
                        }
                    }.onFailure { e ->
                        Log.w(TAG, "Failed to fetch fresh visitorData: ${e.message}")
                    }
                }
                lastRegion = region
            }
        }

        applicationScope.launch {
            try {
                val repository = SubscriptionRepository.getInstance(this@LegionTubeApplication)
                val youtubeRepository = YouTubeRepository.getInstance(playerPreferences)
                val repaired = repository.repairVideoThumbnailSubscriptions { channelId ->
                    withTimeoutOrNull(6_000L) {
                        youtubeRepository.fetchChannelAvatarById(channelId)
                    }.orEmpty()
                }
                if (repaired > 0) {
                    Log.i(TAG, "Repaired $repaired subscription thumbnails")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Subscription thumbnail repair failed: ${e.message}")
            }
        }

        applicationScope.launch {
            try {
                val spotifyEnabled = playerPreferences.spotifyEngineEnabled.first()
                io.github.jasonmomanyi.legiontube.data.recommendation.engines.EngineRegistry
                    .getInstance(this@LegionTubeApplication)
                    .registerSpotifyEngine(spotifyEnabled)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize Spotify Engine", e)
            }
        }
    }

    private fun applyProxyConfig(config: io.github.jasonmomanyi.legiontube.network.AppProxyConfig) {
        AppProxyManager.update(config)
        YouTube.proxy = AppProxyManager.currentProxy()
        YouTube.proxyAuth = AppProxyManager.currentHttpProxyAuthorizationHeader()
        NewPipeExtractor.invalidateClient()
    }
    
    override fun onTerminate() {
        super.onTerminate()
        // Clean up performance dispatcher resources
        PerformanceDispatcher.shutdown()
        applicationScope.cancel()
    }
}
