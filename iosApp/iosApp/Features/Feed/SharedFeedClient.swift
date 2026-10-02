import Foundation
import SharedLogic

@MainActor
final class SharedFeedClient: FeedClient {
    private let newsRepository: any NewsRepository
    private let refreshFeedIfNeeded: RefreshFeedIfNeeded

    init(newsRepository: any NewsRepository, refreshFeedIfNeeded: RefreshFeedIfNeeded) {
        self.newsRepository = newsRepository
        self.refreshFeedIfNeeded = refreshFeedIfNeeded
    }

    func observe() -> AsyncStream<FeedRead> {
        AsyncStream { continuation in
            let task = Task { @MainActor [newsRepository] in
                for await value in newsRepository.observeFeed() {
                    if Task.isCancelled { break }
                    if let read = value as? FeedReadResultSnapshot {
                        let articles = read.feed.articles.map { article in
                            FeedArticle(
                                id: article.id, title: article.title, summary: article.summary,
                                imageURL: article.imageUrl.flatMap(URL.init(string:)),
                                source: article.sourceName,
                                publishedAt: article.publishedAtEpochMilliseconds.map {
                                    Date(timeIntervalSince1970: Double($0.int64Value) / 1_000)
                                }, articleURL: article.url, sourceID: article.sourceId
                            )
                        }
                        continuation.yield(
                            .snapshot(
                                FeedSnapshotState(
                                    articles: articles, hasMore: read.feed.hasMore,
                                    isCacheLimitReached: read.feed.isCacheLimitReached
                                )))
                    } else {
                        continuation.yield(.storageFailure)
                    }
                }
                continuation.finish()
            }
            continuation.onTermination = { _ in task.cancel() }
        }
    }

    func update(_ operation: FeedOperation) async throws -> FeedUpdate {
        let result: any FeedUpdateResult

        switch operation {
        case .activate: result = try await refreshFeedIfNeeded.invoke()
        case .refresh: result = try await newsRepository.refresh()
        case .append: result = try await newsRepository.loadNextPage()
        }

        if let failure = result as? FeedUpdateResultFailed {
            return .failure(isStorage: failure.failure == .storage)
        }

        return .success
    }
}
