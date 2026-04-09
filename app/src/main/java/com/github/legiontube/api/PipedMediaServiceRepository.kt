package com.github.legiontube.api

import com.github.legiontube.api.RetrofitInstance.PIPED_API_URL
import com.github.legiontube.api.obj.Channel
import com.github.legiontube.api.obj.ChannelTabResponse
import com.github.legiontube.api.obj.CommentsPage
import com.github.legiontube.api.obj.DeArrowContent
import com.github.legiontube.api.obj.Message
import com.github.legiontube.api.obj.Playlist
import com.github.legiontube.api.obj.SearchResult
import com.github.legiontube.api.obj.SegmentData
import com.github.legiontube.api.obj.StreamItem
import com.github.legiontube.api.obj.Streams
import com.github.legiontube.constants.PreferenceKeys
import com.github.legiontube.helpers.PreferenceHelper
import retrofit2.HttpException

open class PipedMediaServiceRepository : MediaServiceRepository {
    override fun getTrendingCategories(): List<TrendingCategory> = emptyList()

    override suspend fun getTrending(region: String, category: TrendingCategory): List<StreamItem> =
        api.getTrending(region)

    override suspend fun getStreams(videoId: String): Streams {
        return try {
            api.getStreams(videoId).also {
                it.isShort = it.videoStreams.firstOrNull()?.let { stream ->
                    (stream.height ?: 0) > (stream.width ?: 0)
                } ?: false
            }
        } catch (e: HttpException) {
            val errorMessage = e.response()?.errorBody()?.string()?.runCatching {
                JsonHelper.json.decodeFromString<Message>(this).message
            }?.getOrNull()

            throw Exception(errorMessage)
        }
    }

    override suspend fun getComments(videoId: String): CommentsPage =
        api.getComments(videoId)

    override suspend fun getSegments(
        videoId: String,
        category: List<String>,
        actionType: List<String>?
    ): SegmentData = api.getSegments(
        videoId,
        JsonHelper.json.encodeToString(category),
        JsonHelper.json.encodeToString(actionType)
    )

    override suspend fun getDeArrowContent(videoId: String): DeArrowContent? =
        api.getDeArrowContent(videoId)[videoId]

    override suspend fun getCommentsNextPage(videoId: String, nextPage: String): CommentsPage =
        api.getCommentsNextPage(videoId, nextPage)

    override suspend fun getSearchResults(searchQuery: String, filter: String): SearchResult =
        api.getSearchResults(searchQuery, filter)

    override suspend fun getSearchResultsNextPage(
        searchQuery: String,
        filter: String,
        nextPage: String
    ): SearchResult = api.getSearchResultsNextPage(searchQuery, filter, nextPage)

    override suspend fun getSuggestions(query: String): List<String> =
        api.getSuggestions(query)

    override suspend fun getChannel(channelId: String): Channel =
        api.getChannel(channelId)

    override suspend fun getChannelTab(data: String, nextPage: String?): ChannelTabResponse =
        api.getChannelTab(data, nextPage)

    override suspend fun getChannelByName(channelName: String): Channel =
        api.getChannelByName(channelName)

    override suspend fun getChannelNextPage(channelId: String, nextPage: String): Channel =
        api.getChannelNextPage(channelId, nextPage)

    override suspend fun getPlaylist(playlistId: String): Playlist =
        api.getPlaylist(playlistId)

    override suspend fun getPlaylistNextPage(playlistId: String, nextPage: String): Playlist =
        api.getPlaylistNextPage(playlistId, nextPage)

    companion object {
        val apiUrl get() = PreferenceHelper.getString(PreferenceKeys.FETCH_INSTANCE, PIPED_API_URL)

        private val api by resettableLazy(RetrofitInstance.apiLazyMgr) {
            RetrofitInstance.buildRetrofitInstance<PipedApi>(apiUrl)
        }
    }
}