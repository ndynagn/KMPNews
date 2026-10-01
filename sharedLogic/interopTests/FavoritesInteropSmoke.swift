import Foundation
import SharedLogic

/// Exercises the real repository and Room through the opt-in Kotlin fixture.
@main
struct FavoritesInteropSmoke {
    @MainActor
    static func main() async throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: directory) }

        let fixture = FavoritesInteropFixture(databasePath: directory.appendingPathComponent("favorites.db").path)
        let observer = Task { @MainActor in
            for await _ in fixture.observeFavorites() {}
        }
        try await waitUntil { fixture.activeObservers == 1 }

        let request = Task { try await fixture.refresh() }
        try await fixture.awaitRequestStarted()
        request.cancel()
        do {
            _ = try await request.value
            preconditionFailure("Cancellation was swallowed")
        } catch is CancellationError {}
        try await waitUntil { fixture.activeRequests == 0 }
        fixture.allowResponse()

        let result = try await fixture.refresh()
        precondition(result is FavoritesUpdateResultUpdated)
        for await value in fixture.observeFavorites() {
            guard let snapshot = value as? FavoritesReadResultSnapshot else {
                preconditionFailure("Expected a snapshot")
            }
            precondition(snapshot.articles.count == 1)
            precondition(snapshot.articles[0].article.id == "saved")
            precondition(snapshot.articles[0].article.title == nil)
            precondition(!snapshot.hasMore)
            break
        }

        observer.cancel()
        await observer.value
        try await waitUntil { fixture.activeObservers == 0 }
        fixture.signOut()
        for await value in fixture.observeFavorites() {
            precondition(value is FavoritesReadResultSignedOut)
            break
        }
        let failure = try await fixture.refresh()
        precondition((failure as? FavoritesUpdateResultFailed)?.failure == .authRequired)
        try await waitUntil { fixture.activeObservers == 0 }
        fixture.close()
        print("PASS: favorites Swift models, Room, suspend cancellation/retry, Flow disposal/resubscription, logout")
    }

    @MainActor
    private static func waitUntil(_ condition: () -> Bool) async throws {
        for _ in 0..<200 {
            if condition() { return }
            try await Task.sleep(nanoseconds: 10_000_000)
        }
        preconditionFailure("Favorites interop lifecycle did not settle")
    }
}
