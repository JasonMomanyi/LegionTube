package com.github.legiontube.util

import androidx.media3.common.Player
import com.github.legiontube.api.MediaServiceRepository
import com.github.legiontube.api.PlaylistsHelper
import com.github.legiontube.api.obj.StreamItem
import com.github.legiontube.extensions.move
import com.github.legiontube.extensions.runCatchingIO
import com.github.legiontube.extensions.toID
import com.github.legiontube.helpers.PlayerHelper
import com.github.legiontube.util.PlayingQueue.queueMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Collections

object PlayingQueue {
    // queue is a synchronized list to be safely accessible from different coroutine threads
    private val queue = Collections.synchronizedList(mutableListOf<StreamItem>())
    private var currentStream: StreamItem? = null

    private val queueJobs = mutableListOf<Job>()
    
    val listeners = mutableListOf<() -> Unit>()

    private fun notifyListeners() {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            listeners.forEach { it.invoke() }
        }
    }

    /**
     * Current use case of the queue. Do NOT add any offline videos while the [queueMode] is online
     * or vice versa.
     */
    var queueMode: PlayingQueueMode = PlayingQueueMode.ONLINE

    // wrapper around PlayerHelper#repeatMode for compatibility
    var repeatMode: Int
        get() = PlayerHelper.repeatMode
        set(value) {
            PlayerHelper.repeatMode = value
        }

    private fun clearJobs() {
        queueJobs.forEach {
            it.cancel()
        }
        queueJobs.clear()
    }

    fun clear() {
        clearJobs()
        queue.clear()
        currentStream = null
        notifyListeners()
    }

    /**
     * Remove all items after the current [StreamItem] from the queue
     *
     * I.e., the current and all previous streams are kept
     */
    fun clearAfterCurrent() {
        clearJobs()
        synchronized(queue) {
            val newQueue = queue.filterIndexed { index, item -> index <= currentIndex() }
            setStreams(newQueue)
        }
    }

    /**
     * @param skipExisting Whether to skip the [streamItem] if it's already part of the queue
     */
    fun add(vararg streamItem: StreamItem, skipExisting: Boolean = false) = synchronized(queue) {
        var changed = false
        for (stream in streamItem) {
            if ((skipExisting && contains(stream)) || stream.title.isNullOrBlank()) continue

            queue.remove(stream)
            queue.add(stream)
            changed = true
        }
        if (changed) notifyListeners()
    }

    fun addAsNext(streamItem: StreamItem) = synchronized(queue) {
        if (currentStream == streamItem) return
        if (queue.contains(streamItem)) queue.remove(streamItem)
        queue.add(currentIndex() + 1, streamItem)
        notifyListeners()
    }

    // return the next item, or if repeating enabled and no video left, the first one of the queue
    fun getNext(): String? = synchronized(queue) {
        val nextItem = queue.getOrNull(currentIndex() + 1)
        if (nextItem != null) return nextItem.url?.toID()

        if (repeatMode == Player.REPEAT_MODE_ALL) return queue.firstOrNull()?.url?.toID()

        return null
    }

    // return the previous item, or if repeating enabled and no video left, the last one of the queue
    fun getPrev(): String? = synchronized(queue) {
        val prevItem = queue.getOrNull(currentIndex() - 1)
        if (prevItem != null) return prevItem.url?.toID()

        if (repeatMode == Player.REPEAT_MODE_ALL) return queue.lastOrNull()?.url?.toID()

        return null
    }

    fun hasPrev() = getPrev() != null

    fun hasNext() = getNext() != null

    fun updateCurrent(streamItem: StreamItem) = synchronized(queue) {
        currentStream = streamItem

        if (!contains(streamItem)) add(streamItem)
    }

    fun isNotEmpty() = queue.isNotEmpty()

    fun isEmpty() = queue.isEmpty()

    fun size() = queue.size

    fun isLast() = currentIndex() == size() - 1

    fun currentIndex(): Int = synchronized(queue) {
        return queue.indexOfFirst {
            it.url?.toID() == currentStream?.url?.toID()
        }.takeIf { it >= 0 } ?: 0
    }

    fun getCurrent(): StreamItem? = currentStream

    fun contains(streamItem: StreamItem) = synchronized(queue) {
        queue.any { it.url?.toID() == streamItem.url?.toID() }
    }

    // only returns a copy of the queue, no write access
    fun getStreams() = queue.toList()

    fun setStreams(streams: List<StreamItem>) = synchronized(queue) {
        queue.clear()
        queue.addAll(streams)
        notifyListeners()
    }

    fun remove(index: Int) = synchronized(queue) {
        queue.removeAt(index)
        notifyListeners()
        return@synchronized
    }

    fun move(from: Int, to: Int) = synchronized(queue) {
        queue.move(from, to)
        notifyListeners()
    }

    /**
     * Adds a list of videos to the current queue while updating the position of the current stream
     * @param isMainList whether the videos are part of the list that initially has been used to
     * start the queue, either from a channel or playlist. If it's false, the current stream won't
     * be touched, since it's an independent list.
     */
    private fun addToQueueAsync(
        streams: List<StreamItem>, currentStreamItem: StreamItem? = null, isMainList: Boolean = true
    ) = synchronized(queue) {
        if (!isMainList) {
            add(*streams.toTypedArray())
            return
        }
        val currentStream = currentStreamItem ?: this.currentStream
        // if the stream already got added to the queue earlier, although it's not yet
        // been found in the playlist, remove it and re-add it later
        var reAddStream = true
        if (currentStream != null && streams.any { it.url?.toID() == currentStream.url?.toID() }) {
            queue.removeAll { it.url?.toID() == currentStream.url?.toID() }
            reAddStream = false
        }
        // add all new stream items to the queue
        add(*streams.toTypedArray())

        if (currentStream != null && reAddStream) {
            // re-add the stream to the end of the queue
            updateCurrent(currentStream)
        }
    }

    private suspend fun fetchMoreFromPlaylist(
        playlistId: String,
        nextPage: String?,
        isMainList: Boolean
    ) {
        var playlistNextPage = nextPage
        while (playlistNextPage != null) {
            MediaServiceRepository.instance.getPlaylistNextPage(playlistId, playlistNextPage).run {
                addToQueueAsync(relatedStreams, isMainList = isMainList)
                playlistNextPage = this.nextpage
            }
        }
    }

    fun insertPlaylist(playlistId: String, newCurrentStream: StreamItem?) = runCatchingIO {
        val playlist = PlaylistsHelper.getPlaylist(playlistId)
        val isMainList = newCurrentStream != null
        addToQueueAsync(playlist.relatedStreams, newCurrentStream, isMainList)
        if (playlist.nextpage == null) return@runCatchingIO
        fetchMoreFromPlaylist(playlistId, playlist.nextpage, isMainList)
    }.let { queueJobs.add(it) }

    private suspend fun fetchMoreFromChannel(channelId: String, nextPage: String?) {
        var channelNextPage = nextPage
        var pageIndex = 1
        while (channelNextPage != null && pageIndex < 10) {
            MediaServiceRepository.instance.getChannelNextPage(channelId, channelNextPage).run {
                addToQueueAsync(relatedStreams)
                channelNextPage = this.nextpage
                pageIndex++
            }
        }
    }

    private fun insertChannel(channelId: String, newCurrentStream: StreamItem) = runCatchingIO {
        val channel = MediaServiceRepository.instance.getChannel(channelId)
        addToQueueAsync(channel.relatedStreams, newCurrentStream)
        if (channel.nextpage == null) return@runCatchingIO
        fetchMoreFromChannel(channelId, channel.nextpage)
    }.let { queueJobs.add(it) }

    fun insertByVideoId(videoId: String) = runCatchingIO {
        val streams = MediaServiceRepository.instance.getStreams(videoId.toID())
        add(streams.toStreamItem(videoId))
    }

    /**
     * The category of the currently playing stream (e.g. "Music", "Entertainment").
     * Used to filter related streams for better recommendations.
     */
    var currentCategory: String? = null

    fun updateQueue(
        streamItem: StreamItem,
        playlistId: String?,
        channelId: String?,
        relatedStreams: List<StreamItem> = emptyList(),
        category: String? = null
    ) {
        updateCurrent(streamItem)
        currentCategory = category

        if (playlistId != null) {
            insertPlaylist(playlistId, streamItem)
        } else if (channelId != null) {
            insertChannel(channelId, streamItem)
        } else if (relatedStreams.isNotEmpty()) {
            insertRelatedStreams(relatedStreams)
        }
    }

    fun insertRelatedStreams(streams: List<StreamItem>) {
        if (!PlayerHelper.autoInsertRelatedVideos && currentCategory != CATEGORY_MUSIC) return

        // don't add new videos to the queue if the user chose to repeat only the current queue
        if (isLast() && repeatMode == Player.REPEAT_MODE_ALL) return

        val filtered = streams.filter { !it.isLive }.let { nonLive ->
            if (currentCategory == CATEGORY_MUSIC) {
                // When playing music, prefer music-related content:
                // 1. Prefer items from the same uploader
                // 2. Filter out very short (<30s) or very long (>15min) items typical of non-music
                // 3. Prefer items with "music", "official", "audio", "lyrics" in title
                val currentUploader = currentStream?.uploaderName?.lowercase()
                nonLive.sortedByDescending { item ->
                    var score = 0
                    val title = item.title?.lowercase().orEmpty()
                    val uploader = item.uploaderName?.lowercase().orEmpty()
                    
                    // Same uploader gets highest priority
                    if (currentUploader != null && uploader == currentUploader) score += 50
                    
                    // Massive boost for official audio explicitly
                    if (title.contains("official audio") || title.contains("topic - ")) score += 200

                    // Music-related keywords in title
                    val musicKeywords = listOf("official", "music", "audio", "lyrics", "mv", "video", "ft.", "feat", "remix", "cover", "live performance")
                    score += musicKeywords.count { title.contains(it) } * 10
                    
                    // Typical music duration range (1.5 min to 10 min)
                    val dur = item.duration ?: 0
                    if (dur in 90..600) score += 20
                    
                    // Penalize non-music keywords
                    val nonMusicKeywords = listOf("news", "vlog", "tutorial", "review", "unboxing", "podcast", "episode", "ep.", "documentary")
                    score -= nonMusicKeywords.count { title.contains(it) } * 30
                    
                    score
                }
            } else {
                nonLive
            }
        }

        add(*filtered.toTypedArray(), skipExisting = true)
    }

    const val CATEGORY_MUSIC = "Music"
}

enum class PlayingQueueMode {
    ONLINE,
    OFFLINE
}