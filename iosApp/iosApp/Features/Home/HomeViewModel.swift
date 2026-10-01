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
}
