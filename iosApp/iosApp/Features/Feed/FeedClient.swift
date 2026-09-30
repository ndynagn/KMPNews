import Foundation

/// Native presentation boundary for deterministic tests; policy and storage remain in SharedLogic.
@MainActor
protocol FeedClient {
    func observe() -> AsyncStream<FeedRead>
    func update(_ operation: FeedOperation) async throws -> FeedUpdate
}
