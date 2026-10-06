import Foundation
import Observation
import SharedLogic

/// A window's presentation models share app-owned services but keep independent tasks and search results.
@MainActor @Observable
final class HomeViewModel {
    private let services: AppServices
    var authDependencies: AuthDependencies { services.authDependencies }
    private(set) var storageFailed = false
    private(set) var feedViewModel: FeedViewModel?
    private(set) var favoriteSaveViewModel: FavoriteSaveViewModel?
    private(set) var favoritesViewModel: FavoritesViewModel?
    let searchViewModel: SearchViewModel

    init(services: AppServices) {
        self.services = services
        let searchClient: any SearchClient
        #if DEBUG
            if ProcessInfo.processInfo.arguments.contains("--search-ui-fixture") {
                searchClient = SearchUITestClient()
            } else {
                searchClient = SharedSearchClient(repository: services.searchDependencies.searchRepository)
            }
        #else
            searchClient = SharedSearchClient(repository: services.searchDependencies.searchRepository)
        #endif
        searchViewModel = SearchViewModel(client: searchClient)
    }

    func prepare() {
        if favoriteSaveViewModel == nil {
            let client = services.prepareFavorites()
            let list = FavoritesViewModel(client: client)
            favoritesViewModel = list
            favoriteSaveViewModel = FavoriteSaveViewModel(client: client, onRemove: list.retainUntilRefresh)
        }
        guard feedViewModel == nil else { return }
        #if DEBUG
            if ProcessInfo.processInfo.arguments.contains("--feed-ui-fixture") {
                feedViewModel = FeedViewModel(client: FeedUITestClient(), isConfigured: true)
                return
            }
        #endif
        do {
            let dependencies = try services.prepareFeed()
            feedViewModel = FeedViewModel(
                client: SharedFeedClient(
                    newsRepository: dependencies.newsRepository, refreshFeedIfNeeded: dependencies.refreshFeedIfNeeded),
                isConfigured: services.feedConfiguration.isConfigured)
            storageFailed = false
        } catch {
            storageFailed = true
        }
    }
}
