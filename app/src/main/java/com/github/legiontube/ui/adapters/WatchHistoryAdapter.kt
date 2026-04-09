package com.github.legiontube.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.ListAdapter
import com.github.legiontube.constants.IntentData
import com.github.legiontube.databinding.VideoRowBinding
import com.github.legiontube.db.DatabaseHolder
import com.github.legiontube.db.obj.WatchHistoryItem
import com.github.legiontube.helpers.ImageHelper
import com.github.legiontube.helpers.NavigationHelper
import com.github.legiontube.ui.adapters.callbacks.DiffUtilItemCallback
import com.github.legiontube.ui.base.BaseActivity
import com.github.legiontube.ui.extensions.setFormattedDuration
import com.github.legiontube.ui.extensions.setWatchProgressLength
import com.github.legiontube.ui.sheets.VideoOptionsBottomSheet
import com.github.legiontube.ui.viewholders.WatchHistoryViewHolder
import com.github.legiontube.util.TextUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WatchHistoryAdapter :
    ListAdapter<WatchHistoryItem, WatchHistoryViewHolder>(DiffUtilItemCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WatchHistoryViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        val binding = VideoRowBinding.inflate(layoutInflater, parent, false)
        return WatchHistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: WatchHistoryViewHolder, position: Int) {
        val video = getItem(holder.bindingAdapterPosition)
        holder.binding.apply {
            videoTitle.text = video.title
            channelName.text = video.uploader
            videoInfo.text =
                video.uploadDate?.takeIf { !video.isLive }?.let { TextUtils.localizeDate(it) }
            ImageHelper.loadImage(video.thumbnailUrl, thumbnail)

            if (video.duration != null) {
                // we pass in 0 for the uploadDate, as a future video cannot be watched already
                thumbnailDuration.setFormattedDuration(video.duration, null, 0)
            } else {
                thumbnailDurationCard.isGone = true
            }

            if (video.uploaderAvatar != null) {
                ImageHelper.loadImage(video.uploaderAvatar, channelImage, true)
            } else {
                channelImageContainer.isGone = true
            }

            channelImage.setOnClickListener {
                NavigationHelper.navigateChannel(root.context, video.uploaderUrl)
            }

            root.setOnClickListener {
                NavigationHelper.navigateVideo(root.context, video.videoId)
            }

            val activity = (root.context as BaseActivity)
            val fragmentManager = activity.supportFragmentManager
            root.setOnLongClickListener {
                fragmentManager.setFragmentResultListener(
                    VideoOptionsBottomSheet.VIDEO_OPTIONS_SHEET_REQUEST_KEY,
                    activity
                ) { _, _ ->
                    notifyItemChanged(position)
                }
                val sheet = VideoOptionsBottomSheet()
                sheet.arguments = bundleOf(IntentData.streamItem to video.toStreamItem())
                sheet.show(fragmentManager, WatchHistoryAdapter::class.java.name)
                true
            }

            if (video.duration != null) watchProgress.setWatchProgressLength(
                video.videoId,
                video.duration
            )

            CoroutineScope(Dispatchers.IO).launch {
                val isDownloaded =
                    DatabaseHolder.Database.downloadDao().exists(video.videoId)

                withContext(Dispatchers.Main) {
                    downloadBadge.isVisible = isDownloaded
                }
            }
        }
    }
}
