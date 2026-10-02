import Foundation
import Observation

/// Renders the account-scoped Room stream; failed page requests preserve the visible cache.
@MainActor @Observable
final class FavoritesViewModel {
    private(set) var snapshot: FavoritesSnapshot?
    private(set) var isLoading = false
    private(set) var hasError = false
    private var observer: Task<Void, Never>?
    private var request: Task<Void, Never>?
    private var generation = 0
    private var account: String?
    private var failedAppend = false
    private let client: any FavoriteSaveClient

    init(client: any FavoriteSaveClient) { self.client = client }

    func accountChanged(_ profile: ProfileState) {
        let email: String?
        switch profile {
        case .authenticated(let value): email = value
        case .guest: email = nil
        case .restoring, .unavailable: return
        }

        guard email != account else { return }

        stop()
        account = email
        snapshot = nil
        hasError = false

        guard email != nil else { return }

        let revision = generation
        observer = Task { [weak self, client] in
            for await read in client.observe() {
                guard !Task.isCancelled, let self, revision == self.generation else { break }

                switch read {
                case .snapshot(let snapshot): self.snapshot = snapshot
                case .signedOut: self.snapshot = nil
                case .failed: self.hasError = true
                }
            }
        }
    }

    func refresh() async {
        start(append: false)
        await request?.value
    }

    func loadMore() {
        guard snapshot?.hasMore == true, !hasError else { return }

        start(append: true)
    }

    func retry() { start(append: failedAppend) }

    func stop() {
        generation += 1
        observer?.cancel()
        observer = nil
        request?.cancel()
        request = nil
        isLoading = false
        account = nil
    }

    private func start(append: Bool) {
        guard account != nil, request == nil else { return }

        isLoading = true
        hasError = false
        let revision = generation
        request = Task { [weak self, client] in
            let result: FavoriteSaveResult
            do {
                result = append ? try await client.loadMore() : try await client.refresh()
            } catch {
                result = .failed
            }

            guard !Task.isCancelled, let self, revision == self.generation else { return }

            self.isLoading = false
            self.request = nil
            self.hasError = result != .saved
            self.failedAppend = append
        }
    }
}
