package io.github.jasonmomanyi.legiontube.innertube.pages

import io.github.jasonmomanyi.legiontube.innertube.models.AlbumItem

data class ExplorePage(
    val newReleaseAlbums: List<AlbumItem>,
    val moodAndGenres: List<MoodAndGenres.Item>,
)
