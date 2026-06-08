package io.github.jasonmomanyi.legiontube.innertube.pages

import io.github.jasonmomanyi.legiontube.innertube.models.YTItem

data class LibraryContinuationPage(
    val items: List<YTItem>,
    val continuation: String?,
)
