#if DEBUG
    import Foundation
    import UIKit

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
            let imageURL = Self.makeImage()

            articles = (0..<24).map { index in
                FeedArticle(
                    id: "fixture-\(index)", title: "Тестовая новость \(index + 1): город и технологии",
                    summary: "Fixture description must not appear on a card.",
                    imageURL: Self.imageURL(for: index, sampleURL: imageURL),
                    source: "Демонстрационный источник", publishedAt: Date(timeIntervalSince1970: 1_700_000_000))
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

        private static func makeImage() -> URL? {
            let renderer = UIGraphicsImageRenderer(size: CGSize(width: 600, height: 320))
            let image = renderer.image { context in
                UIColor.systemTeal.setFill()
                context.fill(CGRect(x: 0, y: 0, width: 600, height: 320))
                UIImage(systemName: "newspaper")?.withTintColor(.white, renderingMode: .alwaysOriginal)
                    .draw(in: CGRect(x: 235, y: 95, width: 130, height: 130))
            }
            let url = FileManager.default.temporaryDirectory.appendingPathComponent("feed-ui-fixture.png")

            guard let data = image.pngData() else { return nil }

            do {
                try data.write(to: url, options: .atomic)

                return url
            } catch {
                return nil
            }
        }
    }
#endif
