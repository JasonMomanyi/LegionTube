package io.github.jasonmomanyi.legiontube.innertube.models.body

import io.github.jasonmomanyi.legiontube.innertube.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class SearchBody(
    val context: Context,
    val query: String?,
    val params: String?,
)
