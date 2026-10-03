import Foundation

enum SearchProblem: String, Equatable {
    case notConfigured, invalidQuery, network, timeout, accessDenied, rateLimited, quotaExceeded, service,
        invalidResponse
    var localizationKey: String { "search.error.\(rawValue)" }
    var canRetry: Bool {
        switch self {
        case .notConfigured, .invalidQuery, .accessDenied, .quotaExceeded: return false
        case .network, .timeout, .rateLimited, .service, .invalidResponse: return true
        }
    }
}

enum SearchPhase: Equatable { case idle, loading, loaded, failed(SearchProblem) }

struct SearchState {
    var query = ""
    var articles: [FeedArticle] = []
    var phase: SearchPhase = .idle
    var nextCursor: String?
    var isAppending = false
    var appendFailure: SearchProblem?
    var isWaiting = false

    var canAppend: Bool {
        phase == .loaded && !isWaiting && nextCursor != nil && !isAppending && appendFailure == nil
    }
}

enum SearchInput { case empty, invalid, valid(String) }
enum SearchPageResult { case page([FeedArticle], nextCursor: String?), failed(SearchProblem) }
