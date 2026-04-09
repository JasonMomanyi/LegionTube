package com.github.legiontube.ui.dialogs

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.setFragmentResult
import com.github.legiontube.R
import com.github.legiontube.api.obj.StreamItem
import com.github.legiontube.constants.IntentData
import com.github.legiontube.databinding.DialogAddToPlaylistBinding
import com.github.legiontube.extensions.parcelable
import com.github.legiontube.ui.models.AddToPlaylistViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Dialog to insert new videos to a playlist
 * videoId: The id of the video to add. If non is provided, insert the whole playing queue
 */
class AddToPlaylistDialog : DialogFragment() {

    private var videoInfo: StreamItem? = null
    private val viewModel: AddToPlaylistViewModel by activityViewModels { AddToPlaylistViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        videoInfo = arguments?.parcelable(IntentData.videoInfo)
        viewModel.savedStateHandle[IntentData.videoInfo] = videoInfo
        viewModel.fetchPlaylists()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        childFragmentManager.setFragmentResultListener(
            CreatePlaylistDialog.CREATE_PLAYLIST_DIALOG_REQUEST_KEY,
            this
        ) { _, resultBundle ->
            val addedToPlaylist = resultBundle.getBoolean(IntentData.playlistTask)
            if (addedToPlaylist) {
                val playlistId = resultBundle.getString(IntentData.playlistId)
                // automatically select newly created playlist
                viewModel.setLastSelectedPlaylistId(playlistId)

                viewModel.fetchPlaylists()
            }
        }

        val binding = DialogAddToPlaylistBinding.inflate(layoutInflater)
        binding.createPlaylist.setOnClickListener {
            CreatePlaylistDialog().show(childFragmentManager, null)
        }
        binding.addToPlaylist.setOnClickListener {
            val selectedItemPosition = binding.playlistsSpinner.selectedItemPosition
            viewModel.onAddToPlaylist(selectedItemPosition)
        }

        viewModel.uiState.observe(this) { (lastSelectedPlaylistId, playlists, msg, saved) ->
            binding.playlistsSpinner.items =
                playlists
                    .mapNotNull { it.name }
                    .ifEmpty { listOf("") }

            // disable the spinner and the 'Add' button when there is no available playlist
            binding.playlistsSpinner.isEnabled = playlists.isNotEmpty()
            binding.addToPlaylist.isEnabled = playlists.isNotEmpty()

            // select the last used playlist
            lastSelectedPlaylistId?.let { id ->
                // check if the list is empty, it's possible that the user just deleted
                // all playlists and 'lastSelectedPlaylist' still has value
                if (playlists.isEmpty()) return@let

                binding.playlistsSpinner.selectedItemPosition = playlists
                    .indexOfFirst { it.id == id }
                    .takeIf { it >= 0 } ?: 0
            }

            msg?.let {
                with(binding.root.context) {
                    Toast.makeText(this, getString(it.resId, it.formatArgs), Toast.LENGTH_SHORT)
                        .show()
                }
                viewModel.onMessageShown()
            }

            saved?.let {
                dismiss()
                viewModel.onDismissed()
            }
        }

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.addToPlaylist)
            .setView(binding.root)
            .show()
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)

        setFragmentResult(ADD_TO_PLAYLIST_DIALOG_DISMISSED_KEY, bundleOf())
    }

    companion object {
        const val ADD_TO_PLAYLIST_DIALOG_DISMISSED_KEY = "add_to_playlist_dialog_dismissed"
    }
}
