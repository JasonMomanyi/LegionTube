package com.github.legiontube.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.github.legiontube.db.dao.CustomInstanceDao
import com.github.legiontube.db.dao.DownloadDao
import com.github.legiontube.db.dao.LocalPlaylistsDao
import com.github.legiontube.db.dao.LocalSubscriptionDao
import com.github.legiontube.db.dao.PlaylistBookmarkDao
import com.github.legiontube.db.dao.SearchHistoryDao
import com.github.legiontube.db.dao.SubscriptionGroupsDao
import com.github.legiontube.db.dao.SubscriptionsFeedDao
import com.github.legiontube.db.dao.WatchHistoryDao
import com.github.legiontube.db.dao.WatchPositionDao
import com.github.legiontube.db.obj.CustomInstance
import com.github.legiontube.db.obj.Download
import com.github.legiontube.db.obj.DownloadChapter
import com.github.legiontube.db.obj.DownloadItem
import com.github.legiontube.db.obj.DownloadPlaylist
import com.github.legiontube.db.obj.DownloadPlaylistVideosCrossRef
import com.github.legiontube.db.obj.DownloadSponsorBlockSegment
import com.github.legiontube.db.obj.LocalPlaylist
import com.github.legiontube.db.obj.LocalPlaylistItem
import com.github.legiontube.db.obj.LocalSubscription
import com.github.legiontube.db.obj.PlaylistBookmark
import com.github.legiontube.db.obj.SearchHistoryItem
import com.github.legiontube.db.obj.SubscriptionGroup
import com.github.legiontube.db.obj.SubscriptionsFeedItem
import com.github.legiontube.db.obj.WatchHistoryItem
import com.github.legiontube.db.obj.WatchPosition

@Database(
    entities = [
        WatchHistoryItem::class,
        WatchPosition::class,
        SearchHistoryItem::class,
        CustomInstance::class,
        LocalSubscription::class,
        PlaylistBookmark::class,
        LocalPlaylist::class,
        LocalPlaylistItem::class,
        Download::class,
        DownloadItem::class,
        DownloadChapter::class,
        DownloadSponsorBlockSegment::class,
        DownloadPlaylist::class,
        DownloadPlaylistVideosCrossRef::class,
        SubscriptionGroup::class,
        SubscriptionsFeedItem::class
    ],
    version = 23,
    autoMigrations = [
        AutoMigration(from = 7, to = 8),
        AutoMigration(from = 8, to = 9),
        AutoMigration(from = 9, to = 10),
        AutoMigration(from = 10, to = 11),
        AutoMigration(from = 16, to = 17),
        AutoMigration(from = 18, to = 19),
        AutoMigration(from = 19, to = 20),
        AutoMigration(from = 20, to = 21)
    ]
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    /**
     * Watch History
     */
    abstract fun watchHistoryDao(): WatchHistoryDao

    /**
     * Watch Positions
     */
    abstract fun watchPositionDao(): WatchPositionDao

    /**
     * Search History
     */
    abstract fun searchHistoryDao(): SearchHistoryDao

    /**
     * Custom Instances
     */
    abstract fun customInstanceDao(): CustomInstanceDao

    /**
     * Local Subscriptions
     */
    abstract fun localSubscriptionDao(): LocalSubscriptionDao

    /**
     * Bookmarked Playlists
     */
    abstract fun playlistBookmarkDao(): PlaylistBookmarkDao

    /**
     * Local playlists
     */
    abstract fun localPlaylistsDao(): LocalPlaylistsDao

    /**
     * Downloads
     */
    abstract fun downloadDao(): DownloadDao

    /**
     * Subscription groups
     */
    abstract fun subscriptionGroupsDao(): SubscriptionGroupsDao

    /**
     * Locally cached subscription feed
     */
    abstract fun feedDao(): SubscriptionsFeedDao
}
