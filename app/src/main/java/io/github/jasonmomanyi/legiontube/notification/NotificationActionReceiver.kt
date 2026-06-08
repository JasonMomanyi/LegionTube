package io.github.jasonmomanyi.legiontube.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * BroadcastReceiver for handling notification action clicks
 */
class NotificationActionReceiver : BroadcastReceiver() {
    
    companion object {
        const val ACTION_CANCEL_DOWNLOAD = "io.github.jasonmomanyi.legiontube.action.CANCEL_DOWNLOAD"
        const val ACTION_RETRY_DOWNLOAD = "io.github.jasonmomanyi.legiontube.action.RETRY_DOWNLOAD"
        const val ACTION_PAUSE_DOWNLOAD = "io.github.jasonmomanyi.legiontube.action.PAUSE_DOWNLOAD"
        const val ACTION_RESUME_DOWNLOAD = "io.github.jasonmomanyi.legiontube.action.RESUME_DOWNLOAD"
        const val ACTION_DISMISS_NOTIFICATION = "io.github.jasonmomanyi.legiontube.action.DISMISS_NOTIFICATION"
        
        const val EXTRA_NOTIFICATION_ID = "notification_id"
        const val EXTRA_VIDEO_ID = "video_id"
        const val EXTRA_VIDEO_TITLE = "video_title"
        const val EXTRA_DOWNLOAD_ID = "download_id"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("NotificationAction", "Received action: ${intent.action}")
        
        when (intent.action) {
            ACTION_DISMISS_NOTIFICATION -> handleDismissNotification(context, intent)
        }
    }
    

    private fun handleDismissNotification(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        
        if (notificationId != -1) {
            NotificationHelper.cancelNotification(context, notificationId)
        }
    }
}
