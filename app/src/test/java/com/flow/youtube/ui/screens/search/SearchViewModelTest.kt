package io.github.jasonmomanyi.legiontube.ui.screens.search

import io.github.jasonmomanyi.legiontube.data.local.Duration
import io.github.jasonmomanyi.legiontube.data.local.SearchFilter
import io.github.jasonmomanyi.legiontube.data.model.Video
import io.github.jasonmomanyi.legiontube.data.model.SearchResult
import io.github.jasonmomanyi.legiontube.data.repository.YouTubeRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val repository: YouTubeRepository = mockk()
    private lateinit var viewModel: SearchViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = SearchViewModel(repository)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun createVideo(
        id: String,
        title: String = "Title $id",
        duration: Int = 100,
        viewCount: Long = 1000L
    ) = Video(
        id = id,
        title = title,
        channelName = "Channel",
        channelId = "channelId",
        thumbnailUrl = "thumbnail",
        duration = duration,
        viewCount = viewCount,
        uploadDate = "1 day ago",
        likeCount = 0
    )

    @Test
    fun `initial state is correct`() {
        val state = viewModel.uiState.value
        assertThat(state.query).isEmpty()
        assertThat(state.filters).isNull()
    }

    @Test
    fun `search updates uiState query`() = runTest {
        val query = "test query"
        viewModel.search(query)
        assertThat(viewModel.uiState.value.query).isEqualTo(query)
    }

    @Test
    fun `clearSearch resets uiState`() = runTest {
        viewModel.search("test")
        viewModel.clearSearch()
        
        val state = viewModel.uiState.value
        assertThat(state.query).isEmpty()
        assertThat(state.filters).isNull()
    }
}
