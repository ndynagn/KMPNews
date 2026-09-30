package com.ndynagn.kmp.news.feature.feed.data

import com.ndynagn.kmp.news.feature.feed.data.local.CachedFeed
import com.ndynagn.kmp.news.feature.feed.data.local.FeedStore
import com.ndynagn.kmp.news.feature.feed.data.remote.NewsRemoteSource
import com.ndynagn.kmp.news.feature.feed.data.remote.PageResult
import com.ndynagn.kmp.news.feature.feed.domain.Article
import com.ndynagn.kmp.news.feature.feed.domain.FeedClock
import com.ndynagn.kmp.news.feature.feed.domain.FeedFailure
import com.ndynagn.kmp.news.feature.feed.domain.FeedReadResult
import com.ndynagn.kmp.news.feature.feed.domain.FeedSnapshot
import com.ndynagn.kmp.news.feature.feed.domain.FeedUpdateResult
import com.ndynagn.kmp.news.feature.feed.domain.NewsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex

internal class OfflineFirstNewsRepository(
    private val feedStore: FeedStore,
    private val newsRemoteSource: NewsRemoteSource,
    private val clock: FeedClock,
) : NewsRepository {
    private val mutation = Mutex()

    override fun observeFeed(): Flow<FeedReadResult> = feedStore.observe()
        .map<CachedFeed, FeedReadResult> { cached ->
            FeedReadResult.Snapshot(
                FeedSnapshot(
                    cached.articles,
                    cached.refreshedAt,
                    cached.nextPage != null,
                    cached.articles.size >= CACHE_LIMIT,
                ),
            )
        }
        .catch { failure ->
            if (failure is CancellationException) throw failure

            emit(FeedReadResult.StorageFailure)
        }

    override suspend fun refresh(): FeedUpdateResult = update(refresh = true)

    override suspend fun loadNextPage(): FeedUpdateResult = update(refresh = false)

    private suspend fun update(refresh: Boolean): FeedUpdateResult {
        if (!mutation.tryLock()) return FeedUpdateResult.AlreadyRunning

        try {
            val previous = try {
                feedStore.read()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                return FeedUpdateResult.Failed(FeedFailure.STORAGE)
            }

            val isFirstPage = refresh || previous.refreshedAt == null

            if (!isFirstPage) {
                if (previous.nextPage == null) return FeedUpdateResult.EndReached

                if (previous.articles.size >= CACHE_LIMIT) return FeedUpdateResult.CacheLimitReached
            }

            val response = newsRemoteSource.fetch(if (isFirstPage) null else previous.nextPage)

            if (response is PageResult.Failure) return FeedUpdateResult.Failed(response.failure)

            val page = (response as PageResult.Success).page
            val updated = CachedFeed(
                merge(previous.articles, page.articles),
                if (isFirstPage) clock.nowEpochMilliseconds() else previous.refreshedAt,
                page.nextPage,
            )

            try {
                feedStore.write(updated)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                return FeedUpdateResult.Failed(FeedFailure.STORAGE)
            }

            return FeedUpdateResult.Updated
        } finally {
            mutation.unlock()
        }
    }

    private fun merge(previous: List<Article>, incoming: List<Article>): List<Article> {
        // Linked insertion order preserves old ties; updated values keep their position.
        val articlesById = previous.associateByTo(linkedMapOf()) { it.id }

        incoming.forEach { articlesById[it.id] = it }

        return articlesById.values.sortedWith(
            compareByDescending<Article> { it.publishedAtEpochMilliseconds != null }
                .thenByDescending { it.publishedAtEpochMilliseconds },
        ).take(CACHE_LIMIT)
    }

    private companion object {
        const val CACHE_LIMIT = 200
    }
}
