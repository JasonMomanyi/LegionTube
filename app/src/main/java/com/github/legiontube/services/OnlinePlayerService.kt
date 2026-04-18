package com.github.legiontube.services

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaItem.SubtitleConfiguration
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import com.github.legiontube.R
import com.github.legiontube.api.MediaServiceRepository
import com.github.legiontube.api.SubscriptionHelper
import com.github.legiontube.api.obj.Segment
import com.github.legiontube.api.obj.Streams
import com.github.legiontube.constants.IntentData
import com.github.legiontube.db.DatabaseHelper
import com.github.legiontube.extensions.TAG
import com.github.legiontube.extensions.parcelable
import com.github.legiontube.extensions.setMetadata
import com.github.legiontube.extensions.toastFromMainDispatcher
import com.github.legiontube.extensions.toastFromMainThread
import com.github.legiontube.extensions.updateParameters
import com.github.legiontube.helpers.PlayerHelper
import com.github.legiontube.helpers.PlayerHelper.getSubtitleRoleFlags
import com.github.legiontube.helpers.ProxyHelper
import com.github.legiontube.parcelable.PlayerData
import com.github.legiontube.util.DeArrowUtil
import com.github.legiontube.util.PlayingQueue
import com.github.legiontube.util.YoutubeHlsPlaylistParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Loads the selected videos audio in background mode with a notification area.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
open class OnlinePlayerService : AbstractPlayerService() {
    override val isOfflinePlayer: Boolean = false

    // PlaylistId/ChannelId for autoplay
    private var playlistId: String? = null
    private var channelId: String? = null
    private var startTimestampSeconds: Long? = null

    /**
     * The response that gets when called the Api.
     */
    private var streams: Streams? = null

    private val scope = CoroutineScope(Dispatchers.IO)

    /*
    Current job that's loading a new video (the value is null if no video is loading at the moment).
     */
    private var fetchVideoInfoJob: Job? = null

    private val streamCache = mutableMapOf<String, Streams>()
    private var isPrefetching = false
    private var prefetchVideoId: String? = null

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (mediaItem != null && mediaItem.mediaId != videoId) {
                // We transitioned to a new track in the queue!
                videoId = mediaItem.mediaId
                streams = streamCache[videoId]

                streams?.toStreamItem(videoId)?.let {
                    PlayingQueue.updateCurrent(it)
                    if (!PlayingQueue.hasNext()) {
                        PlayingQueue.updateQueue(it, playlistId, channelId, streams!!.relatedStreams, streams!!.category)
                    }
                    scope.launch {
                        SubscriptionHelper.submitFeedItemChange(it.toFeedItem())
                    }
                }

                scope.launch {
                    val segments = getSponsorBlockSegments()
                    withContext(Dispatchers.Main) { setSponsorBlockSegments(segments) }
                }
            }

