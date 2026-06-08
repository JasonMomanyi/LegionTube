package io.github.jasonmomanyi.legiontube.innertube.models.body

import io.github.jasonmomanyi.legiontube.innertube.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class GetQueueBody(
    val context: Context,
    val videoIds: List<String>?,
    val playlistId: String?,
)
