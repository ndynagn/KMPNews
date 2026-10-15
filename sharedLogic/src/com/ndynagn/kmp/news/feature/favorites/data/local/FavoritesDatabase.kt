package com.ndynagn.kmp.news.feature.favorites.data.local

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor

/** Separate file/schema: feed eviction and feed migrations cannot remove saved snapshots. */
@Database(entities = [FavoriteEntity::class, FavoritesMetadataEntity::class], version = 1, exportSchema = true)
@ConstructedBy(FavoritesDatabaseConstructor::class)
internal abstract class FavoritesDatabase : RoomDatabase() {
    abstract fun favoritesDao(): FavoritesDao
}

// Room/KSP supplies platform actuals.
@Suppress("KotlinNoActualForExpect")
internal expect object FavoritesDatabaseConstructor : RoomDatabaseConstructor<FavoritesDatabase> {
    override fun initialize(): FavoritesDatabase
}
