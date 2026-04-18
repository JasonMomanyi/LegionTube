package com.github.legiontube.obj.update

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@Parcelize
data class UpdateInfo(
    val name: String,
    val body: String,
    @SerialName("html_url") val htmlUrl: String,
    val assets: List<UpdateAsset> = emptyList()
) : Parcelable

@Serializable
@Parcelize
data class UpdateAsset(
    val name: String,
    @SerialName("browser_download_url") val browserDownloadUrl: String
) : Parcelable
