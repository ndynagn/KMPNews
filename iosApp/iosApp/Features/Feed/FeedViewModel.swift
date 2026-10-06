import Foundation
import Observation

/// The visible screen owns activation. Deactivation cancels and awaits all work before reactivation.
@MainActor @Observable
final class FeedViewModel {
    private(set) var state: FeedUiState
    private(set) var errorFeedback = 0
    private var status: FeedStatus
    private var snapshot: FeedSnapshotState?
    private let client: any FeedClient
    private var observer: Task<Void, Never>?
    private var request: Task<Void, Never>?
    private var cleanup: Task<Void, Never>?
    private var activation: Task<Void, Never>?
    private var isActive = false
    private var generation = 0

    init(client: any FeedClient, isConfigured: Bool) {
        self.client = client

        let initialStatus = FeedStatus(isConfigured: isConfigured)
        status = initialStatus
        state = .initial(initialStatus)
    }

    func activate() {
        guard !isActive, activation == nil else { return }

        generation += 1

        let session = generation
        activation = Task { [weak self, cleanup] in
            await cleanup?.value
            guard !Task.isCancelled, let self, self.generation == session else { return }

            self.isActive = true
            self.observe()
            self.start(.activate)
            self.activation = nil
        }
    }

    func deactivate() {
        isActive = false
        generation += 1
        activation?.cancel()
        activation = nil
        observer?.cancel()
        request?.cancel()
        cleanup = Task { [cleanup, observer, request] in
            await cleanup?.value
            await observer?.value
            await request?.value
        }
        observer = nil
        request = nil
        status.operation = nil
        publishState()
    }

    func onEvent(_ event: FeedEvent) {
        guard isActive else { return }

        switch event {
        case .refresh: start(.refresh, userInitiated: true)
        case .loadMore:
            if state.canAppend { start(.append) }
        case .retry:
            guard request == nil else { return }

            if status.storageFailed { observe() }

            let operation = status.failedOperation ?? .activate
            status.failedOperation = nil
            publishState()
            start(operation, userInitiated: true)
        }
    }

    func refresh() async {
        onEvent(.refresh)
        await request?.value
    }

    private func observe() {
        observer?.cancel()

        let session = generation
        observer = Task { [weak self, client] in
            for await read in client.observe() {
                guard !Task.isCancelled, let self, self.generation == session else { break }

                switch read {
                case .snapshot(let snapshot):
                    self.snapshot = snapshot
                    self.status.storageFailed = false
                case .storageFailure: self.status.storageFailed = true
                }

                self.publishState()
            }
        }
    }

    private func start(_ operation: FeedOperation, userInitiated: Bool = false) {
        guard isActive, request == nil, status.isConfigured else { return }

        status.operation = operation
        if operation != .activate { status.failedOperation = nil }
        publishState()

        let session = generation
        request = Task { [weak self, client] in
            do {
                let result = try await client.update(operation)
                guard !Task.isCancelled, let self, self.generation == session else { return }

                self.finish(operation, result: result, userInitiated: userInitiated)
                self.request = nil
            } catch {
                guard !Task.isCancelled, let self, self.generation == session else { return }

                self.finish(operation, result: .failure(isStorage: false), userInitiated: userInitiated)
                self.request = nil
            }
        }
    }

    private func finish(_ operation: FeedOperation, result: FeedUpdate, userInitiated: Bool) {
        status.operation = nil
        if case .failure(let isStorage) = result {
            if userInitiated { errorFeedback += 1 }
            if operation != .activate || status.failedOperation == nil { status.failedOperation = operation }
            status.storageFailed = status.storageFailed || isStorage
        }

        publishState()
    }

    private func publishState() {
        if let snapshot, !snapshot.articles.isEmpty {
            state = .content(snapshot, status)
        } else if status.storageFailed || status.failedOperation != nil {
            state = .error(status)
        } else if status.operation != nil || snapshot == nil {
            state = .loading(status)
        } else {
            state = .empty(status)
        }
    }
}
