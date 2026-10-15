import Foundation

/// Native article presentation shared by feed, search and favorites.
struct FeedArticle: Identifiable, Equatable {
    let id: String
    let title: String?
    let summary: String?
    let imageURL: URL?
    let source: String?
    let publishedAt: Date?
    var articleURL: String? = nil
    var sourceID: String? = nil

    var openingURL: URL? {
        guard let articleURL, let url = URL(string: articleURL),
            let scheme = url.scheme?.lowercased(), ["http", "https"].contains(scheme),
            let host = url.host, !host.isEmpty
        else { return nil }
        return url
    }
}
