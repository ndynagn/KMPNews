import Foundation
import SharedLogic

/// The application owns resource graphs. Window view models never close resources used by another window.
@MainActor
final class AppServices {
    let authDependencies: AuthDependencies
    let searchDependencies: SearchDependencies
    let feedConfiguration: FeedApiConfiguration
    private(set) var feedDependencies: FeedDependencies?
    private var favoritesDependencies: FavoritesDependencies?
    private var favoriteClient: (any FavoriteSaveClient)?

    init() {
        let url = Bundle.main.object(forInfoDictionaryKey: "SupabaseURL") as? String ?? ""
        let key = Bundle.main.object(forInfoDictionaryKey: "SupabasePublishableKey") as? String ?? ""

        authDependencies = AuthFactory_appleKt.createAuthDependencies(
            configuration: AuthConfiguration(projectUrl: url, publishableKey: key), storage: KeychainAuthStorage())
        feedConfiguration = FeedApiConfiguration(supabaseUrl: url, publishableKey: key)
        searchDependencies = SearchFactory_appleKt.createSearchDependencies(
            configuration: NewsApiConfiguration(supabaseUrl: url, publishableKey: key))
    }

    func prepareFeed() throws -> FeedDependencies {
        if let feedDependencies { return feedDependencies }

        let dependencies = FeedFactory_appleKt.createFeedDependencies(
            databasePath: try databasePath("news-feed.db"), configuration: feedConfiguration)

        feedDependencies = dependencies

        return dependencies
    }

    func prepareFavorites() -> any FavoriteSaveClient {
        if let favoriteClient { return favoriteClient }

        let client: any FavoriteSaveClient
        #if DEBUG
            if ProcessInfo.processInfo.arguments.contains("--auth-ui-fixture") {
                let fixture = FavoriteSaveUITestClient()
                favoriteClient = fixture
                return fixture
            }
        #endif

        do {
            let dependencies = FavoritesFactory_appleKt.createFavoritesDependencies(
                authDependencies: authDependencies, databasePath: try databasePath("favorites.db"))
            favoritesDependencies = dependencies
            client = SharedFavoriteSaveClient(repository: dependencies.favoritesRepository)
        } catch {
            client = UnavailableFavoriteSaveClient()
        }

        favoriteClient = client

        return client
    }

    private func databasePath(_ name: String) throws -> String {
        try FileManager.default.url(
            for: .applicationSupportDirectory, in: .userDomainMask, appropriateFor: nil, create: true
        ).appendingPathComponent(name).path
    }
}

@MainActor
private struct UnavailableFavoriteSaveClient: FavoriteSaveClient {
    func save(_ article: FeedArticle) async throws -> FavoriteSaveResult { .failed }
    func remove(_ articleID: String) async throws -> FavoriteSaveResult { .failed }
    func membership(_ articleIDs: [String]) async throws -> FavoritesMembership { .failed }
    func refresh() async throws -> FavoriteSaveResult { .failed }
    func loadMore() async throws -> FavoriteSaveResult { .failed }
    func observe() -> AsyncStream<FavoritesRead> {
        AsyncStream {
            $0.yield(.failed)
            $0.finish()
        }
    }
}
