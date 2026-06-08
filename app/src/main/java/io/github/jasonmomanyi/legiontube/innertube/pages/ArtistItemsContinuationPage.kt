package io.github.jasonmomanyi.legiontube.innertube.pages

import io.github.jasonmomanyi.legiontube.innertube.models.YTItem

data class ArtistItemsContinuationPage(
    val items: List<YTItem>,
    val continuation: String?,
)
