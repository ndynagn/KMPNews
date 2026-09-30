import Foundation

/// Standalone native presentation tests; excluded from the synchronized application source folder.
@main
struct FeedPresentationTests {
    @MainActor
    static func main() async throws {
        let client = FakeFeedClient()
        let model = FeedViewModel(client: client, isConfigured: true)
        model.activate()
        await settle()
        precondition(client.updates == [.activate])
        precondition(model.state.snapshot?.articles.count == 1)

        client.result = .failure(isStorage: false)
        model.onEvent(.loadMore)
        await settle()
        precondition(model.state.status.failedOperation == .append)
        precondition(model.state.snapshot?.articles.count == 1)
        model.deactivate()
        model.activate()
        await settle()
        precondition(model.state.status.failedOperation == .append)
        let failedCount = client.updates.count
        model.onEvent(.loadMore)
        await settle()
        precondition(client.updates.count == failedCount)

        client.result = .success
        model.onEvent(.retry)
        await settle()
        precondition(model.state.status.failedOperation == nil)
        precondition(client.updates.last == .append)

        client.shouldWait = true
        model.onEvent(.refresh)
        model.onEvent(.refresh)
        model.onEvent(.loadMore)
        await settle()
        precondition(client.activeRequests == 1)
        model.deactivate()
        model.activate()
        await settle()
        precondition(client.maximumActiveRequests == 1)
        model.deactivate()
        await settle()
        precondition(client.activeRequests == 0)
        precondition(client.activeObservers == 0)
        precondition(model.state.status.operation == nil)

        let lateClient = FakeFeedClient()
        let lateModel = FeedViewModel(client: lateClient, isConfigured: true)
        lateModel.activate()
        await settle()
        lateClient.shouldWait = true
        lateClient.ignoreCancellation = true
        lateClient.result = .failure(isStorage: false)
        lateModel.onEvent(.refresh)
        await settle()
        lateModel.deactivate()
        await settle()
        precondition(lateModel.state.status.failedOperation == nil)
        precondition(lateModel.state.snapshot?.articles.count == 1)

        let localClient = FakeFeedClient()
        localClient.read = .storageFailure
        let localModel = FeedViewModel(client: localClient, isConfigured: false)
        localModel.activate()
        await settle()
        precondition(localModel.state.status.storageFailed)
        localClient.read = .snapshot(FakeFeedClient.snapshot)
        localModel.onEvent(.retry)
        await settle()
        precondition(!localModel.state.status.storageFailed)
        precondition(localClient.updates.isEmpty)
        precondition(!localModel.state.canAppend)
        localModel.deactivate()
        await settle()

        let emptyClient = FakeFeedClient()
        emptyClient.read = .snapshot(FeedSnapshotState(articles: [], hasMore: false, isCacheLimitReached: false))
        emptyClient.result = .failure(isStorage: false)
        let emptyModel = FeedViewModel(client: emptyClient, isConfigured: true)
        if case .initial = emptyModel.state {} else { preconditionFailure("Expected initial") }
        emptyModel.activate()
        await settle()
        if case .error = emptyModel.state {} else { preconditionFailure("Expected error") }
        emptyClient.result = .success
        emptyModel.onEvent(.retry)
        await settle()
        if case .empty = emptyModel.state {} else { preconditionFailure("Expected empty") }
        precondition(!emptyModel.state.canAppend)
        emptyModel.deactivate()
        await settle()

        for snapshot in [
            FeedSnapshotState(articles: FakeFeedClient.snapshot.articles, hasMore: false, isCacheLimitReached: false),
            FeedSnapshotState(articles: FakeFeedClient.snapshot.articles, hasMore: true, isCacheLimitReached: true),
        ] {
            let cappedClient = FakeFeedClient()
            cappedClient.read = .snapshot(snapshot)
            let capped = FeedViewModel(client: cappedClient, isConfigured: true)
            capped.activate()
            await settle()
            if case .content = capped.state {} else { preconditionFailure("Expected content") }
            capped.onEvent(.loadMore)
            precondition(cappedClient.updates == [.activate])
            cappedClient.result = .failure(isStorage: false)
            capped.onEvent(.refresh)
            await settle()
            precondition(capped.state.snapshot?.articles.count == 1)
            precondition(capped.state.status.failedOperation == .refresh)
            capped.deactivate()
            await settle()
        }
        print(
            "PASS: native feed transitions, retained cache, retry, request serialization, cancellation, storage and missing key"
        )
    }

    @MainActor
    private static func settle() async {
        for _ in 0..<100 { await Task.yield() }
    }
}

@MainActor
private final class FakeFeedClient: FeedClient {
    static let snapshot = FeedSnapshotState(
        articles: [FeedArticle(id: "one", title: nil, summary: nil, imageURL: nil, source: nil, publishedAt: nil)],
        hasMore: true, isCacheLimitReached: false
    )
    var read: FeedRead = .snapshot(snapshot)
    var result = FeedUpdate.success
    var updates: [FeedOperation] = []
    var shouldWait = false
    var ignoreCancellation = false
    var activeRequests = 0
    var maximumActiveRequests = 0
    var activeObservers = 0

    func observe() -> AsyncStream<FeedRead> {
        activeObservers += 1
        return AsyncStream { continuation in
            continuation.yield(read)
            continuation.onTermination = { [weak self] _ in
                Task { @MainActor in self?.activeObservers -= 1 }
            }
            if case .storageFailure = read { continuation.finish() }
        }
    }

    func update(_ operation: FeedOperation) async throws -> FeedUpdate {
        updates.append(operation)
        activeRequests += 1
        maximumActiveRequests = max(maximumActiveRequests, activeRequests)
        defer { activeRequests -= 1 }
        if shouldWait {
            do { try await Task.sleep(for: .seconds(60)) } catch { if !ignoreCancellation { throw error } }
        }
        return result
    }
}
