#if DEBUG
    import Foundation

    /// Launch-argument-only fixture; performs no account or network operations.
    @MainActor
    final class FavoriteSaveUITestClient: FavoriteSaveClient {
        private var didFail = false
        private var articles: [FeedArticle] = []
        private var continuations: [UUID: AsyncStream<FavoritesRead>.Continuation] = [:]

        func observe() -> AsyncStream<FavoritesRead> {
            AsyncStream { continuation in
                let id = UUID()
                continuations[id] = continuation
                continuation.yield(.snapshot(snapshot))
                continuation.onTermination = { [weak self] _ in
                    Task { @MainActor in self?.continuations.removeValue(forKey: id) }
                }
            }
        }

        func membership(_ articleIDs: [String]) async throws -> FavoritesMembership {
            .snapshot(Set(articles.map(\.id)).intersection(articleIDs))
        }

        func refresh() async throws -> FavoriteSaveResult {
            publish()
            return .saved
        }

        func loadMore() async throws -> FavoriteSaveResult { .saved }

        func remove(_ articleID: String) async throws -> FavoriteSaveResult {
            try await Task.sleep(for: .milliseconds(150))
            if ProcessInfo.processInfo.arguments.contains("--favorites-ui-remove-error") { return .failed }
            articles.removeAll { $0.id == articleID }
            publish()
            return .saved
        }

        func save(_ article: FeedArticle) async throws -> FavoriteSaveResult {
            try await Task.sleep(for: .milliseconds(150))
            if ProcessInfo.processInfo.arguments.contains("--favorites-ui-error"), !didFail {
                didFail = true
                return .failed
            }
            if !articles.contains(where: { $0.id == article.id }) { articles.insert(article, at: 0) }
            publish()
            return .saved
        }

        private var snapshot: FavoritesSnapshot {
            FavoritesSnapshot(articles: articles, hasMore: false, isInitialized: true)
        }

        private func publish() {
            for continuation in continuations.values { continuation.yield(.snapshot(snapshot)) }
        }
    }
#endif
