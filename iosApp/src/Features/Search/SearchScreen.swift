import SwiftUI

struct SearchScreen: View {
    let viewModel: SearchViewModel
    @Binding var scrollID: String?
    let savedIDs: Set<String>
    let canSave: Bool
    let onSave: (FeedArticle) -> Void
    let onOpen: (FeedArticle) -> Void
    @State private var visibleIDs: Set<String> = []

    var body: some View {
        Group {
            if viewModel.state.articles.isEmpty {
                AppScreenState { status }
            } else {
                articleFeed
            }
        }
        .background(Color(uiColor: .systemGroupedBackground))
        .scrollDismissesKeyboard(.interactively)
        .errorFeedback(viewModel.errorFeedback)
        .onChange(of: viewModel.state.canAppend) { _, _ in loadMoreIfNeeded() }
        .onChange(of: viewModel.state.articles.map(\.id)) { _, ids in
            visibleIDs.formIntersection(ids)
            if ids.isEmpty { scrollID = nil }
            loadMoreIfNeeded()
        }
    }

    private var articleFeed: some View {
        ArticleFeed(
            articles: viewModel.state.articles, scrollID: $scrollID,
            savedIDs: savedIDs, canSave: canSave, onSave: onSave,
            onOpen: onOpen,
            onVisibleIDsChange: { ids in
                visibleIDs = Set(ids)
                loadMoreIfNeeded()
            }
        ) {
            status
        }
        .accessibilityIdentifier("search.results")
    }

    @ViewBuilder private var status: some View {
        VStack(spacing: 12) {
            switch viewModel.state.phase {
            case .idle:
                AppStatusView(title: "search.prompt", systemImage: "magnifyingglass", message: "search.start")
            case .loading:
                ProgressView("feed.loading")
            case .loaded:
                if viewModel.state.articles.isEmpty {
                    AppStatusView(title: "search.empty", systemImage: "magnifyingglass", message: "search.emptyHint")
                }
            case .failed(let problem):
                failure(problem, compact: false)
            }
            if viewModel.state.isAppending, viewModel.state.appendFailure == nil { ProgressView("feed.loading") }
            if let problem = viewModel.state.appendFailure { failure(problem, compact: true) }
        }
        .frame(maxWidth: .infinity)
    }

    @ViewBuilder private func failure(_ problem: SearchProblem, compact: Bool) -> some View {
        if compact {
            AppStatusView(
                title: "search.unavailable", systemImage: "exclamationmark.circle",
                message: LocalizedStringKey(problem.localizationKey), isCompact: true)
            retry(problem)
        } else {
            AppErrorView(title: "search.unavailable", message: LocalizedStringKey(problem.localizationKey)) {
                retry(problem)
            }
        }
    }

    @ViewBuilder private func retry(_ problem: SearchProblem) -> some View {
        if problem.canRetry {
            AppActionButton(
                title: "feed.retry", emphasis: .text, isBusy: viewModel.state.isAppending,
                isEnabled: !viewModel.state.isWaiting
            ) {
                viewModel.onEvent(.retry)
            }
            .accessibilityIdentifier("search.retry")
        }
    }

    private func loadMoreIfNeeded() {
        if !visibleIDs.isDisjoint(with: viewModel.state.articles.suffix(3).map(\.id)) {
            viewModel.onEvent(.loadMore)
        }
    }
}
