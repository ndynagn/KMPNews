import NukeUI
import SwiftUI

struct ArticleDetailScreen: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Bindable var viewModel: ArticleDetailViewModel
    let favoriteActions: FavoriteSaveViewModel?
    let favorites: FavoritesViewModel?
    let canPresentActions: () -> Bool
    let onSave: () -> Void
    @State private var actionAnchor = ArticleActionAnchor.Reference()

    var body: some View {
        GeometryReader { geometry in
            ScrollView {
                VStack(spacing: 24) {
                    if let url = viewModel.article.imageURL {
                        articleImage(url: url, width: geometry.size.width)
                    }
                    articleText
                        .frame(maxWidth: 640, alignment: .leading)
                        .padding(.horizontal, 24)
                        .padding(.top, viewModel.article.imageURL == nil ? 24 : 0)
                        .padding(.bottom, 32)
                        .frame(maxWidth: .infinity)
                }
                .frame(width: geometry.size.width)
            }
            .coordinateSpace(name: "article.reader")
            .scrollPosition($viewModel.readingPosition)
            .accessibilityIdentifier("article.detail")
        }
        .ignoresSafeArea(.container, edges: viewModel.article.imageURL == nil ? [] : .top)
        .background(Color(uiColor: .systemBackground))
        .background {
            ArticleActionPresenter(
                action: viewModel.presentedAction, anchor: actionAnchor, onDismiss: viewModel.dismissAction
            )
            .frame(width: 0, height: 0)
        }
        .navigationTitle("")
        .navigationBarTitleDisplayMode(.inline)
        .modifier(ArticleSearchToolbar())
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button(action: onSave) {
                    Image(systemName: isSaved ? "star.fill" : "star")
                        .foregroundStyle(.tint)
                }
                .tint(.accentColor)
                .accessibilityLabel(isSaved ? Text("favorites.remove") : Text("favorites.save"))
                .accessibilityValue(isSaved ? Text("favorites.saved") : Text("favorites.notSaved"))
                .accessibilityIdentifier("article.save")
                .disabled(
                    !canPresentActions() || favoriteActions == nil || favorites?.isLoading == true
                        || viewModel.presentedAction != nil)
            }
            if viewModel.article.openingURL != nil {
                ToolbarItem(placement: .topBarTrailing) {
                    Menu {
                        Button("article.openSource", systemImage: "safari", action: viewModel.openSource)
                            .accessibilityIdentifier("article.openSource")
                        Button("article.share", systemImage: "square.and.arrow.up", action: viewModel.share)
                            .accessibilityIdentifier("article.share")
                    } label: {
                        Image(systemName: "ellipsis")
                            .foregroundStyle(.primary)
                    }
                    .accessibilityLabel(Text("article.actions"))
                    .accessibilityIdentifier("article.actions")
                    .disabled(!canPresentActions())
                    .background {
                        ArticleActionAnchor(reference: actionAnchor)
                            .allowsHitTesting(false)
                            .accessibilityHidden(true)
                    }
                }
            }
        }
    }

    private var isSaved: Bool {
        favoriteActions?.isSaved(
            viewModel.article.id,
            fallback: favorites?.snapshot?.articles.contains { $0.id == viewModel.article.id } == true) ?? false
    }

    private func articleImage(url: URL, width: CGFloat) -> some View {
        let height = min(320, width * 9 / 16)
        return GeometryReader { geometry in
            let pull = reduceMotion ? 0 : max(0, geometry.frame(in: .named("article.reader")).minY)
            LazyImage(url: url) { state in
                if let image = state.image {
                    image.resizable().scaledToFill()
                } else {
                    AppImagePlaceholder(state: state.error == nil ? .loading : .unavailable)
                }
            }
            .frame(width: width, height: height + pull)
            .clipped()
            .offset(y: -pull)
        }
        .frame(height: height)
        .accessibilityHidden(true)
    }

    private var articleText: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text(viewModel.article.title ?? String(localized: "feed.noTitle"))
                .font(.title.bold())
                .accessibilityAddTraits(.isHeader)
                .accessibilityIdentifier("article.title")
            if !metadata.isEmpty { Text(metadata).font(.subheadline).foregroundStyle(.secondary) }
            if let summary = viewModel.summary {
                Text(summary).textSelection(.enabled).accessibilityIdentifier("article.summary")
            } else {
                Text("article.noSummary").foregroundStyle(.secondary).accessibilityIdentifier("article.noSummary")
            }
        }
    }

    private var metadata: String {
        [viewModel.article.source, viewModel.article.publishedAt?.formatted(date: .long, time: .shortened)]
            .compactMap { $0 }.joined(separator: " · ")
    }
}

private struct ArticleSearchToolbar: ViewModifier {
    @ViewBuilder func body(content: Content) -> some View {
        if #available(iOS 26, *) { content.toolbar(removing: .search) } else { content }
    }
}
