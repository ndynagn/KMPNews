package com.ndynagn.kmp.news.feature.favorites.data.local

import com.ndynagn.kmp.news.feature.favorites.data.FavoriteDto
import com.ndynagn.kmp.news.feature.favorites.data.FavoritesCursor
import com.ndynagn.kmp.news.feature.favorites.data.FavoritesPage
import com.ndynagn.kmp.news.feature.favorites.data.favoritesJson
import kotlinx.coroutines.flow.map
import kotlin.time.Instant

internal class RoomFavoritesStore(private val dao: FavoritesDao) : FavoritesStore {
    override fun observe(userId: String) = dao.observe(userId).map { it.toCache() }

    override suspend fun read(userId: String): CachedFavorites = dao.read(userId).toCache()

    override suspend fun writePage(userId: String, page: FavoritesPage, replace: Boolean) {
        require(page.entries.all { it.userId == userId })
        dao.writePage(
            page.entries.map { it.toEntity() },
            FavoritesMetadataEntity(userId, page.next?.addedAt, page.next?.articleId, isInitialized = true),
            replace,
        )
    }

    override suspend fun save(entry: FavoriteDto) = dao.save(entry.toEntity())

    override suspend fun remove(userId: String, articleId: String) = dao.remove(userId, articleId)

    private fun FavoriteDto.toEntity() = FavoriteEntity(userId, articleId, favoritesJson.encodeToString(this))

    private fun List<StoredFavorites>.toCache(): CachedFavorites {
        val stored = singleOrNull() ?: return CachedFavorites()
        val metadata = stored.metadata
        val entries = stored.entries.map { entity ->
            favoritesJson.decodeFromString<FavoriteDto>(entity.payload).also {
                require(it.userId == metadata.userId && it.articleId == entity.articleId)
            }
        }.sortedWith(compareByDescending<FavoriteDto> { Instant.parse(it.addedAt) }.thenByDescending { it.articleId })
        val cursor = metadata.cursorTime?.let { FavoritesCursor(it, requireNotNull(metadata.cursorId)) }

        return CachedFavorites(entries, cursor, metadata.isInitialized)
    }
}
