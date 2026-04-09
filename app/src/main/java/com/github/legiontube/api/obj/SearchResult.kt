package com.github.legiontube.api.obj

import kotlinx.serialization.Serializable

@Serializable
data class SearchResult(
    var items: List<ContentItem> = emptyList(),
    val nextpage: String? = null,
    val suggestion: String? = null,
    val corrected: Boolean = false
)
