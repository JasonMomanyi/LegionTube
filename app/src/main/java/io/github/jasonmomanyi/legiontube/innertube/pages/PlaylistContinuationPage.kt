package io.github.jasonmomanyi.legiontube.innertube.pages

import io.github.jasonmomanyi.legiontube.innertube.models.SongItem

data class PlaylistContinuationPage(
    val songs: List<SongItem>,
    val continuation: String?,
)
