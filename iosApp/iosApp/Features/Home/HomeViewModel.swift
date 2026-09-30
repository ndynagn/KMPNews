import Foundation
import Observation
import SharedLogic

/// Owns the app's database graph. Screen ViewModels borrow its typed services.
@MainActor @Observable
final class HomeViewModel {
    private(set) var dependencies: FeedDependencies?
    private(set) var storageFailed = false
    let apiKey: String

    init() {
        let configured = Bundle.main.object(forInfoDictionaryKey: "NewsDataAPIKey") as? String ?? ""
        apiKey = configured.hasPrefix("$(") ? "" : configured.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    func prepare() {
        guard dependencies == nil else { return }
        do {
            let directory = try FileManager.default.url(
                for: .applicationSupportDirectory, in: .userDomainMask, appropriateFor: nil, create: true
            )
            dependencies = FeedFactory_appleKt.createFeedDependencies(
                databasePath: directory.appendingPathComponent("news-feed.db").path, apiKey: apiKey
            )
            storageFailed = false
        } catch {
            storageFailed = true
        }
    }
}
