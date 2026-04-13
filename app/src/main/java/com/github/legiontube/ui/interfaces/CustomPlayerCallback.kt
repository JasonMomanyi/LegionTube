package com.github.legiontube.ui.interfaces

interface CustomPlayerCallback {
    fun toggleFullscreen()
    fun toggleAudioOnlyMode()
    fun getVideoId(): String
    fun isVideoShort(): Boolean
    fun isVideoLive(): Boolean
}
