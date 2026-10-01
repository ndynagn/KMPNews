package com.ndynagn.kmp.news.feature.favorites.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
internal abstract class FavoritesDao {
    @Transaction
    @Query("SELECT * FROM favorites_metadata WHERE userId = :userId")
    abstract fun observe(userId: String): Flow<List<StoredFavorites>>

    @Transaction
    @Query("SELECT * FROM favorites_metadata WHERE userId = :userId")
    abstract suspend fun read(userId: String): List<StoredFavorites>

    @Transaction
    open suspend fun writePage(entries: List<FavoriteEntity>, metadata: FavoritesMetadataEntity, replace: Boolean) {
        if (replace) clear(metadata.userId)

        insertEntries(entries)
        insertMetadata(metadata)
    }

    @Transaction
    open suspend fun save(entry: FavoriteEntity) {
        ensureMetadata(FavoritesMetadataEntity(entry.userId))
        insertEntries(listOf(entry))
    }

    @Query("DELETE FROM favorites WHERE userId = :userId AND articleId = :articleId")
    abstract suspend fun remove(userId: String, articleId: String)

    @Query("DELETE FROM favorites WHERE userId = :userId")
    protected abstract suspend fun clear(userId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertEntries(entries: List<FavoriteEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertMetadata(metadata: FavoritesMetadataEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun ensureMetadata(metadata: FavoritesMetadataEntity)
}
