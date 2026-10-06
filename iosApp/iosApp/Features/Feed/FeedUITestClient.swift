#if DEBUG
    import Foundation

    /// Explicit UI-test fixture: local images and articles, with no transport or database access.
    @MainActor
    final class FeedUITestClient: FeedClient {
        private let arguments = ProcessInfo.processInfo.arguments
        private var continuation: AsyncStream<FeedRead>.Continuation?
        private var attempts = 0
        private var didCompleteLoad = false
        private let articles: [FeedArticle]

        private var snapshot: FeedSnapshotState {
            let isEmpty =
                arguments.contains("--feed-ui-empty")
                || (arguments.contains("--feed-ui-error") && attempts < 2)
                || (arguments.contains("--feed-ui-loading") && !didCompleteLoad)
            let content = arguments.contains("--feed-ui-cached-error") ? Array(articles.prefix(6)) : articles

            return FeedSnapshotState(articles: isEmpty ? [] : content, hasMore: false, isCacheLimitReached: false)
        }

        init() {
            let imageURL = ArticleUITestImage.make()
            let detailFixture = ProcessInfo.processInfo.arguments.contains("--article-ui-fixture")
            let longArticle = ProcessInfo.processInfo.arguments.contains("--article-ui-long")
            let longTitle = ProcessInfo.processInfo.arguments.contains("--article-ui-long-title")
            let variedGrid = ProcessInfo.processInfo.arguments.contains("--grid-ui-varied")

            articles = (0..<24).map { index in
                FeedArticle(
                    id: "fixture-\(index)",
                    title: longTitle && index == 1
                        ? "A detailed headline about cities, science and technology that must wrap naturally on narrow screens and remain readable without truncation."
                        : variedGrid && index == 0
                            ? "Short headline" : "Тестовая новость \(index + 1): город и технологии",
                    summary: detailFixture && index == 2
                        ? nil
                        : longArticle
                            ? (1...30).map { "Paragraph \($0). A long article summary for reading position checks." }
                                .joined(separator: "\n\n")
                            : "Fixture description must not appear on a card.",
                    imageURL: Self.imageURL(for: index, sampleURL: imageURL),
                    source: variedGrid && index == 2 ? nil : "Демонстрационный источник",
                    publishedAt: variedGrid && index == 2 ? nil : Date(timeIntervalSince1970: 1_700_000_000),
                    articleURL: detailFixture && index != 2 ? "https://example.com/news/\(index)" : nil)
            }
        }

        func observe() -> AsyncStream<FeedRead> {
            AsyncStream { continuation in
                self.continuation = continuation
                continuation.yield(.snapshot(snapshot))
            }
        }

        func update(_ operation: FeedOperation) async throws -> FeedUpdate {
            attempts += 1

            if arguments.contains("--feed-ui-loading") { try await Task.sleep(for: .seconds(8)) }

            let shouldFailFirstUpdate =
                arguments.contains("--feed-ui-error") || arguments.contains("--feed-ui-cached-error")

            if shouldFailFirstUpdate && attempts == 1 {
                return .failure(isStorage: false)
            }

            didCompleteLoad = true
            continuation?.yield(.snapshot(snapshot))

            return .success
        }

        private static func imageURL(for index: Int, sampleURL: URL?) -> URL? {
            switch index % 3 {
            case 0: return sampleURL
            case 1: return URL(fileURLWithPath: "/missing-news-fixture.png")
            default: return nil
            }
        }
    }
#endif
