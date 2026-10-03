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
    private var retainedArticles: [FeedArticle]?
    private let client: any FavoriteSaveClient

    var articles: [FeedArticle] {
        let current = snapshot?.articles ?? []
        guard let retainedArticles else { return current }

        let currentIDs = Set(current.map(\.id))
        var visible = current
        for (index, article) in retainedArticles.enumerated() where !currentIDs.contains(article.id) {
            visible.insert(article, at: min(index, visible.count))
        }
        return visible
    }

    init(client: any FavoriteSaveClient) { self.client = client }

    /// Keep visible rows stable during deletion; only a successful refresh releases them.
    func retainUntilRefresh() {
        retainedArticles = articles
    }

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
        retainedArticles = nil
        hasError = false

        guard email != nil else { return }

        let revision = generation
        observer = Task { [weak self, client] in
            for await read in client.observe() {
                guard !Task.isCancelled, let self, revision == self.generation else { break }

                switch read {
                case .snapshot(let snapshot): self.snapshot = snapshot
                case .signedOut:
                    self.snapshot = nil
                    self.retainedArticles = nil
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
            self.hasError = result != .saved && result != .busy
            self.failedAppend = append
            if result == .saved, !append { self.retainedArticles = nil }
        }
    }
}
