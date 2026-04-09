package com.github.legiontube.ui.dialogs

import android.app.Dialog
import android.os.Bundle
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import com.github.legiontube.R
import com.github.legiontube.api.PlaylistsHelper
import com.github.legiontube.constants.IntentData
import com.github.legiontube.databinding.DialogCreatePlaylistBinding
import com.github.legiontube.extensions.toastFromMainDispatcher
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class CreatePlaylistDialog : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = DialogCreatePlaylistBinding.inflate(layoutInflater)

        binding.createPlaylistSpinner.items = listOf(
            getString(R.string.createNewPlaylist),
            getString(R.string.clonePlaylist)
        )
        binding.createPlaylistSpinner.setOnSelectionChangeListener { position ->
            binding.createPlayistContainerView.isVisible = position == 0
            binding.clonePlaylistContainerView.isVisible = position == 1
        }

        binding.clonePlaylist.setOnClickListener {
            val playlistUrl = binding.playlistUrl.text.toString().toHttpUrlOrNull()
            val appContext = context?.applicationContext

            playlistUrl?.queryParameter("list")?.let {
                lifecycleScope.launch {
                    requireDialog().hide()
                    val playlistId = withContext(Dispatchers.IO) {
                        runCatching {
                            PlaylistsHelper.clonePlaylist(it)
                        }.getOrNull()
                    }
                    if (playlistId != null) {
                        setFragmentResult(
                            CREATE_PLAYLIST_DIALOG_REQUEST_KEY,
                            bundleOf(
                                IntentData.playlistTask to true,
                                IntentData.playlistId to playlistId
                            ),
                        )
                    }
                    appContext?.toastFromMainDispatcher(
                        if (playlistId != null) R.string.playlistCloned else R.string.server_error
                    )
                    dismiss()
                }
            } ?: run {
                Toast.makeText(context, R.string.invalid_url, Toast.LENGTH_SHORT).show()
            }
        }

        binding.createNewPlaylist.setOnClickListener {
            val appContext = context?.applicationContext
            val listName = binding.playlistName.text?.toString()
            if (!listName.isNullOrEmpty()) {
                // avoid creating the same playlist multiple times by spamming the button
                binding.createNewPlaylist.setOnClickListener(null)
                lifecycleScope.launch {
                    requireDialog().hide()
                    val playlistId = withContext(Dispatchers.IO) {
                        runCatching {
                            PlaylistsHelper.createPlaylist(listName)
                        }.getOrNull()
                    }
                    appContext?.toastFromMainDispatcher(
                        if (playlistId != null) R.string.playlistCreated else R.string.unknown_error
                    )
                    playlistId?.let {
                        setFragmentResult(
                            CREATE_PLAYLIST_DIALOG_REQUEST_KEY,
                            bundleOf(
                                IntentData.playlistTask to true,
                                IntentData.playlistId to playlistId
                            )
                        )
                    }
                    dismiss()
                }
            } else {
                Toast.makeText(context, R.string.emptyPlaylistName, Toast.LENGTH_LONG).show()
            }
        }

        binding.cancelCreate.setOnClickListener {
            dialog?.cancel()
        }

        binding.cancelClone.setOnClickListener {
            dialog?.cancel()
        }

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.createPlaylist)
            .setView(binding.root)
            .show()
    }

    companion object {
        const val CREATE_PLAYLIST_DIALOG_REQUEST_KEY = "create_playlist_dialog_request_key"
    }
}
