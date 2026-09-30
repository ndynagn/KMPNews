import Foundation
import SharedLogic

/// Runs against a fixture-enabled framework only; never part of either app target.
@main
struct FeedInteropSmoke {
    @MainActor
    static func main() async throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: directory) }

        let dependencies = FeedFactory_appleKt.createFeedDependencies(
            databasePath: directory.appendingPathComponent("composition.db").path,
            configuration: FeedApiConfiguration(supabaseUrl: "", publishableKey: "")
        )
        let missingKey = try await dependencies.newsRepository.refresh()

        precondition((missingKey as? FeedUpdateResultFailed)?.failure == .accessDenied)
        dependencies.close()

        let debugLogger = SmokeHttpLogger()
        let debugDependencies = FeedFactory_appleKt.createFeedDependencies(
            databasePath: directory.appendingPathComponent("debug-composition.db").path,
            configuration: FeedApiConfiguration(supabaseUrl: "", publishableKey: ""),
            httpLogger: debugLogger
        )
        let debugMissingKey = try await debugDependencies.newsRepository.refresh()

        precondition((debugMissingKey as? FeedUpdateResultFailed)?.failure == .accessDenied)
        debugDependencies.close()

        let unreadable = FeedInteropFixture(databasePath: directory.path, shouldFail: false)
        for await value in unreadable.observeFeed() {
            precondition(value is FeedReadResultStorageFailure)
        }
        precondition(unreadable.activeObservers == 0)
        unreadable.close()

        let path = directory.appendingPathComponent("feed.db").path
        let fixture = FeedInteropFixture(databasePath: path, shouldFail: false)
        try await fixture.verifyHttpLogging(httpLogger: debugLogger)

        precondition(debugLogger.hasMessages)

        let observer = Task { @MainActor in
            for await _ in fixture.observeFeed() {}
        }
        try await waitUntil { fixture.activeObservers == 1 }
        fixture.allowResponse()

        let update = try await fixture.refresh()

        precondition(update is FeedUpdateResultUpdated)

        for await value in fixture.observeFeed() {
            guard let snapshot = value as? FeedReadResultSnapshot else {
                preconditionFailure("Unexpected storage failure")
            }

            precondition(snapshot.feed.articles.count == 1)

            let article = snapshot.feed.articles[0]

            precondition(article.id == "fixture")
            precondition(article.title == nil)
            precondition(article.summary == "summary")
            precondition(article.publishedAtEpochMilliseconds?.int64Value == 1_000)
            break
        }

        observer.cancel()
        await observer.value
        try await waitUntil { fixture.activeObservers == 0 }

        // Resubscribe after cancellation: the app-owned repository is still usable.

        for await value in fixture.observeFeed() {
            precondition(value is FeedReadResultSnapshot)
            break
        }
        try await waitUntil { fixture.activeObservers == 0 }

        fixture.close()

        let reopened = FeedInteropFixture(databasePath: path, shouldFail: true)
        reopened.allowResponse()

        let failure = try await reopened.refresh()

        precondition((failure as? FeedUpdateResultFailed)?.failure == .quotaExceeded)

        for await value in reopened.observeFeed() {
            precondition((value as? FeedReadResultSnapshot)?.feed.articles.count == 1)
            break
        }
        try await waitUntil { reopened.activeObservers == 0 }
        reopened.close()

        let cancellation = FeedInteropFixture(
            databasePath: directory.appendingPathComponent("cancel.db").path,
            shouldFail: false
        )
        let request = Task { try await cancellation.refresh() }
        try await cancellation.awaitRequestStarted()
        precondition(cancellation.activeRequests == 1)

        request.cancel()
        do {
            _ = try await request.value
            preconditionFailure("Suspend cancellation was swallowed")
        } catch is CancellationError {}
        try await cancellation.awaitRequestFinished()
        precondition(cancellation.activeRequests == 0)

        cancellation.allowResponse()

        let retried = try await cancellation.refresh()

        precondition(retried is FeedUpdateResultUpdated)
        cancellation.close()

        print(
            "PASS: Swift models, Koin, Room reopen, suspend success/failure/cancellation, Flow cancellation/resubscription"
        )
    }

    @MainActor
    private static func waitUntil(_ condition: () -> Bool) async throws {
        for _ in 0..<200 {
            if condition() { return }
            try await Task.sleep(nanoseconds: 10_000_000)
        }

        preconditionFailure("Interop lifecycle did not settle")
    }
}

/// Protects callback evidence because Kotlin can invoke this sink on worker threads.
private final class SmokeHttpLogger: FeedHttpLogger {
    private let lock = NSLock()
    private var receivedMessage = false

    var hasMessages: Bool {
        lock.lock()
        defer { lock.unlock() }

        return receivedMessage
    }

    func log(message: String) {
        precondition(!message.contains("sb_publishable_fixture-key"))

        lock.lock()
        defer { lock.unlock() }
        receivedMessage = true
    }
}
