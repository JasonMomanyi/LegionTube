package com.github.legiontube.helpers

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.core.os.bundleOf
import androidx.fragment.app.commit
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.github.legiontube.constants.IntentData
import com.github.legiontube.parcelable.PlayerData
import com.github.legiontube.services.AbstractPlayerService
import com.github.legiontube.services.OfflinePlayerService
import com.github.legiontube.services.OnlinePlayerService
import com.github.legiontube.ui.activities.AbstractPlayerHostActivity
import com.github.legiontube.ui.activities.NoInternetActivity
import com.github.legiontube.ui.fragments.DownloadSortingOrder
import com.github.legiontube.ui.fragments.DownloadTab
import com.github.legiontube.ui.fragments.PlayerFragment
import com.github.legiontube.util.PlayingQueue
import com.github.legiontube.util.PlayingQueueMode
import com.google.common.util.concurrent.MoreExecutors

/**
 * Helper for starting a new Instance of the [OnlinePlayerService]
 */
object BackgroundHelper {
    /**
     * Start the foreground service [OnlinePlayerService] to play in background. [position]
     * is seek to position specified in milliseconds in the current [videoId].
     */
    fun playOnBackground(
        context: Context,
        videoId: String,
        position: Long = 0,
        playlistId: String? = null,
        channelId: String? = null,
        keepQueue: Boolean = false,
    ) {
        // close the previous video player if open
        val fragmentManager =
            ContextHelper.unwrapActivity<AbstractPlayerHostActivity>(context).supportFragmentManager
        fragmentManager.fragments.firstOrNull { it is PlayerFragment }?.let {
            fragmentManager.commit { remove(it) }
        }

        val playerData = PlayerData(videoId, playlistId, channelId, keepQueue, position)

        stopBackgroundPlay(context)
        startMediaService(
            context,
            OnlinePlayerService::class.java,
            bundleOf(IntentData.playerData to playerData, IntentData.audioOnly to true)
        )
    }

    /**
     * Stop the [OnlinePlayerService] service if it is running.
     */
    fun stopBackgroundPlay(context: Context) {
        arrayOf(
            OnlinePlayerService::class.java,
            OfflinePlayerService::class.java
        ).forEach {
            val intent = Intent(context, it)
            context.stopService(intent)
        }
    }

    /**
     * Start the offline background player
     *
     * @param context the current context
     * @param videoId the videoId of the video or null if all available downloads should be shuffled
     */
    fun playOnBackgroundOffline(
        context: Context,
        videoId: String?,
        playlistId: String?,
        downloadTab: DownloadTab,
        shuffle: Boolean = false,
        sortOrder: DownloadSortingOrder? = null,
    ) {
        // whether the service is started from the MainActivity or NoInternetActivity
        val noInternet = ContextHelper.tryUnwrapActivity<NoInternetActivity>(context) != null

        val arguments = bundleOf(
            IntentData.videoId to videoId,
            IntentData.playlistId to playlistId,
            IntentData.shuffle to shuffle,
            IntentData.downloadTab to downloadTab,
            IntentData.noInternet to noInternet,
            IntentData.audioOnly to true,
            IntentData.sortOptions to sortOrder,
        )

        stopBackgroundPlay(context)
        startMediaService(context, OfflinePlayerService::class.java, arguments)
    }

    @OptIn(UnstableApi::class)
    fun startMediaService(
        context: Context,
        serviceClass: Class<*>,
        arguments: Bundle = Bundle.EMPTY,
        onController: (MediaController) -> Unit = {}
    ) {
        val sessionToken =
            SessionToken(context, ComponentName(context, serviceClass))

        val controllerFuture =
            MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture.addListener({
            val controller = controllerFuture.get()
            if (!arguments.isEmpty) controller.sendCustomCommand(
                AbstractPlayerService.startServiceCommand,
                arguments
            )
            onController(controller)
        }, MoreExecutors.directExecutor())
    }

    /**
     * Get the service class of the currently active player based on the playing queue mode
     */
    fun getCurrentPlayerServiceClass(): Class<*> {
        return if (PlayingQueue.queueMode == PlayingQueueMode.OFFLINE) {
            OfflinePlayerService::class.java
        } else {
            OnlinePlayerService::class.java
        }
    }

    /**
     * Start a media service for the currently active player (online or offline)
     * and pass the MediaController to the callback
     */
    fun startCurrentMediaService(
        context: Context,
        arguments: Bundle = Bundle.EMPTY,
        onController: (MediaController) -> Unit = {}
    ) {
        startMediaService(context, getCurrentPlayerServiceClass(), arguments, onController)
    }


    /**
     * Set the volume of the currently active player
     *
     * @param context the current context
     * @param volume the volume level to set (0.0 to 1.0)
     */
    fun setVolume(context: Context, volume: Float) {
        startCurrentMediaService(context, Bundle.EMPTY) { controller ->
            try {
                controller.volume = volume.coerceIn(0f, 1f)
            } finally {
                controller.release()
            }
        }
    }
}
