package com.github.legiontube.helpers

import android.content.Context
import androidx.core.content.ContextCompat

object DisplayHelper {
    /**
     * Detect whether the device supports HDR as the ExoPlayer doesn't handle it properly
     * Returns false below SDK 24
     */
    fun supportsHdr(context: Context) = ContextCompat.getDisplayOrDefault(context).isHdr
}
