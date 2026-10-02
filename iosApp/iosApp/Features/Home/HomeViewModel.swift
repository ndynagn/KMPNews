import Foundation
import Observation
import SharedLogic

/// Owns independent Auth and feed graphs. A feed-storage failure does not block account access.
@MainActor @Observable
final class HomeViewModel {
    private(set) var dependencies: FeedDependencies?
    private(set) var storageFailed = false
    let configuration: FeedApiConfiguration
    let authDependencies: AuthDependencies
    private(set) var feedViewModel: FeedViewModel?
    private(set) var favoriteSaveViewModel: FavoriteSaveViewModel?
    private(set) var favoritesViewModel: FavoritesViewModel?
    private var favoritesDependencies: FavoritesDependencies?

    init() {
        let authConfiguration = AuthConfiguration(
            projectUrl: Bundle.main.object(forInfoDictionaryKey: "SupabaseURL") as? String ?? "",
            publishableKey: Bundle.main.object(forInfoDictionaryKey: "SupabasePublishableKey") as? String ?? ""
        )
        authDependencies = AuthFactory_appleKt.createAuthDependencies(
            configuration: authConfiguration, storage: KeychainAuthStorage())
        configuration = FeedApiConfiguration(
            supabaseUrl: Bundle.main.object(forInfoDictionaryKey: "SupabaseURL") as? String ?? "",
            publishableKey: Bundle.main.object(forInfoDictionaryKey: "SupabasePublishableKey") as? String ?? ""
        )
    }

    func prepare() {
        prepareFavorites()
        #if DEBUG
            if ProcessInfo.processInfo.arguments.contains("--feed-ui-fixture") {
                if feedViewModel == nil {
                    feedViewModel = FeedViewModel(client: FeedUITestClient(), isConfigured: true)
                }
                return
            }
        #endif
        guard dependencies == nil else { return }

        do {
            let directory = try FileManager.default.url(
                for: .applicationSupportDirectory, in: .userDomainMask, appropriateFor: nil, create: true
            )
            dependencies = FeedFactory_appleKt.createFeedDependencies(
                databasePath: directory.appendingPathComponent("news-feed.db").path, configuration: configuration
            )
            if let dependencies {
                feedViewModel = FeedViewModel(
                    client: SharedFeedClient(
                        newsRepository: dependencies.newsRepository,
                        refreshFeedIfNeeded: dependencies.refreshFeedIfNeeded),
                    isConfigured: configuration.isConfigured)
            }
            storageFailed = false
        } catch {
            storageFailed = true
        }
    }

    private func prepareFavorites() {
        guard favoriteSaveViewModel == nil else { return }
        #if DEBUG
            if ProcessInfo.processInfo.arguments.contains("--auth-ui-fixture") {
                let client = FavoriteSaveUITestClient()
                favoriteSaveViewModel = FavoriteSaveViewModel(client: client)
                favoritesViewModel = FavoritesViewModel(client: client)
                return
            }
        #endif
        do {
            let directory = try FileManager.default.url(
                for: .applicationSupportDirectory, in: .userDomainMask, appropriateFor: nil, create: true)
            let dependencies = FavoritesFactory_appleKt.createFavoritesDependencies(
                authDependencies: authDependencies,
                databasePath: directory.appendingPathComponent("favorites.db").path)
            favoritesDependencies = dependencies
            let client = SharedFavoriteSaveClient(repository: dependencies.favoritesRepository)
            favoriteSaveViewModel = FavoriteSaveViewModel(client: client)
            favoritesViewModel = FavoritesViewModel(client: client)
        } catch {
            // Feed and authentication remain available when favorites storage cannot open.
            favoriteSaveViewModel = FavoriteSaveViewModel(client: UnavailableFavoriteSaveClient())
            favoritesViewModel = FavoritesViewModel(client: UnavailableFavoriteSaveClient())
        }
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
            $0.yield(.failed); $0.finish()
        }
    }
}
