package io.github.jasonmomanyi.legiontube.ui.screens.player

import android.content.Context
import android.util.Log
import io.github.jasonmomanyi.legiontube.data.local.*
import io.github.jasonmomanyi.legiontube.data.model.Video
import io.github.jasonmomanyi.legiontube.data.recommendation.LegionTubeNeuroEngine
import io.github.jasonmomanyi.legiontube.data.recommendation.InterestProfile
import io.github.jasonmomanyi.legiontube.data.repository.YouTubeRepository
import io.github.jasonmomanyi.legiontube.data.video.VideoDownloadManager
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.schabi.newpipe.extractor.stream.StreamInfo

@OptIn(ExperimentalCoroutinesApi::class)
class VideoPlayerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val context: Context = mockk(relaxed = true)
    private val repository: YouTubeRepository = mockk(relaxed = true)
    private val viewHistory: ViewHistory = mockk()
    private val subscriptionRepository: SubscriptionRepository = mockk()
    private val likedVideosRepository: LikedVideosRepository = mockk()
    private val playlistRepository: PlaylistRepository = mockk()
    private val interestProfile: InterestProfile = mockk()
    private val playerPreferences: PlayerPreferences = mockk()
    private val videoDownloadManager: VideoDownloadManager = mockk()
    private val sponsorBlockRepository: io.github.jasonmomanyi.legiontube.data.repository.SponsorBlockRepository = mockk(relaxed = true)

    private lateinit var viewModel: VideoPlayerViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0

        mockkObject(LegionTubeNeuroEngine)
        
        // Default mocks for preferences
        every { playerPreferences.defaultQualityWifi } returns flowOf(VideoQuality.Q_720p)
        every { playerPreferences.defaultQualityCellular } returns flowOf(VideoQuality.Q_360p)
        every { playerPreferences.autoplayEnabled } returns flowOf(true)
        every { videoDownloadManager.downloadedVideos } returns flowOf(emptyList())

        viewModel = VideoPlayerViewModel(
            context, repository, viewHistory, subscriptionRepository,
            likedVideosRepository, playlistRepository, interestProfile,
            playerPreferences, videoDownloadManager, sponsorBlockRepository
        )
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun createVideo(id: String) = Video(
        id = id,
        title = "Title $id",
        channelName = "Channel",
        channelId = "channelId",
        thumbnailUrl = "thumbnail",
        duration = 100,
        viewCount = 1000L,
        likeCount = 0L,
        uploadDate = "today"
    )

    @Test
    fun `loadVideoInfo updates state to loading`() = runTest {
        coEvery { repository.getVideoStreamInfo(any()) } coAnswers {
            delay(1000)
            mockk<StreamInfo>(relaxed = true)
        }
        
        viewModel.loadVideoInfo("test_vid")
        
        assertThat(viewModel.uiState.value.isLoading).isTrue()
        assertThat(viewModel.uiState.value.error).isNull()
    }

    @Test
    fun `loadVideoInfo sets error when repository fails`() = runTest {
        coEvery { repository.getVideoStreamInfo(any()) } throws Exception("Network error")
        
        viewModel.loadVideoInfo("test_vid")
        advanceUntilIdle()
        
        assertThat(viewModel.uiState.value.isLoading).isFalse()
        assertThat(viewModel.uiState.value.error).isEqualTo("Failed to load video")
    }

    @Test
    fun `toggleSubscription updates UI and repo`() = runTest {
        coEvery { subscriptionRepository.isSubscribed("chan1") } returns flowOf(false)
        coEvery { subscriptionRepository.subscribe(any()) } just Runs
        coEvery { interestProfile.recordSubscription(any(), any()) } just Runs
        
        viewModel.toggleSubscription("chan1", "Channel Name", "thumb")
        advanceUntilIdle()
        
        coVerify { subscriptionRepository.subscribe(match { it.channelId == "chan1" }) }
        assertThat(viewModel.uiState.value.isSubscribed).isTrue()
    }

    @Test
    fun `switchQuality updates uiState`() {
        viewModel.switchQuality(VideoQuality.Q_1080p)
        assertThat(viewModel.uiState.value.selectedQuality).isEqualTo(VideoQuality.Q_1080p)
    }
}
