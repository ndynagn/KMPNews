import Foundation

enum FavoriteSaveResult { case saved, authenticationRequired, busy, failed }

struct FavoritesSnapshot: Equatable {
    let articles: [FeedArticle]
    let hasMore: Bool
    let isInitialized: Bool
}

enum FavoritesRead { case snapshot(FavoritesSnapshot), signedOut, failed }
enum FavoritesMembership { case snapshot(Set<String>), failed }

/// Saves a complete article snapshot; cancellation propagates to the shared repository.
@MainActor
protocol FavoriteSaveClient {
    func save(_ article: FeedArticle) async throws -> FavoriteSaveResult
    func remove(_ articleID: String) async throws -> FavoriteSaveResult
    func membership(_ articleIDs: [String]) async throws -> FavoritesMembership
    func observe() -> AsyncStream<FavoritesRead>
    func refresh() async throws -> FavoriteSaveResult
    func loadMore() async throws -> FavoriteSaveResult
}
