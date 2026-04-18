package com.github.legiontube.util

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.FragmentActivity
import com.github.legiontube.BuildConfig
import com.github.legiontube.R
import com.github.legiontube.api.RetrofitInstance
import com.github.legiontube.constants.IntentData.appUpdateChangelog
import com.github.legiontube.constants.IntentData.appUpdateURL
import com.github.legiontube.extensions.TAG
import com.github.legiontube.extensions.toastFromMainDispatcher
import com.github.legiontube.ui.dialogs.UpdateAvailableDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

class UpdateChecker(private val context: Context) {
    suspend fun checkUpdate(isManualCheck: Boolean = false) {
        if (BuildConfig.DEBUG) {
            if (isManualCheck) {
                context.toastFromMainDispatcher("Updates are disabled for debug builds")
            }
            return
        }

        try {
            val response = RetrofitInstance.externalApi.getLatestRelease()
            val hasUpdate = compareVersions(BuildConfig.VERSION_NAME, response.name)

            if (hasUpdate) {
                val downloadUrl = response.assets.firstOrNull { it.name.endsWith(".apk") }?.browserDownloadUrl ?: response.htmlUrl
                withContext(Dispatchers.Main) {
                    showUpdateAvailableDialog(response.body, downloadUrl, response.name)
                }
                Log.i(TAG(), response.toString())
            } else if (isManualCheck) {
                context.toastFromMainDispatcher(R.string.app_uptodate)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showUpdateAvailableDialog(
        changelog: String,
        url: String,
        versionName: String
    ) {
        val dialog = UpdateAvailableDialog()
        val args =
            Bundle().apply {
                putString(appUpdateChangelog, sanitizeChangelog(changelog))
                putString(appUpdateURL, url)
                putString("appUpdateVersionName", versionName)
            }
        dialog.arguments = args
        val fragmentManager = (context as? FragmentActivity)?.supportFragmentManager
        fragmentManager?.let {
            dialog.show(it, UpdateAvailableDialog::class.java.simpleName)
        }
    }

    private fun compareVersions(current: String, latest: String): Boolean {
        // Returns true if latest > current
        val currentParts = current.replace(Regex("[^0-9.]"), "").split(".").map { it.toIntOrNull() ?: 0 }
        val latestParts = latest.replace(Regex("[^0-9.]"), "").split(".").map { it.toIntOrNull() ?: 0 }

        val length = maxOf(currentParts.size, latestParts.size)
        for (i in 0 until length) {
            val curr = currentParts.getOrElse(i) { 0 }
            val lat = latestParts.getOrElse(i) { 0 }
            if (lat > curr) return true
            if (lat < curr) return false
        }
        return false
    }

    private fun sanitizeChangelog(changelog: String): String {
        return changelog.substringBeforeLast("**Full Changelog**")
            .replace(Regex("in https://github\\.com/\\S+"), "")
            .lines().joinToString("\n") { line ->
                if (line.startsWith("##")) line.uppercase(Locale.ROOT) + " :" else line
            }
            .replace("## ", "")
            .replace(">", "")
            .replace("*", "•")
            .lines()
            .joinToString("\n") { it.trim() }
    }
}
