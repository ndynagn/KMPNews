import Foundation
import SharedLogic

@MainActor
final class SharedFavoriteSaveClient: FavoriteSaveClient {
    private let repository: any FavoritesRepository

    init(repository: any FavoritesRepository) { self.repository = repository }

    func observe() -> AsyncStream<FavoritesRead> {
        AsyncStream { continuation in
            let task = Task { @MainActor [repository] in
                for await value in repository.observeFavorites() {
                    guard !Task.isCancelled else { break }
                    if let snapshot = value as? FavoritesReadResultSnapshot {
                        continuation.yield(
                            .snapshot(
                                FavoritesSnapshot(
                                    articles: snapshot.articles.map { item in
                                        let article = item.article
                                        return FeedArticle(
                                            id: article.id, title: article.title, summary: article.summary,
                                            imageURL: article.imageUrl.flatMap(URL.init(string:)),
                                            source: article.sourceName,
                                            publishedAt: article.publishedAtEpochMilliseconds.map {
                                                Date(timeIntervalSince1970: Double($0.int64Value) / 1_000)
                                            }, articleURL: article.url, sourceID: article.sourceId)
                                    }, hasMore: snapshot.hasMore, isInitialized: snapshot.isInitialized)))
                    } else if value is FavoritesReadResultSignedOut {
                        continuation.yield(.signedOut)
                    } else {
                        continuation.yield(.failed)
                    }
                }
                continuation.finish()
            }
            continuation.onTermination = { _ in task.cancel() }
        }
    }

    func membership(_ articleIDs: [String]) async throws -> FavoritesMembership {
        let result = try await repository.membership(articleIds: articleIDs)
        if let snapshot = result as? FavoritesMembershipResultSnapshot {
            return .snapshot(Set(snapshot.articleIds))
        }
        return .failed
    }

    func refresh() async throws -> FavoriteSaveResult { map(try await repository.refresh()) }
    func loadMore() async throws -> FavoriteSaveResult { map(try await repository.loadNextPage()) }
    func remove(_ articleID: String) async throws -> FavoriteSaveResult {
        map(try await repository.remove(articleId: articleID))
    }

    func save(_ article: FeedArticle) async throws -> FavoriteSaveResult {
        let snapshot = Article(
            id: article.id, title: article.title, url: article.articleURL,
            summary: article.summary, imageUrl: article.imageURL?.absoluteString,
            sourceId: article.sourceID, sourceName: article.source,
            publishedAtEpochMilliseconds: article.publishedAt.map {
                KotlinLong(value: Int64(($0.timeIntervalSince1970 * 1_000).rounded()))
            })
        return map(try await repository.add(article: snapshot))
    }

    private func map(_ result: any FavoritesUpdateResult) -> FavoriteSaveResult {
        if result is FavoritesUpdateResultUpdated || result is FavoritesUpdateResultNoMorePages { return .saved }
        if let failure = result as? FavoritesUpdateResultFailed, failure.failure == .authRequired {
            return .authenticationRequired
        }
        return .failed
    }
}
