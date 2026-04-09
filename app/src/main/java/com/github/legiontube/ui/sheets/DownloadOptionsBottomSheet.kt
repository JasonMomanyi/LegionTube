package com.github.legiontube.ui.sheets

import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import com.github.legiontube.R
import com.github.legiontube.api.obj.StreamItem
import com.github.legiontube.constants.IntentData
import com.github.legiontube.enums.ShareObjectType
import com.github.legiontube.extensions.parcelable
import com.github.legiontube.extensions.serializable
import com.github.legiontube.extensions.toID
import com.github.legiontube.helpers.BackgroundHelper
import com.github.legiontube.helpers.ContextHelper
import com.github.legiontube.helpers.NavigationHelper
import com.github.legiontube.obj.ShareData
import com.github.legiontube.ui.activities.NoInternetActivity
import com.github.legiontube.ui.dialogs.ShareDialog
import com.github.legiontube.ui.fragments.DownloadTab
import com.github.legiontube.util.PlayingQueue
import com.github.legiontube.util.PlayingQueueMode

class DownloadOptionsBottomSheet : BaseBottomSheet() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val streamItem = arguments?.parcelable<StreamItem>(IntentData.streamItem)!!
        val videoId = streamItem.url!!.toID()
        val downloadTab = arguments?.serializable<DownloadTab>(IntentData.downloadTab)!!
        val playlistId = arguments?.getString(IntentData.playlistId)

        val options = mutableListOf(
            R.string.playOnBackground,
            R.string.share,
            R.string.delete
        )

        // can't navigate to video while in offline activity
        if (ContextHelper.tryUnwrapActivity<NoInternetActivity>(requireContext()) == null) {
            options += R.string.go_to_video
        }

        val isSelectedVideoCurrentlyPlaying = PlayingQueue.getCurrent()?.url?.toID() == videoId
        if (!isSelectedVideoCurrentlyPlaying && PlayingQueue.isNotEmpty() && PlayingQueue.queueMode == PlayingQueueMode.OFFLINE) {
            options += R.string.play_next
            options += R.string.add_to_queue
        }

        setSimpleItems(options.map { getString(it) }) { which ->
            when (options[which]) {
                R.string.playOnBackground -> {
                    BackgroundHelper.playOnBackgroundOffline(requireContext(), videoId, playlistId, downloadTab)
                }

                R.string.go_to_video -> {
                    NavigationHelper.navigateVideo(requireContext(), videoId = videoId)
                }

                R.string.share -> {
                    val shareData = ShareData(currentVideo = videoId)
                    val bundle = bundleOf(
                        IntentData.id to videoId,
                        IntentData.shareObjectType to ShareObjectType.VIDEO,
                        IntentData.shareData to shareData
                    )
                    val newShareDialog = ShareDialog()
                    newShareDialog.arguments = bundle
                    newShareDialog.show(parentFragmentManager, null)
                }

                R.string.delete -> {
                    setFragmentResult(DELETE_DOWNLOAD_REQUEST_KEY, bundleOf())
                    dialog?.dismiss()
                }

                R.string.play_next -> {
                    PlayingQueue.addAsNext(streamItem)
                }

                R.string.add_to_queue -> {
                    PlayingQueue.add(streamItem)
                }
            }
        }

        super.onCreate(savedInstanceState)
    }

    companion object {
        const val DELETE_DOWNLOAD_REQUEST_KEY = "delete_download_request_key"
    }
}
