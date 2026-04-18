package com.github.legiontube.ui.dialogs

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import androidx.core.net.toUri
import androidx.fragment.app.DialogFragment
import com.github.legiontube.R
import com.github.legiontube.constants.IntentData.appUpdateChangelog
import com.github.legiontube.constants.IntentData.appUpdateURL
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class UpdateAvailableDialog : DialogFragment() {
    private var changelog: String? = null
    private var releaseUrl: String? = null
    private var versionName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.run {
            changelog = getString(appUpdateChangelog)
            releaseUrl = getString(appUpdateURL)
            versionName = getString("appUpdateVersionName")
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val titleText = versionName?.let { "${getString(R.string.update_available)} ($it)" }
            ?: getString(R.string.update_available)

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(titleText)
            .setMessage(changelog)
            .setPositiveButton(R.string.download) { _, _ ->
                releaseUrl?.let {
                    startActivity(Intent(Intent.ACTION_VIEW, it.toUri()))
                }
            }
            .setNegativeButton(R.string.tooltip_dismiss, null)
            .setCancelable(false)
            .show()
    }
}
