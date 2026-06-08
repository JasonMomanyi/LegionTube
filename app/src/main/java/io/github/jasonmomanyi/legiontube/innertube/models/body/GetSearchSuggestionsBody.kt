package io.github.jasonmomanyi.legiontube.innertube.models.body

import io.github.jasonmomanyi.legiontube.innertube.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class GetSearchSuggestionsBody(
    val context: Context,
    val input: String,
)
