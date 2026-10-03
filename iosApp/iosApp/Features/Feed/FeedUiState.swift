import Foundation

struct FeedSnapshotState: Equatable {
    let articles: [FeedArticle]
    let hasMore: Bool
    let isCacheLimitReached: Bool
}

enum FeedOperation { case activate, refresh, append }
enum FeedRead { case snapshot(FeedSnapshotState), storageFailure }
enum FeedUpdate { case success, failure(isStorage: Bool) }

/// Request and recovery information may coexist with cached content.
struct FeedStatus {
    var operation: FeedOperation?
    var failedOperation: FeedOperation?
    var storageFailed = false
    var isConfigured = true
}

enum FeedUiState {
    case initial(FeedStatus)
    case loading(FeedStatus)
    case empty(FeedStatus)
    case error(FeedStatus)
    case content(FeedSnapshotState, FeedStatus)

    var status: FeedStatus {
        switch self {
        case .initial(let status), .loading(let status), .empty(let status), .error(let status),
            .content(_, let status):
            return status
        }
    }

    var snapshot: FeedSnapshotState? {
        if case .content(let snapshot, _) = self { return snapshot }

        return nil
    }

    var canAppend: Bool {
        guard case .content(let snapshot, let status) = self else { return false }

        return status.isConfigured && !status.storageFailed && status.operation == nil
            && status.failedOperation == nil && snapshot.hasMore && !snapshot.isCacheLimitReached
    }
}
