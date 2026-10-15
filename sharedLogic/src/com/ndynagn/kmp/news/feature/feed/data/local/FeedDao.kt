package com.ndynagn.kmp.news.feature.feed.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
internal abstract class FeedDao {
    @Transaction
    @Query("SELECT * FROM feed_metadata WHERE id = 1")
    abstract fun observe(): Flow<List<StoredFeed>>

    @Transaction
    @Query("SELECT * FROM feed_metadata WHERE id = 1")
    abstract suspend fun read(): List<StoredFeed>

    @Transaction
    open suspend fun replace(articles: List<ArticleEntity>, metadata: FeedMetadataEntity) {
        clearArticles()
        insertArticles(articles)
        insertMetadata(metadata)
    }

    @Query("DELETE FROM articles")
    protected abstract suspend fun clearArticles()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertArticles(articles: List<ArticleEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertMetadata(metadata: FeedMetadataEntity)
}
