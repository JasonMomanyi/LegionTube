package com.github.legiontube.ui.activities

import android.content.Intent
import android.os.Bundle
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import com.github.legiontube.R
import com.github.legiontube.api.MediaServiceRepository
import com.github.legiontube.api.obj.StreamItem
import com.github.legiontube.constants.IntentData
import com.github.legiontube.extensions.toastFromMainDispatcher
import com.github.legiontube.helpers.IntentHelper
import com.github.legiontube.helpers.PreferenceHelper
import com.github.legiontube.ui.base.BaseActivity
import com.github.legiontube.ui.dialogs.AddToPlaylistDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AddToPlaylistActivity : BaseActivity() {
    override val isDialogActivity: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val videoId = intent.getStringExtra(Intent.EXTRA_TEXT)?.let {
            IntentHelper.resolveType(it.toUri())
        }?.getStringExtra(IntentData.videoId)

        if (videoId == null) {
            finish()
            return
        }

        supportFragmentManager.setFragmentResultListener(
            AddToPlaylistDialog.ADD_TO_PLAYLIST_DIALOG_DISMISSED_KEY,
            this
        ) { _, _ -> finish() }

        lifecycleScope.launch(Dispatchers.IO) {
            val videoInfo = if (PreferenceHelper.getToken().isEmpty()) {
                try {
                    MediaServiceRepository.instance.getStreams(videoId).toStreamItem(videoId)
                } catch (e: Exception) {
                    toastFromMainDispatcher(R.string.unknown_error)
                    withContext(Dispatchers.Main) {
                        finish()
                    }
                    return@launch
                }
            } else {
                StreamItem(videoId)
            }

            withContext(Dispatchers.Main) {
                AddToPlaylistDialog().apply {
                    arguments = bundleOf(IntentData.videoInfo to videoInfo)
                }.show(supportFragmentManager, null)
            }
        }
    }
}