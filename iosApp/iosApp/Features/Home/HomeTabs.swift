import SwiftUI

/// Each destination owns its navigation stack; only Search supplies a searchable field.
struct HomeTabs<News: View, Favorites: View, Profile: View, Search: View>: View {
    @Binding var selection: HomeSection
    let usesSearchRole: Bool
    @ViewBuilder var news: () -> News
    @ViewBuilder var favorites: () -> Favorites
    @ViewBuilder var profile: () -> Profile
    @ViewBuilder var search: () -> Search

    var body: some View {
        TabView(selection: $selection) {
            Tab("home.news", systemImage: "newspaper", value: HomeSection.news, content: news)
            Tab("home.favorites", systemImage: "star", value: HomeSection.favorites, content: favorites)
            Tab("home.profile", systemImage: "person", value: HomeSection.profile, content: profile)
            if #available(iOS 26, *), usesSearchRole {
                Tab(
                    "search.title", systemImage: "magnifyingglass", value: HomeSection.search, role: .search,
                    content: search)
            } else {
                Tab("search.title", systemImage: "magnifyingglass", value: HomeSection.search, content: search)
            }
        }
        .tabViewStyle(.sidebarAdaptable)
        .modifier(SearchTabActivation(usesSearchRole: usesSearchRole))
    }
}
