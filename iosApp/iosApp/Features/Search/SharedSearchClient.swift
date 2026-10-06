import SharedLogic

@MainActor
final class SharedSearchClient: SearchClient {
    private let repository: any SearchRepository

    init(repository: any SearchRepository) { self.repository = repository }

    func validate(_ query: String) -> SearchInput {
        if SearchQuery.shared.isBlank(input: query) { return .empty }

        guard let normalized = SearchQuery.shared.normalize(input: query) else { return .invalid }

        return .valid(normalized)
    }

    func fetch(query: String, cursor: String?) async throws -> SearchPageResult {
        let result = try await repository.search(query: query, cursor: cursor)

        if let page = result as? SearchResultPage {
            return .page(page.articles.map(FeedArticle.init), nextCursor: page.nextCursor)
        }

        guard let failed = result as? SearchResultFailed else { return .failed(.invalidResponse) }

        let problem: SearchProblem

        switch failed.failure {
        case .notConfigured: problem = .notConfigured
        case .invalidQuery: problem = .invalidQuery
        case .network: problem = .network
        case .timeout: problem = .timeout
        case .accessDenied: problem = .accessDenied
        case .rateLimited: problem = .rateLimited
        case .quotaExceeded: problem = .quotaExceeded
        case .service: problem = .service
        default: problem = .invalidResponse
        }

        return .failed(problem)
    }
}
