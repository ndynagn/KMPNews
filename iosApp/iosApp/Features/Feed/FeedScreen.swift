import NukeUI
import SwiftUI

struct FeedScreen: View {
    let viewModel: FeedViewModel
    @Binding var scrollID: String?
    @State private var visibleIDs: Set<String> = []

    var body: some View {
        ScrollView {
            LazyVStack(spacing: 16) {
                ForEach(viewModel.state.snapshot?.articles ?? []) { article in
                    FeedCard(article: article)
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
                    message: "feed.missingKey", compact: state.snapshot != nil)
            }
            if state.status.storageFailed || state.status.failedOperation != nil {
                AppStatusView(
                    title: "feed.unavailableTitle", systemImage: "exclamationmark.circle",
                    message: state.status.storageFailed ? "feed.storageError" : nil,
                    compact: state.snapshot != nil)
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

private struct FeedCard: View {
    let article: FeedArticle

    var body: some View {
        AppNewsPreview(
            title: article.title ?? String(localized: "feed.noTitle"), metadata: metadata,
            showsImage: article.imageURL != nil
        ) {
            if let imageURL = article.imageURL {
                LazyImage(url: imageURL) { state in
                    if let image = state.image {
                        image.resizable().scaledToFill()
                    } else {
                        AppImagePlaceholder(state: state.error == nil ? .loading : .unavailable)
                    }
                }
                .accessibilityHidden(true)
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("feed.article.\(article.id)")
    }

    private var metadata: String {
        [article.source, article.publishedAt?.formatted(date: .numeric, time: .shortened)]
            .compactMap { $0 }.joined(separator: " · ")
    }
}
