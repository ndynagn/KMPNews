import Foundation
import Observation
import SharedLogic

/// Owns the app's database graph. Screen ViewModels borrow its typed services.
@MainActor @Observable
final class HomeViewModel {
    private(set) var dependencies: FeedDependencies?
    private(set) var storageFailed = false
    let configuration: FeedApiConfiguration

    init() {
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
            storageFailed = false
        } catch {
            storageFailed = true
        }
    }
}
