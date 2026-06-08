package io.github.jasonmomanyi.legiontube.innertube.pages

import io.github.jasonmomanyi.legiontube.innertube.models.Album
import io.github.jasonmomanyi.legiontube.innertube.models.AlbumItem
import io.github.jasonmomanyi.legiontube.innertube.models.Artist
import io.github.jasonmomanyi.legiontube.innertube.models.ArtistItem
import io.github.jasonmomanyi.legiontube.innertube.models.MusicResponsiveListItemRenderer
import io.github.jasonmomanyi.legiontube.innertube.models.MusicTwoRowItemRenderer
import io.github.jasonmomanyi.legiontube.innertube.models.PlaylistItem
import io.github.jasonmomanyi.legiontube.innertube.models.SongItem
import io.github.jasonmomanyi.legiontube.innertube.models.YTItem
import io.github.jasonmomanyi.legiontube.innertube.models.oddElements
import io.github.jasonmomanyi.legiontube.innertube.utils.parseTime

data class LibraryAlbumsPage(
    val albums: List<AlbumItem>,
    val continuation: String?,
) {
    companion object {
        fun fromMusicTwoRowItemRenderer(renderer: MusicTwoRowItemRenderer): AlbumItem? {
            return AlbumItem(
                        browseId = renderer.navigationEndpoint.browseEndpoint?.browseId ?: return null,
                        playlistId = renderer.thumbnailOverlay?.musicItemThumbnailOverlayRenderer?.content
                            ?.musicPlayButtonRenderer?.playNavigationEndpoint
                            ?.watchPlaylistEndpoint?.playlistId ?: return null,
                        title = renderer.title.runs?.firstOrNull()?.text ?: return null,
                        artists = null,
                        year = renderer.subtitle?.runs?.lastOrNull()?.text?.toIntOrNull(),
                        thumbnail = renderer.thumbnailRenderer.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
                        explicit = renderer.subtitleBadges?.find {
                            it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                        } != null
                    )
        }
    }
}
