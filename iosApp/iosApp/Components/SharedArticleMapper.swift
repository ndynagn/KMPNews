import Foundation
import SharedLogic

extension FeedArticle {
    init(_ article: Article) {
        self.init(
            id: article.id, title: article.title, summary: article.summary,
            imageURL: article.imageUrl.flatMap(URL.init(string:)), source: article.sourceName,
            publishedAt: article.publishedAtEpochMilliseconds.map {
                Date(timeIntervalSince1970: Double($0.int64Value) / 1_000)
            }, articleURL: article.url, sourceID: article.sourceId)
    }
}
