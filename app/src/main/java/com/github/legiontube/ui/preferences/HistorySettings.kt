package com.github.legiontube.ui.preferences

import android.os.Bundle
import androidx.preference.Preference
import com.github.legiontube.R
import com.github.legiontube.constants.PreferenceKeys
import com.github.legiontube.db.DatabaseHolder.Database
import com.github.legiontube.ui.base.BasePreferenceFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HistorySettings : BasePreferenceFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.history_settings, rootKey)

        // clear search history
        val clearHistory = findPreference<Preference>(PreferenceKeys.CLEAR_SEARCH_HISTORY)
        clearHistory?.setOnPreferenceClickListener {
            showClearDialog(R.string.clear_history) {
                Database.searchHistoryDao().deleteAll()
            }
            true
        }

        // clear watch history and positions
        val clearWatchHistory = findPreference<Preference>(PreferenceKeys.CLEAR_WATCH_HISTORY)
        clearWatchHistory?.setOnPreferenceClickListener {
            showClearDialog(R.string.clear_history) {
                Database.watchHistoryDao().deleteAll()
            }
            true
        }

        // clear watch positions
        val clearWatchPositions = findPreference<Preference>(PreferenceKeys.CLEAR_WATCH_POSITIONS)
        clearWatchPositions?.setOnPreferenceClickListener {
            showClearDialog(R.string.reset_watch_positions) {
                Database.watchPositionDao().deleteAll()
            }
            true
        }

        val resetBookmarks = findPreference<Preference>(PreferenceKeys.CLEAR_BOOKMARKS)
        resetBookmarks?.setOnPreferenceClickListener {
            showClearDialog(R.string.clear_bookmarks) {
                Database.playlistBookmarkDao().deleteAll()
            }
            true
        }
    }

    private fun showClearDialog(title: Int, actionOnConfirm: suspend () -> Unit) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage(R.string.irreversible)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.okay) { _, _ ->
                // clear the selected preference preferences
                CoroutineScope(Dispatchers.IO).launch {
                    actionOnConfirm.invoke()
                }
            }
            .show()
    }
}
