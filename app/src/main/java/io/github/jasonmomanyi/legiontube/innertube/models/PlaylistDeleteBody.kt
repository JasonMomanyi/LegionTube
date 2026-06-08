package io.github.jasonmomanyi.legiontube.innertube.models.body

import io.github.jasonmomanyi.legiontube.innertube.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class PlaylistDeleteBody(
    val context: Context,
    val playlistId: String
)
