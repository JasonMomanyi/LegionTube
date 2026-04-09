package com.github.legiontube.ui.preferences

import android.os.Bundle
import com.github.legiontube.R
import com.github.legiontube.ui.base.BasePreferenceFragment

class SponsorBlockSettings : BasePreferenceFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.sponsorblock_settings, rootKey)
    }
}
