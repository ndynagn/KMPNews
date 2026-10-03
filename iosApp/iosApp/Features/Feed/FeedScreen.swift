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
        Group {
            if viewModel.state.snapshot?.articles.isEmpty != false {
                AppScreenState { status }
            } else {
                articleFeed
            }
        }
        .background(Color(uiColor: .systemGroupedBackground))
        .refreshable { await viewModel.refresh() }
        .onChange(of: viewModel.state.canAppend) { _, _ in loadMoreIfNeeded() }
        .onChange(of: viewModel.state.snapshot?.articles.count) { _, _ in loadMoreIfNeeded() }
    }

    private var articleFeed: some View {
        ArticleFeed(
            articles: viewModel.state.snapshot?.articles ?? [], scrollID: $scrollID,
            savedIDs: savedIDs, canSave: savingID == nil && !favoritesBusy, onSave: onSave,
            onVisibleIDsChange: { ids in
                visibleIDs = Set(ids)
                loadMoreIfNeeded()
            }
        ) {
            status
        }
        .accessibilityIdentifier("feed.list")
    }

    @ViewBuilder private var status: some View {
        let state = viewModel.state

        if state.snapshot?.articles.isEmpty != false,
            !state.status.isConfigured || state.status.storageFailed || state.status.failedOperation != nil
        {
            AppErrorView(
                title: "feed.unavailableTitle",
                message: !state.status.isConfigured
                    ? "feed.missingKey" : state.status.storageFailed ? "feed.storageError" : nil
            ) {
                if state.status.storageFailed || state.status.failedOperation != nil {
                    AppActionButton(
                        title: "feed.retry", emphasis: .text, isBusy: state.status.operation != nil
                    ) { viewModel.onEvent(.retry) }
                    .accessibilityIdentifier("feed.retryButton")
                }
            }
        } else {
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
