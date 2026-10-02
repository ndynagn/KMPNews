import SwiftUI

struct FeedScreen: View {
    let viewModel: FeedViewModel
    @Binding var scrollID: String?
    var savedIDs: Set<String> = []
    var savingID: String?
    var favoritesBusy = false
    var onSave: ((FeedArticle) -> Void)?
    @State private var visibleIDs: Set<String> = []

    var body: some View {
        ScrollView {
            LazyVStack(spacing: 16) {
                ForEach(viewModel.state.snapshot?.articles ?? []) { article in
                    FeedCard(
                        article: article, isSaved: savedIDs.contains(article.id),
                        canSave: savingID == nil && !favoritesBusy, onSave: onSave.map { action in { action(article) } }
                    )
                    .id(article.id)
                    .onAppear {
                        visibleIDs.insert(article.id)
                        loadMoreIfNeeded()
                    }
                    .onDisappear { visibleIDs.remove(article.id) }
                }
                status
            }
            .scrollTargetLayout()
            .padding(16)
        }
        .background(Color(uiColor: .systemGroupedBackground))
        .scrollPosition(id: $scrollID)
        .refreshable { await viewModel.refresh() }
        .onChange(of: viewModel.state.canAppend) { _, _ in loadMoreIfNeeded() }
        .onChange(of: viewModel.state.snapshot?.articles.count) { _, _ in loadMoreIfNeeded() }
        .accessibilityIdentifier("feed.list")
    }

    @ViewBuilder private var status: some View {
        let state = viewModel.state

        VStack(spacing: 12) {
            if state.status.operation != nil || isInitiallyLoading {
                ProgressView("feed.loading")
            }
            if !state.status.isConfigured {
                AppStatusView(
                    title: "feed.unavailableTitle", systemImage: "network.slash",
                    message: "feed.missingKey", isCompact: state.snapshot != nil)
            }
            if state.status.storageFailed || state.status.failedOperation != nil {
                AppStatusView(
                    title: "feed.unavailableTitle", systemImage: "exclamationmark.circle",
                    message: state.status.storageFailed ? "feed.storageError" : nil,
                    isCompact: state.snapshot != nil)
                AppActionButton(title: "feed.retry", isEnabled: state.status.operation == nil) {
                    viewModel.onEvent(.retry)
                }
                .accessibilityIdentifier("feed.retryButton")
            } else if state.status.operation == nil {
                if case .empty = state {
                    AppStatusView(title: "feed.empty", systemImage: "newspaper")
                } else if state.snapshot?.isCacheLimitReached == true {
                    Text("feed.limit").font(.footnote).foregroundStyle(.secondary)
                } else if state.snapshot?.hasMore == false {
                    Text("feed.end").font(.footnote).foregroundStyle(.secondary)
                }
            }
        }
        .frame(maxWidth: .infinity)
    }

    private var isInitiallyLoading: Bool {
        switch viewModel.state {
        case .initial, .loading: return true
        default: return false
        }
    }

    private func loadMoreIfNeeded() {
        let lastIDs = (viewModel.state.snapshot?.articles ?? []).suffix(3).map(\.id)

        if !visibleIDs.isDisjoint(with: lastIDs) { viewModel.onEvent(.loadMore) }
    }
}
