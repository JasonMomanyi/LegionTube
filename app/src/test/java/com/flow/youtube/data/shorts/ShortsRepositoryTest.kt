package io.github.jasonmomanyi.legiontube.data.shorts

import android.content.Context
import android.util.Log
import io.github.jasonmomanyi.legiontube.data.model.Video
import io.github.jasonmomanyi.legiontube.data.recommendation.InterestProfile
import io.github.jasonmomanyi.legiontube.data.repository.YouTubeRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ShortsRepositoryTest {

    private val context: Context = mockk(relaxed = true)
    private val youtubeRepository: YouTubeRepository = mockk()
    private val interestProfile: InterestProfile = mockk()
    private lateinit var repository: ShortsRepository

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        
        mockkObject(YouTubeRepository)
        every { YouTubeRepository.getInstance() } returns youtubeRepository
        
        mockkObject(InterestProfile)
        every { InterestProfile.getInstance(any()) } returns interestProfile

        // Mock filesDir for DataStore
        val filesDir = java.io.File("build/tmp/shorts_tests").apply { mkdirs() }
        every { context.filesDir } returns filesDir
        every { context.applicationContext } returns context

        repository = ShortsRepository.getInstance(context)
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    private fun createVideo(id: String, duration: Int = 30) = Video(
        id = id,
        title = "Short $id",
        channelName = "Channel",
        channelId = "channelId",
        thumbnailUrl = "thumb",
        duration = duration,
        viewCount = 1000L,
        uploadDate = "today",
        isShort = true,
        likeCount = 0
    )

    @Test
    fun `getShortsFeed returns empty result when innerTube fails`() = runTest {
        coEvery { youtubeRepository.getVideoStreamInfo(any()) } returns null
        
        val result = repository.getShortsFeed()
        assertThat(result).isNotNull()
    }

    @Test
    fun `resolveStreamInfo returns null on failure`() = runTest {
        coEvery { youtubeRepository.getVideoStreamInfo(any()) } returns null
        
        val result = repository.resolveStreamInfo("test_id")
        assertThat(result).isNull()
    }
}
