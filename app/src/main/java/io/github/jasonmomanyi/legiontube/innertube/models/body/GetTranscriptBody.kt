package io.github.jasonmomanyi.legiontube.innertube.models.body

import io.github.jasonmomanyi.legiontube.innertube.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class GetTranscriptBody(
    val context: Context,
    val params: String,
)