            // Phase 2: N+1 Fetch Logic
            if (mediaItem != null && exoPlayer != null && !exoPlayer!!.hasNextMediaItem()) {
                val nextId = PlayingQueue.getNext() ?: return
                scope.launch {
                    var prefetchedStreams: Streams? = null
                    var retries = 0
                    while (prefetchedStreams == null && retries < 3) {
                        prefetchedStreams = kotlinx.coroutines.withContext(Dispatchers.IO) {
                            try {
                                MediaServiceRepository.instance.getStreams(nextId).let {
                                    DeArrowUtil.deArrowStreams(it, nextId)
                                }
                            } catch (e: Exception) {
                                null
                            }
                        }
                        if (prefetchedStreams == null) {
                            retries++
                            kotlinx.coroutines.delay(5000L)
                        }
                    }
                    
                    if (prefetchedStreams != null) {
                        streamCache[nextId] = prefetchedStreams
                        val mediaSource = getMediaSource(prefetchedStreams, nextId)
                        kotlinx.coroutines.withContext(Dispatchers.Main) {
                            if (mediaSource != null) {
                                // Phase 1: Clear Duplicate Track Logic
                                val lastItem = if (exoPlayer!!.mediaItemCount > 0) exoPlayer!!.getMediaItemAt(exoPlayer!!.mediaItemCount - 1) else null
                                if (lastItem?.mediaId != nextId) {
                                    exoPlayer?.addMediaSource(mediaSource)
                                }
                            }
                        }
                    }
                }
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_ENDED -> {
                    if (exoPlayer!!.hasNextMediaItem()) {
                        exoPlayer?.seekToNextMediaItem()
                        exoPlayer?.play()
                    } else if (!isTransitioning) {
                        toastFromMainThread("Loading next track...")
                    }
                }

                Player.STATE_IDLE -> {
                    onDestroy()
                }

                Player.STATE_BUFFERING -> {}
                Player.STATE_READY -> {
                    // save video to watch history when the video starts playing or is being resumed
                    // waiting for the player to be ready since the video can't be claimed to be watched
                    // while it did not yet start actually, but did buffer only so far
                    if (PlayerHelper.watchHistoryEnabled) {
                        scope.launch(Dispatchers.IO) {
                            streams?.let { streams ->
                                val watchHistoryItem =
                                    streams.toStreamItem(videoId).toWatchHistoryItem(videoId)
                                DatabaseHelper.addToWatchHistory(watchHistoryItem)
                            }
                        }
                    }
                }
            }
        }
    }

    override suspend fun onServiceCreated(args: Bundle) {
        val playerData = args.parcelable<PlayerData>(IntentData.playerData)
        if (playerData == null) {
            stopSelf()
            return
        }
        isAudioOnlyPlayer = args.getBoolean(IntentData.audioOnly)

        // get the intent arguments
        videoId = playerData.videoId
        playlistId = playerData.playlistId
        channelId = playerData.channelId
        startTimestampSeconds = playerData.timestamp

        if (!playerData.keepQueue) PlayingQueue.clear()

        exoPlayer?.addListener(playerListener)
        trackSelector?.updateParameters {
            setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, isAudioOnlyPlayer)
        }
    }

    override suspend fun startPlayback() {
        super.startPlayback()

        if (PlayerHelper.globalAudioOnlyMode) {
            isAudioOnlyPlayer = true
            trackSelector?.updateParameters {
                setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
            }
        }

        val timestampMs = startTimestampSeconds?.times(1000) ?: 0L
        startTimestampSeconds = null

        // stop any previous task for loading video info
        fetchVideoInfoJob?.cancelAndJoin()

        // start loading the video info while keeping a reference to the job
        // so that it can be canceled once a different video is loaded
        fetchVideoInfoJob = scope.launch {
            streams = withContext(Dispatchers.IO) {
                try {
                    MediaServiceRepository.instance.getStreams(videoId).let {
                        DeArrowUtil.deArrowStreams(it, videoId)
                    }
                }  catch (e: Exception) {
                    Log.e(TAG(), e.stackTraceToString())
                    toastFromMainDispatcher("Connection failed. Please change your instance in Settings.\n(${e.localizedMessage.orEmpty()})")
                    return@withContext null
                }
            } ?: return@launch

            streams?.let { streamCache[videoId] = it }

            streams?.toStreamItem(videoId)?.let {
                // save the current stream to the queue
                PlayingQueue.updateCurrent(it)

                if (!PlayingQueue.hasNext()) {
                    PlayingQueue.updateQueue(it, playlistId, channelId, streams!!.relatedStreams, streams!!.category)
                }

                // update feed item with newer information, e.g. more up-to-date views
                SubscriptionHelper.submitFeedItemChange(it.toFeedItem())
            }

            launch {
                val segments = getSponsorBlockSegments()
                withContext(Dispatchers.Main) { setSponsorBlockSegments(segments) }
            }

            withContext(Dispatchers.Main) {
                setStreamSource()
                configurePlayer(timestampMs)
            }
        }

        fetchVideoInfoJob?.join()
        fetchVideoInfoJob = null
    }

    private fun configurePlayer(seekToPositionMs: Long) {
        // seek to the previous position if available
        if (seekToPositionMs != 0L) {
            exoPlayer?.seekTo(seekToPositionMs)
        } else if (watchPositionsEnabled) {
            DatabaseHelper.getWatchPositionBlocking(videoId)?.let {
                if (!DatabaseHelper.isVideoWatched(it, streams?.duration)) exoPlayer?.seekTo(it)
            }
        }

        exoPlayer?.apply {
            repeatMode = Player.REPEAT_MODE_OFF
            playWhenReady = PlayerHelper.playAutomatically
            prepare()
        }
    }

    /**
     * Plays the next video from the queue
     */
    private fun playNextVideo(nextId: String? = null) {
        if (nextId == null) {
            if (PlayingQueue.repeatMode == Player.REPEAT_MODE_ONE) {
                exoPlayer?.seekTo(0)
                return
            }

            if (!PlayerHelper.isAutoPlayEnabled(playlistId != null) || !shouldHandleAutoplay) return
        }

        val nextVideo = nextId ?: PlayingQueue.getNext() ?: return

        // play new video on background
        navigateVideo(nextVideo)
    }

    private suspend fun getSponsorBlockSegments(): List<Segment> {
        return runCatching {
            MediaServiceRepository.instance.getSegments(
                videoId,
                sponsorBlockConfig.keys.toList(),
                listOf("skip", "mute", "full", "poi", "chapter")
            ).segments
        }.getOrElse { emptyList() }
    }

    override fun navigateVideo(videoId: String) {
        this.streams = null
        isPrefetching = false

        super.navigateVideo(videoId)
    }

    private fun getMediaSource(streams: Streams, vid: String): androidx.media3.exoplayer.source.MediaSource? {
        when {
            // DASH
            streams.videoStreams.isNotEmpty() -> {
                val dashUri =
                    if (streams.isLive && streams.dash != null) {
                        ProxyHelper.rewriteUrlUsingProxyPreference(streams.dash).toUri()
                    } else {
                        PlayerHelper.createDashSource(streams, this)
                    }

                val mediaItem = createMediaItem(dashUri, MimeTypes.APPLICATION_MPD, streams, vid)
                return androidx.media3.exoplayer.source.DefaultMediaSourceFactory(this).createMediaSource(mediaItem)
            }
            // HLS as last fallback
            streams.hls != null -> {
                val hlsMediaSourceFactory = HlsMediaSource.Factory(DefaultDataSource.Factory(this))
                    .setPlaylistParserFactory(YoutubeHlsPlaylistParser.Factory())

                val mediaItem = createMediaItem(
                    ProxyHelper.rewriteUrlUsingProxyPreference(streams.hls).toUri(),
                    MimeTypes.APPLICATION_M3U8,
                    streams, vid
                )
                return hlsMediaSourceFactory.createMediaSource(mediaItem)
            }
            else -> return null
        }
    }

    /**
     * Sets the [MediaItem] with the [streams] into the [exoPlayer]
     */
    private fun setStreamSource() {
        val streams = streams ?: return
        
        val mediaSource = getMediaSource(streams, videoId)
        if (mediaSource != null) {
            exoPlayer?.setMediaSource(mediaSource)
        } else {
            toastFromMainThread(R.string.unknown_error)
        }
    }

    override fun checkPrefetch() {
        // Disabled timer-based prefetch in favor of onMediaItemTransition event-driven prefetch
    }

    private fun getSubtitleConfigs(streams: Streams?): List<SubtitleConfiguration> = streams?.subtitles?.map {
        val roleFlags = getSubtitleRoleFlags(it)
        SubtitleConfiguration.Builder(it.url!!.toUri())
            .setRoleFlags(roleFlags)
            .setLanguage(it.code)
            .setMimeType(it.mimeType).build()
    }.orEmpty()

    private fun createMediaItem(uri: Uri, mimeType: String, streams: Streams, vid: String) =
        MediaItem.Builder()
            .setUri(uri)
            .setMediaId(vid)
            .setMimeType(mimeType)
            .setSubtitleConfigurations(getSubtitleConfigs(streams))
            .setMetadata(streams, vid)
            .build()
}
