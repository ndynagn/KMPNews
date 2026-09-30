import NukeUI
import SwiftUI

struct FeedScreen: View {
    let viewModel: FeedViewModel
    @Binding var expanded: Set<String>
    @Binding var scrollID: String?
    @State private var visibleIDs: Set<String> = []

    var body: some View {
        ScrollView {
            LazyVStack(spacing: 16) {
                ForEach(viewModel.state.snapshot?.articles ?? []) { article in
                    FeedCard(article: article, isExpanded: expanded.contains(article.id)) {
                        if expanded.contains(article.id) {
                            expanded.remove(article.id)
                        } else {
                            expanded.insert(article.id)
                        }
                    }
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
            if !state.status.isConfigured { Text("feed.missingKey") }
            if state.status.storageFailed || state.status.failedOperation != nil {
                Text(state.status.storageFailed ? "feed.storageError" : "feed.updateError")
                Button("feed.retry") { viewModel.onEvent(.retry) }.accessibilityIdentifier("feed.retryButton")
            } else if state.status.operation == nil {
                if case .empty = state {
                    Text("feed.empty")
                } else if state.snapshot?.isCacheLimitReached == true {
                    Text("feed.limit")
                } else if state.snapshot?.hasMore == false {
                    Text("feed.end")
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
    let isExpanded: Bool
    let onToggle: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            if let imageURL = article.imageURL {
                LazyImage(url: imageURL) { state in
                    if let image = state.image {
                        image.resizable().scaledToFill()
                    } else {
                        ZStack {
                            Color(uiColor: .tertiarySystemFill)
                            if state.error != nil { Text("feed.imageError").font(.caption) }
                        }
                    }
                }
                .frame(height: 150).clipped().accessibilityHidden(true)
            }
            VStack(alignment: .leading, spacing: 16) {
                Button(action: onToggle) {
                    Text(article.title ?? String(localized: "feed.noTitle"))
                        .font(.headline).multilineTextAlignment(.leading)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                .buttonStyle(.plain)
                .accessibilityHint(isExpanded ? "feed.collapse" : "feed.expand")
                if isExpanded { Text(article.summary ?? String(localized: "feed.noSummary")) }
                if article.source != nil || article.publishedAt != nil {
                    Text(metadata).font(.caption).foregroundStyle(.secondary)
                }
            }
            .padding(16)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(uiColor: .secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 12))
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .accessibilityIdentifier("feed.article.\(article.id)")
    }

    private var metadata: String {
        [article.source, article.publishedAt?.formatted(date: .numeric, time: .shortened)]
            .compactMap { $0 }.joined(separator: " · ")
    }
}
