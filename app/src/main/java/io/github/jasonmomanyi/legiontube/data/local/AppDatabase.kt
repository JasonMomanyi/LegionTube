package io.github.jasonmomanyi.legiontube.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import io.github.jasonmomanyi.legiontube.data.local.dao.CacheDao
import io.github.jasonmomanyi.legiontube.data.local.dao.DownloadDao
import io.github.jasonmomanyi.legiontube.data.local.dao.SpotifyEngineDao
import io.github.jasonmomanyi.legiontube.data.local.dao.DownloadedSongDao
import io.github.jasonmomanyi.legiontube.data.local.dao.NotificationDao
import io.github.jasonmomanyi.legiontube.data.local.dao.PlaylistDao
import io.github.jasonmomanyi.legiontube.data.local.dao.SubscriptionGroupDao
import io.github.jasonmomanyi.legiontube.data.local.dao.VideoDao
import io.github.jasonmomanyi.legiontube.data.local.dao.WatchHistoryDao
import io.github.jasonmomanyi.legiontube.data.local.entity.DownloadEntity
import io.github.jasonmomanyi.legiontube.data.local.entity.DownloadItemEntity
import io.github.jasonmomanyi.legiontube.data.local.entity.DownloadedSongEntity
import io.github.jasonmomanyi.legiontube.data.local.entity.MusicHomeCacheEntity
import io.github.jasonmomanyi.legiontube.data.local.entity.NotificationEntity
import io.github.jasonmomanyi.legiontube.data.local.entity.PlaylistEntity
import io.github.jasonmomanyi.legiontube.data.local.entity.PlaylistVideoCrossRef
import io.github.jasonmomanyi.legiontube.data.local.entity.MusicHomeChipEntity
import io.github.jasonmomanyi.legiontube.data.local.entity.SubscriptionFeedEntity
import io.github.jasonmomanyi.legiontube.data.local.entity.SubscriptionGroupEntity
import io.github.jasonmomanyi.legiontube.data.local.entity.VideoEntity
import io.github.jasonmomanyi.legiontube.data.local.entity.WatchHistoryEntity
import io.github.jasonmomanyi.legiontube.data.recommendation.engines.spotify.SpotifyCachedRecommendation
import io.github.jasonmomanyi.legiontube.data.recommendation.engines.spotify.SpotifyEmbedding
import io.github.jasonmomanyi.legiontube.data.recommendation.engines.spotify.SpotifyInteraction

@Database(
    entities = [
        VideoEntity::class,
        PlaylistEntity::class,
        PlaylistVideoCrossRef::class,
        NotificationEntity::class,
        SubscriptionFeedEntity::class,
        MusicHomeCacheEntity::class,
        MusicHomeChipEntity::class,
        DownloadedSongEntity::class,
        DownloadEntity::class,
        DownloadItemEntity::class,
        WatchHistoryEntity::class,
        SubscriptionGroupEntity::class,
        SpotifyInteraction::class,
        SpotifyEmbedding::class,
        SpotifyCachedRecommendation::class
    ],
    version = 19,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun videoDao(): VideoDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun notificationDao(): NotificationDao
    abstract fun cacheDao(): CacheDao
    abstract fun downloadedSongDao(): DownloadedSongDao
    abstract fun downloadDao(): DownloadDao
    abstract fun watchHistoryDao(): WatchHistoryDao
    abstract fun subscriptionGroupDao(): SubscriptionGroupDao
    abstract fun spotifyEngineDao(): SpotifyEngineDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS watch_history (
                        videoId      TEXT    NOT NULL PRIMARY KEY,
                        position     INTEGER NOT NULL,
                        duration     INTEGER NOT NULL,
                        timestamp    INTEGER NOT NULL,
                        title        TEXT    NOT NULL,
                        thumbnailUrl TEXT    NOT NULL,
                        channelName  TEXT    NOT NULL,
                        channelId    TEXT    NOT NULL,
                        isMusic      INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_watch_history_videoId ON watch_history(videoId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_watch_history_timestamp ON watch_history(timestamp)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_watch_history_isMusic ON watch_history(isMusic)")
            }
        }

        // Devices that installed the buggy 10→11 migration (missing the unique
        // videoId index) need this patch migration to add it.
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_watch_history_videoId ON watch_history(videoId)")
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE playlists ADD COLUMN isUserCreated INTEGER NOT NULL DEFAULT 1")
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE downloads ADD COLUMN sponsorBlockSegmentsJson TEXT")
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS subscription_groups (
                        name TEXT NOT NULL PRIMARY KEY,
                        channelIds TEXT NOT NULL DEFAULT '',
                        sortOrder INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE subscription_feed_cache ADD COLUMN isLive INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE watch_history ADD COLUMN isShort INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_watch_history_isShort ON watch_history(isShort)")
            }
        }

        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Spotify Algorithm Engine — interaction tracking
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS spotify_interactions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        userId TEXT NOT NULL DEFAULT 'local_user',
                        trackId TEXT NOT NULL,
                        channelId TEXT NOT NULL,
                        title TEXT NOT NULL,
                        thumbnailUrl TEXT NOT NULL DEFAULT '',
                        channelName TEXT NOT NULL DEFAULT '',
                        duration INTEGER NOT NULL DEFAULT 0,
                        playCount INTEGER NOT NULL DEFAULT 1,
                        skipCount INTEGER NOT NULL DEFAULT 0,
                        likeCount INTEGER NOT NULL DEFAULT 0,
                        totalWatchPercent REAL NOT NULL DEFAULT 0,
                        weight REAL NOT NULL DEFAULT 1.0,
                        lastInteractedAt INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_spotify_interactions_trackId ON spotify_interactions(trackId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_spotify_interactions_userId_trackId ON spotify_interactions(userId, trackId)")

                // Spotify Algorithm Engine — latent factor embeddings
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS spotify_embeddings (
                        trackId TEXT NOT NULL PRIMARY KEY,
                        factors TEXT NOT NULL,
                        norm REAL NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_spotify_embeddings_trackId ON spotify_embeddings(trackId)")

                // Spotify Algorithm Engine — cached recommendations
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS spotify_cached_recs (
                        trackId TEXT NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL DEFAULT '',
                        channelName TEXT NOT NULL DEFAULT '',
                        channelId TEXT NOT NULL DEFAULT '',
                        thumbnailUrl TEXT NOT NULL DEFAULT '',
                        duration INTEGER NOT NULL DEFAULT 0,
                        viewCount INTEGER NOT NULL DEFAULT 0,
                        uploadDate TEXT NOT NULL DEFAULT '',
                        score REAL NOT NULL,
                        source TEXT NOT NULL,
                        cachedAt INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_spotify_cached_recs_cachedAt ON spotify_cached_recs(cachedAt)")
            }
        }

        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE subscription_feed_cache ADD COLUMN isUpcoming INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getDatabase(context: android.content.Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = androidx.room.Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "flow_database"
                )
                .addMigrations(MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
