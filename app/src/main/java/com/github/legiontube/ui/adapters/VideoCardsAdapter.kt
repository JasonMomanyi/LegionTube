package com.github.legiontube.ui.adapters

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.ListAdapter
import com.github.legiontube.api.MediaServiceRepository
import com.github.legiontube.api.SponsorBlockLabelHelper
import com.github.legiontube.api.obj.StreamItem
import com.github.legiontube.constants.IntentData
import com.github.legiontube.databinding.AllCaughtUpRowBinding
import com.github.legiontube.databinding.TrendingRowBinding
import com.github.legiontube.extensions.dpToPx
import com.github.legiontube.extensions.toID
import com.github.legiontube.helpers.ImageHelper
import com.github.legiontube.helpers.NavigationHelper
import com.github.legiontube.helpers.PlayerHelper
import com.github.legiontube.ui.adapters.callbacks.DiffUtilItemCallback
import com.github.legiontube.ui.base.BaseActivity
import com.github.legiontube.ui.extensions.setFormattedDuration
import com.github.legiontube.ui.extensions.setWatchProgressLength
import com.github.legiontube.ui.sheets.VideoOptionsBottomSheet
import com.github.legiontube.ui.viewholders.VideoCardsViewHolder
import com.github.legiontube.util.DeArrowUtil
import com.github.legiontube.util.TextUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VideoCardsAdapter(private val columnWidthDp: Float? = null) :
    ListAdapter<StreamItem, VideoCardsViewHolder>(DiffUtilItemCallback()) {

    override fun getItemViewType(position: Int): Int {
        return if (currentList[position].type == CAUGHT_UP_STREAM_TYPE) CAUGHT_UP_TYPE else NORMAL_TYPE
    }

    fun removeItemById(videoId: String) {
        val index = currentList.indexOfFirst {
            it.url?.toID() == videoId
        }.takeIf { it > 0 } ?: return
        val updatedList = currentList.toMutableList().also {
            it.removeAt(index)
        }

        submitList(updatedList)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoCardsViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        return when {
            viewType == CAUGHT_UP_TYPE -> VideoCardsViewHolder(
                AllCaughtUpRowBinding.inflate(layoutInflater, parent, false)
            )

            else -> VideoCardsViewHolder(
                TrendingRowBinding.inflate(layoutInflater, parent, false)
            )
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: VideoCardsViewHolder, position: Int) {
        val video = getItem(holder.bindingAdapterPosition)
        val videoId = video.url.orEmpty().toID()

        val context = (holder.trendingRowBinding ?: holder.allCaughtUpBinding)!!.root.context
        val activity = (context as BaseActivity)
        val fragmentManager = activity.supportFragmentManager

        holder.trendingRowBinding?.apply {
            // set a fixed width for better visuals
            if (columnWidthDp != null) {
                root.updateLayoutParams {
                    width = columnWidthDp.dpToPx()
                }
            }
            watchProgress.setWatchProgressLength(videoId, video.duration ?: 0L)

            textViewTitle.text = video.title
            textViewChannel.text = TextUtils.formatViewsString(
                root.context,
                video.views ?: -1,
                video.uploaded,
                video.uploaderName
            )

            video.duration?.let {
                thumbnailDuration.setFormattedDuration(
                    it,
                    video.isShort,
                    video.uploaded
                )
            }
            ImageHelper.loadImage(video.thumbnail, thumbnail)

            if (video.uploaderAvatar != null) {
                channelImageContainer.isVisible = true
                ImageHelper.loadImage(video.uploaderAvatar, channelImage, true)
                channelImage.setOnClickListener {
                    NavigationHelper.navigateChannel(root.context, video.uploaderUrl)
                }
            } else {
                channelImageContainer.isGone = true
                textViewChannel.setOnClickListener {
                    NavigationHelper.navigateChannel(root.context, video.uploaderUrl)
                }
            }
            root.setOnClickListener {
                NavigationHelper.navigateVideo(root.context, videoId)
            }

            root.setOnLongClickListener {
                fragmentManager.setFragmentResultListener(
                    VideoOptionsBottomSheet.VIDEO_OPTIONS_SHEET_REQUEST_KEY,
                    activity
                ) { _, _ ->
                    notifyItemChanged(position)
                }
                val sheet = VideoOptionsBottomSheet()
                sheet.arguments = bundleOf(IntentData.streamItem to video)
                sheet.show(fragmentManager, VideoCardsAdapter::class.java.name)
                true
            }

            // always hide the icon, to avoid issues where the icon is recycled and shown until the web requests succeeds
            sponsorBadgeCard.isVisible = false
            CoroutineScope(Dispatchers.IO).launch {
                if (PlayerHelper.sponsorBlockEnabled) {
                    val sponsor = SponsorBlockLabelHelper.getVideoLabels(videoId)
                    withContext(Dispatchers.Main) {
                        sponsorBadgeCard.isVisible = sponsor?.segments?.isNotEmpty() == true
                    }
                }

                DeArrowUtil.deArrowVideoId(videoId)?.let { (title, thumbnail) ->
                    withContext(Dispatchers.Main) {
                        if (title != null) this@apply.textViewTitle.text = title
                        if (thumbnail != null) ImageHelper.loadImage(thumbnail, this@apply.thumbnail)
                    }
                }
            }
        }
    }

    companion object {
        private const val NORMAL_TYPE = 0
        private const val CAUGHT_UP_TYPE = 1

        const val CAUGHT_UP_STREAM_TYPE = "caught"
    }
}
