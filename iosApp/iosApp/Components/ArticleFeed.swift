import SwiftUI

/// The same card composition, margins and scroll ownership for News and Search.
struct ArticleFeed<Footer: View>: View {
    let articles: [FeedArticle]
    @Binding var scrollID: String?
    var savedIDs: Set<String> = []
    var canSave = true
    var onSave: ((FeedArticle) -> Void)?
    var onOpen: ((FeedArticle) -> Void)?
    var onVisibleIDsChange: ([String]) -> Void = { _ in }
    @ViewBuilder var footer: () -> Footer

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                LazyVStack(spacing: 16) {
                    ForEach(articles) { article in
                        FeedCard(
                            article: article, isSaved: savedIDs.contains(article.id), canSave: canSave,
                            onSave: onSave.map { action in { action(article) } },
                            onOpen: article.openingURL == nil ? nil : onOpen.map { action in { action(article) } }
                        )
                        .id(article.id)
                    }
                }
                .scrollTargetLayout()
                footer()
            }
            .padding(16)
        }
        .background(Color(uiColor: .systemGroupedBackground))
        .scrollPosition(id: $scrollID, anchor: .top)
        .onScrollTargetVisibilityChange(idType: String.self, threshold: 0.1, onVisibleIDsChange)
    }
}
