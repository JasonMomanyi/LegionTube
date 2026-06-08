package io.github.jasonmomanyi.legiontube.ui.screens.player.components

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.ui.PlayerView
import io.github.jasonmomanyi.legiontube.R
import io.github.jasonmomanyi.legiontube.data.local.PlayerPreferences
import io.github.jasonmomanyi.legiontube.data.model.Video
import io.github.jasonmomanyi.legiontube.player.EnhancedPlayerManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import android.view.TextureView

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun VideoPlayerSurface(
    video: Video,
    resizeMode: Int,
    modifier: Modifier = Modifier,
    onVideoAspectRatioChanged: ((Float) -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var surfaceRestoreTrigger by remember { mutableIntStateOf(0) }
    var attachedVideoId by remember { mutableStateOf<String?>(null) }
    
    val preferences = remember { PlayerPreferences(context) }
    val ambientGlowEnabled by preferences.ambientGlowEnabled.collectAsState(initial = true)
    var frameBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var isPlaying by remember { mutableStateOf(false) }

    val playerView = remember(video.id) {
        Log.d("EnhancedVideoPlayer", "Creating shared PlayerView")
        (LayoutInflater.from(context).inflate(R.layout.video_player_view, null) as PlayerView).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
            setKeepContentOnPlayerReset(false)
        }
    }

    val videoSizeListener = remember {
        object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    val ratio = videoSize.width.toFloat() / videoSize.height.toFloat()
                    onVideoAspectRatioChanged?.invoke(ratio.coerceIn(0.56f, 2.5f))
                }
            }
        }
    }

    DisposableEffect(playerView) {
        onDispose {
            playerView.player?.removeListener(videoSizeListener)
            playerView.player = null
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START || event == Lifecycle.Event.ON_RESUME) {
                surfaceRestoreTrigger++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val currentSurfaceRestoreTrigger = surfaceRestoreTrigger

    key(video.id) {
        val animatedGlowAlpha by animateFloatAsState(
            targetValue = if (ambientGlowEnabled && isPlaying) 0.65f else 0f,
            animationSpec = tween(durationMillis = 1000),
            label = "ambientGlowAlpha"
        )

        LaunchedEffect(playerView, isPlaying, ambientGlowEnabled) {
            if (ambientGlowEnabled) {
                while (isActive) {
                    if (isPlaying) {
                        val textureView = playerView.videoSurfaceView as? TextureView
                        textureView?.bitmap?.let { bmp ->
                            frameBitmap = bmp.asImageBitmap()
                        }
                    }
                    delay(120) // ~8 fps is plenty for a smooth blur
                }
            }
        }

        Box(modifier = modifier.fillMaxSize()) {
            if (ambientGlowEnabled && frameBitmap != null) {
                Image(
                    bitmap = frameBitmap!!,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .scale(1.3f)
                        .graphicsLayer {
                            renderEffect = BlurEffect(120f, 120f, androidx.compose.ui.graphics.TileMode.Mirror)
                            alpha = animatedGlowAlpha
                        }
                )
            }

            AndroidView(
                factory = { playerView },
                update = { view ->
                    @Suppress("UNUSED_VARIABLE")
                    val restoreTrigger = currentSurfaceRestoreTrigger
                    val manager = EnhancedPlayerManager.getInstance()
                    val newPlayer = manager.getPlayer()
                    val oldPlayer = view.player
                    val videoChanged = attachedVideoId != video.id

                    if (oldPlayer !== newPlayer || videoChanged) {
                        oldPlayer?.removeListener(videoSizeListener)
                        if (oldPlayer === newPlayer && oldPlayer != null) {
                            view.player = null
                        }
                        newPlayer?.addListener(videoSizeListener)
                        view.player = newPlayer
                        attachedVideoId = video.id
                    }

                    if (newPlayer != null) {
                        isPlaying = newPlayer.playWhenReady && newPlayer.playbackState == Player.STATE_READY
                    }

                    if (newPlayer != null && manager.isInAudioOnlyMode()) {
                        Log.d("VideoPlayerSurface", "Restoring video output after audio-only background mode")
                        manager.restoreVideoOutput()
                    }

                    if (newPlayer != null &&
                        newPlayer.playbackState == Player.STATE_IDLE &&
                        newPlayer.currentMediaItem != null
                    ) {
                        Log.d("VideoPlayerSurface", "PlayerView attached; player IDLE with media - calling prepare()")
                        newPlayer.prepare()
                    }

                    view.subtitleView?.let { subtitleView ->
                        subtitleView.visibility = View.GONE
                    }

                    view.resizeMode = when (resizeMode) {
                        0 -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                        1 -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
                        2 -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        else -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
