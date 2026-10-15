import Observation

struct ArticleRoute: Hashable {
    let model: ArticleDetailViewModel

    static func == (lhs: Self, rhs: Self) -> Bool { lhs.model === rhs.model }
    func hash(into hasher: inout Hasher) { hasher.combine(ObjectIdentifier(model)) }
}

/// Each section keeps one reading destination while other sections are selected.
@MainActor @Observable
final class ArticleNavigationState {
    private(set) var destinations: [HomeSection: ArticleDetailViewModel] = [:]
    private var account: String?

    var articleIDs: [String] { destinations.values.map { $0.article.id }.sorted() }

    func open(_ article: FeedArticle, in section: HomeSection) {
        guard section != .profile, destinations[section] == nil else { return }
        destinations[section] = ArticleDetailViewModel(article: article)
    }

    func close(_ section: HomeSection) { destinations[section] = nil }

    func accountChanged(_ profile: ProfileState) {
        let nextAccount: String?
        switch profile {
        case .authenticated(let email): nextAccount = email
        case .guest: nextAccount = nil
        case .restoring, .unavailable: return
        }
        if account != nil, account != nextAccount { close(.favorites) }
        account = nextAccount
    }
}
