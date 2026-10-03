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
        Group {
            if case .guest = profile {
                guestContent
            } else if showsScreenState {
                AppScreenState { screenState }
            } else {
                articleList
            }
        }
        .background(Color(uiColor: .systemGroupedBackground))
        .refreshable {
            if actions.savingID == nil { await viewModel.refresh() }
        }
        .task(id: profile) {
            viewModel.accountChanged(profile)
            if actions.savingID == nil { await viewModel.refresh() }
        }
        .onChange(of: viewModel.isLoading) { _, loading in if !loading { loadMoreIfNeeded() } }
        .onChange(of: actions.savingID) { _, id in if id == nil { loadMoreIfNeeded() } }
        .onChange(of: viewModel.snapshot?.articles.map(\.id)) { _, _ in loadMoreIfNeeded() }
    }

    private var guestContent: some View {
        GeometryReader { geometry in
            ScrollView {
                VStack(spacing: 16) {
                    AppStatusView(
                        title: "favorites.guestTitle", systemImage: "star", message: "favorites.invitation.message"
                    )
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .padding(.vertical, 32)
                    AppActionButton(title: "auth.login", action: onLogin)
                        .accessibilityIdentifier("favorites.guest.login")
                    RegistrationPrompt(action: onRegister)
                        .accessibilityIdentifier("favorites.guest.register")
                }
                .padding(24)
                .frame(minHeight: geometry.size.height)
            }
            .accessibilityIdentifier("favorites.guest")
        }
    }

    private var articleList: some View {
        ScrollView {
            LazyVStack(spacing: 16) {
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
            .scrollTargetLayout()
            .padding(16)
        }
        .background(Color(uiColor: .systemGroupedBackground))
        .scrollPosition(id: $scrollID)
        .scrollBounceBehavior(.always)
        .accessibilityIdentifier("favorites.list")
    }

    private var showsScreenState: Bool {
        if case .authenticated = profile { return viewModel.articles.isEmpty }
        return true
    }

    @ViewBuilder private var screenState: some View {
        if showsFullScreenError {
            AppErrorView(title: "favorites.loadError") {
                if case .authenticated = profile {
                    AppActionButton(title: "feed.retry", emphasis: .text, isEnabled: actions.savingID == nil) {
                        viewModel.retry()
                    }
                    .accessibilityIdentifier("favorites.retry")
                }
            }
        } else if showsEmptyState {
            AppStatusView(title: "favorites.empty", systemImage: "star", message: "favorites.emptyMessage")
        } else {
            ProgressView("feed.loading")
        }
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
