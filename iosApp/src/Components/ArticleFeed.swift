import SwiftUI

/// Shared adaptive collection for News, Search and Favorites, with stable article scroll targets.
struct ArticleFeed<Footer: View>: View {
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @ScaledMetric(relativeTo: .body) private var minimumCardWidth: CGFloat = 320
    let articles: [FeedArticle]
    @Binding var scrollID: String?
    var savedIDs: Set<String> = []
    var canSave = true
    var onSave: ((FeedArticle) -> Void)?
    var onOpen: ((FeedArticle) -> Void)?
    var onVisibleIDsChange: ([String]) -> Void = { _ in }
    @ViewBuilder var footer: () -> Footer

    var body: some View {
        GeometryReader { geometry in
            ScrollView {
                VStack(spacing: 16) {
                    LazyVGrid(columns: columns(width: geometry.size.width), alignment: .leading, spacing: 16) {
                        ForEach(articles) { article in
                            FeedCard(
                                article: article, isSaved: savedIDs.contains(article.id), canSave: canSave,
                                onSave: onSave.map { action in { action(article) } },
                                onOpen: onOpen.map { action in { action(article) } }
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
            .scrollBounceBehavior(.always)
        }
    }

    private func columns(width: CGFloat) -> [GridItem] {
        let count = ArticleGridLayout.columnCount(
            width: width, minimumCardWidth: minimumCardWidth, accessibilitySize: dynamicTypeSize.isAccessibilitySize)
        return Array(repeating: GridItem(.flexible(), spacing: 16, alignment: .top), count: count)
    }
}
