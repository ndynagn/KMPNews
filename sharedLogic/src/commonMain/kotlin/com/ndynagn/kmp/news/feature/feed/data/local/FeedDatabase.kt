package com.ndynagn.kmp.news.feature.feed.data.local

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor

@Database(entities = [ArticleEntity::class, FeedMetadataEntity::class], version = 1, exportSchema = true)
@ConstructedBy(FeedDatabaseConstructor::class)
internal abstract class FeedDatabase : RoomDatabase() {
    abstract fun feedDao(): FeedDao
}

// Room/KSP generates the actual constructor in each target; there is no handwritten actual.
@Suppress("KotlinNoActualForExpect")
internal expect object FeedDatabaseConstructor : RoomDatabaseConstructor<FeedDatabase> {
    override fun initialize(): FeedDatabase
}
