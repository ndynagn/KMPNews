#if DEBUG
    import SwiftUI

    /// Deterministic navigation-only harness; no repository or search ViewModel participates.
    struct SearchNavigationPrototype: View {
        @State private var selected: HomeSection = .news
        @State private var previous: HomeSection = .news
        @State private var query = ""
        private var usesSearchRole: Bool {
            if #available(iOS 26, *) {
                return !ProcessInfo.processInfo.arguments.contains("--search-legacy-navigation")
            }
            return false
        }

        var body: some View {
            HomeTabs(selection: selection, usesSearchRole: usesSearchRole) {
                NavigationStack { Text("home.news").accessibilityIdentifier("prototype.news") }
            } favorites: {
                NavigationStack { Text("home.favorites").accessibilityIdentifier("prototype.favorites") }
            } profile: {
                NavigationStack { Text("home.profile").accessibilityIdentifier("prototype.profile") }
            } search: {
                SearchNavigationDestination(
                    query: $query, isSelected: selected == .search, usesSearchRole: usesSearchRole,
                    onClose: { selected = previous }, onSubmit: {}
                ) {
                    ScrollView {
                        LazyVStack(spacing: 16) {
                            Text("Query: [\(query)]").accessibilityIdentifier("prototype.query")
                            ForEach(0..<12) { index in
                                FeedCard(
                                    article: FeedArticle(
                                        id: "prototype-\(index)", title: "Search news \(index)", summary: nil,
                                        imageURL: nil, source: "Preview", publishedAt: nil),
                                    isSaved: false, canSave: true, onSave: {})
                            }
                        }
                        .padding(16)
                    }
                    .background(Color(uiColor: .systemGroupedBackground))
                }
            }
        }

        private var selection: Binding<HomeSection> {
            Binding(
                get: { selected },
                set: { value in
                    if value == .search, selected != .search { previous = selected }
                    selected = value
                })
        }
    }
#endif
