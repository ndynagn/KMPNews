import Foundation
import SharedLogic

@main
struct SearchInteropSmoke {
    @MainActor
    static func main() async throws {
        let dependencies = SearchFactory_appleKt.createSearchDependencies(
            configuration: NewsApiConfiguration(supabaseUrl: "", publishableKey: ""))
        let unconfigured = try await dependencies.searchRepository.search(query: "space", cursor: nil)
        precondition((unconfigured as? SearchResultFailed)?.failure == .notConfigured)
        dependencies.close()
        precondition(SearchQuery.shared.normalize(input: String(repeating: "🌍", count: 100)) != nil)

        for fail in [false, true] {
            let fixture = SearchInteropFixture(shouldFail: fail)
            fixture.allowResponse()
            let result = try await fixture.repository.search(query: "space", cursor: nil)
            if fail {
                precondition((result as? SearchResultFailed)?.failure == .quotaExceeded)
            } else {
                precondition((result as? SearchResultPage)?.articles.first?.id == "fixture")
            }
            fixture.close()
        }
        let fixture = SearchInteropFixture(shouldFail: false)
        let task = Task { try await fixture.repository.search(query: "cancel", cursor: nil) }
        try await fixture.awaitStarted()
        task.cancel()
        _ = await task.result
        try await fixture.awaitFinished()
        fixture.close()
        print("Search interop: typed success, failures and Swift-to-Ktor cancellation passed")
    }
}
