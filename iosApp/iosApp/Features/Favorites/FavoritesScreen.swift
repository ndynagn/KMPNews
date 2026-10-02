import SwiftUI

struct FavoritesScreen: View {
    let viewModel: FavoritesViewModel
    let actions: FavoriteSaveViewModel
    let profile: ProfileState
    let onLogin: () -> Void
    let onRegister: () -> Void
    @State private var scrollID: String?
    @State private var visibleIDs: Set<String> = []

    var body: some View {
        ScrollView {
            LazyVStack(spacing: 16) {
                if case .guest = profile {
                    AppStatusView(
                        title: "favorites.guestTitle", systemImage: "star", message: "favorites.invitation.message")
                    AppActionButton(title: "auth.login", action: onLogin)
                    AppActionButton(title: "auth.registration", emphasis: .secondary, action: onRegister)
                } else if case .restoring = profile {
                    ProgressView("feed.loading")
                } else if case .unavailable = profile {
                    EmptyView()
                } else {
                    ForEach(viewModel.articles) { article in
                        FeedCard(
                            article: article, isSaved: isSaved(article),
                            canSave: actions.savingID == nil && !viewModel.isLoading,
                            onSave: { actions.save(article, profile: profile, isSaved: isSaved(article)) }
                        )
                        .id(article.id)
                        .onAppear {
                            visibleIDs.insert(article.id)
                            loadMoreIfNeeded()
                        }
                        .onDisappear { visibleIDs.remove(article.id) }
                    }
                    if viewModel.isLoading { ProgressView("feed.loading") }
                    if viewModel.hasError, !showsFullScreenError {
                        AppStatusView(
                            title: "favorites.loadError", systemImage: "exclamationmark.circle", isCompact: true)
                        AppActionButton(title: "feed.retry", isEnabled: actions.savingID == nil) { viewModel.retry() }
                            .accessibilityIdentifier("favorites.retry")
                    }
                }
            }
            .scrollTargetLayout()
            .padding(16)
        }
        .background(Color(uiColor: .systemGroupedBackground))
        .overlay {
            if showsFullScreenError {
                VStack(spacing: 16) {
                    AppStatusView(title: "favorites.loadError", systemImage: "exclamationmark.circle")
                        .fixedSize(horizontal: false, vertical: true)
                    if case .authenticated = profile {
                        AppActionButton(title: "feed.retry", isEnabled: actions.savingID == nil) { viewModel.retry() }
                            .accessibilityIdentifier("favorites.retry")
                    }
                }
                .padding(16)
            } else if showsEmptyState {
                AppStatusView(title: "favorites.empty", systemImage: "star", message: "favorites.emptyMessage")
                    .allowsHitTesting(false)
            }
        }
        .scrollPosition(id: $scrollID)
        .scrollBounceBehavior(.always)
        .refreshable {
            if actions.savingID == nil { await viewModel.refresh() }
        }
        .task(id: profile) {
            viewModel.accountChanged(profile)
            if actions.savingID == nil { await viewModel.refresh() }
        }
        .accessibilityIdentifier("favorites.list")
        .onChange(of: viewModel.isLoading) { _, loading in if !loading { loadMoreIfNeeded() } }
        .onChange(of: actions.savingID) { _, id in if id == nil { loadMoreIfNeeded() } }
        .onChange(of: viewModel.snapshot?.articles.map(\.id)) { _, _ in loadMoreIfNeeded() }
    }

    private func isSaved(_ article: FeedArticle) -> Bool {
        actions.isSaved(article.id, fallback: viewModel.snapshot?.articles.contains { $0.id == article.id } == true)
    }

    private var showsEmptyState: Bool {
        guard case .authenticated = profile else { return false }

        return viewModel.snapshot?.isInitialized == true && viewModel.articles.isEmpty
            && !viewModel.isLoading && !viewModel.hasError
    }

    private var showsFullScreenError: Bool {
        if case .unavailable = profile { return true }
        guard case .authenticated = profile else { return false }
        return viewModel.hasError && viewModel.articles.isEmpty
    }

    private func loadMoreIfNeeded() {
        guard actions.savingID == nil, !viewModel.isLoading,
            let last = viewModel.snapshot?.articles.last?.id, visibleIDs.contains(last)
        else { return }
        viewModel.loadMore()
    }
}
