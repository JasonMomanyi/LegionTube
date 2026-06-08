package io.github.jasonmomanyi.legiontube.di

import android.content.Context
import io.github.jasonmomanyi.legiontube.data.local.PlayerPreferences
import io.github.jasonmomanyi.legiontube.data.repository.YouTubeRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideYouTubeRepository(playerPreferences: PlayerPreferences): YouTubeRepository {
        return YouTubeRepository.getInstance(playerPreferences)
    }

    @Provides
    @Singleton
    fun provideSubscriptionRepository(@ApplicationContext context: Context): io.github.jasonmomanyi.legiontube.data.local.SubscriptionRepository {
        return io.github.jasonmomanyi.legiontube.data.local.SubscriptionRepository.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideLikedVideosRepository(@ApplicationContext context: Context): io.github.jasonmomanyi.legiontube.data.local.LikedVideosRepository {
        return io.github.jasonmomanyi.legiontube.data.local.LikedVideosRepository.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideViewHistory(@ApplicationContext context: Context): io.github.jasonmomanyi.legiontube.data.local.ViewHistory {
        return io.github.jasonmomanyi.legiontube.data.local.ViewHistory.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideInterestProfile(@ApplicationContext context: Context): io.github.jasonmomanyi.legiontube.data.recommendation.InterestProfile {
        return io.github.jasonmomanyi.legiontube.data.recommendation.InterestProfile.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideMusicPlaylistRepository(@ApplicationContext context: Context): io.github.jasonmomanyi.legiontube.data.music.PlaylistRepository {
        return io.github.jasonmomanyi.legiontube.data.music.PlaylistRepository(context)
    }


    // VideoDownloadManager is now @Singleton @Inject — Hilt provides it automatically
    @Provides
    @Singleton
    fun providePlayerPreferences(@ApplicationContext context: Context): io.github.jasonmomanyi.legiontube.data.local.PlayerPreferences {
        return io.github.jasonmomanyi.legiontube.data.local.PlayerPreferences(context)
    }

    @Provides
    @Singleton
    fun provideShortsRepository(@ApplicationContext context: Context): io.github.jasonmomanyi.legiontube.data.shorts.ShortsRepository {
        return io.github.jasonmomanyi.legiontube.data.shorts.ShortsRepository.getInstance(context)
    }
}
