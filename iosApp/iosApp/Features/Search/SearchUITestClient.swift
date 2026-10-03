#if DEBUG
    import Foundation
    import SharedLogic

    @MainActor
    final class SearchUITestClient: SearchClient {
        private let imageURL = ArticleUITestImage.make()
        private var failedAppend = false
        func validate(_ query: String) -> SearchInput {
            if SearchQuery.shared.isBlank(input: query) { return .empty }
            guard let value = SearchQuery.shared.normalize(input: query) else { return .invalid }
            return .valid(value)
        }

        func fetch(query: String, cursor: String?) async throws -> SearchPageResult {
            try await Task.sleep(for: query == "slow" ? .seconds(10) : .milliseconds(100))
            if query == "error" { return .failed(.rateLimited) }
            if query == "empty" { return .page([], nextCursor: nil) }
            if query == "append", cursor != nil, !failedAppend {
                failedAppend = true
                return .failed(.network)
            }
            let offset = Int(cursor ?? "0") ?? 0
            return .page(
                (offset..<(offset + 10)).map { index in
                    FeedArticle(
                        id: "search-\(index)", title: "\(query) article \(index + 1)",
                        summary: "Search fixture for cards and pagination checks.",
                        imageURL: index % 3 == 0 ? imageURL : nil, source: "Fixture News",
                        publishedAt: Date(timeIntervalSince1970: 1_700_000_000),
                        articleURL: index == 0 ? nil : "https://example.com/news/\(index)")
                }, nextCursor: offset < 20 ? String(offset + 10) : nil)
        }
    }
#endif
