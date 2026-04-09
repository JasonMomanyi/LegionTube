package com.github.legiontube.db.obj

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "searchHistoryItem")
data class SearchHistoryItem(
    @PrimaryKey val query: String = ""
)
