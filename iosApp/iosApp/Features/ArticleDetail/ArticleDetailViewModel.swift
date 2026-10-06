import Foundation
import Observation
import SwiftUI

enum ArticleDetailAction: Equatable {
    case source(URL)
    case share(URL)
}

/// Owns an immutable selected snapshot, reading position and one auxiliary presentation.
@MainActor @Observable
final class ArticleDetailViewModel {
    let article: FeedArticle
    var readingPosition = ScrollPosition(edge: .top)
    private(set) var presentedAction: ArticleDetailAction?

    var summary: String? {
        guard let summary = article.summary?.trimmingCharacters(in: .whitespacesAndNewlines), !summary.isEmpty else {
            return nil
        }
        return summary
    }

    init(article: FeedArticle) { self.article = article }

    func openSource() {
        guard presentedAction == nil, let url = article.openingURL else { return }
        presentedAction = .source(url)
    }

    func share() {
        guard presentedAction == nil, let url = article.openingURL else { return }
        presentedAction = .share(url)
    }

    func dismissAction() { presentedAction = nil }
}
