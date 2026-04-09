package com.github.legiontube.obj

import android.graphics.Bitmap
import com.github.legiontube.api.obj.Streams

data class DownloadedFile(
    val name: String,
    val size: Long,
    var metadata: Streams? = null,
    var thumbnail: Bitmap? = null
)
