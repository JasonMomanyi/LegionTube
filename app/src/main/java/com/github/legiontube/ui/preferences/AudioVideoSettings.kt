package com.github.legiontube.ui.preferences

import android.os.Bundle
import com.github.legiontube.R
import com.github.legiontube.ui.base.BasePreferenceFragment

class AudioVideoSettings : BasePreferenceFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.audio_video_settings, rootKey)
    }
}
