package com.github.legiontube.util

import android.content.Context
import com.github.legiontube.constants.PreferenceKeys
import com.github.legiontube.helpers.NetworkHelper
import com.github.legiontube.helpers.PreferenceHelper

object DataSaverMode {
    fun isEnabled(context: Context): Boolean {
        val pref = PreferenceHelper.getString(PreferenceKeys.DATA_SAVER_MODE, "disabled")
        return when (pref) {
            "enabled" -> true
            "disabled" -> false
            "metered" -> NetworkHelper.isNetworkMetered(context)
            else -> throw IllegalArgumentException()
        }
    }
}
