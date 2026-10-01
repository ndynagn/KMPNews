import SharedLogic
import SwiftUI

struct ContentView: View {
    @Environment(\.scenePhase) private var scenePhase
    #if DEBUG
        @State private var showsComponentCatalog = false
    #endif
    @State private var selected = 0
    @State private var feedViewModel: FeedViewModel
    @State private var scrollID: String?

    init(dependencies: FeedDependencies, isConfigured: Bool) {
        _feedViewModel = State(
            initialValue: FeedViewModel(
                client: SharedFeedClient(
                    newsRepository: dependencies.newsRepository,
                    refreshFeedIfNeeded: dependencies.refreshFeedIfNeeded
                ), isConfigured: isConfigured
            ))
    }

    var body: some View {
        TabView(selection: $selected) {
            NavigationStack {
                FeedScreen(viewModel: feedViewModel, scrollID: $scrollID)
                    .navigationTitle("home.news")
                    .navigationBarTitleDisplayMode(.large)
            }
            .tabItem { Label("home.news", systemImage: "newspaper") }.tag(0)
            NavigationStack {
                Text("home.placeholder").navigationTitle("home.favorites").navigationBarTitleDisplayMode(.large)
            }
            .tabItem { Label("home.favorites", systemImage: "star") }.tag(1)
            NavigationStack {
                Text("home.placeholder").navigationTitle("home.profile").navigationBarTitleDisplayMode(.large)
                    #if DEBUG
                        .toolbar {
                            ToolbarItem(placement: .topBarTrailing) {
                                Button("kit.title") { showsComponentCatalog = true }
                                .accessibilityIdentifier("kit.entry")
                            }
                        }
                        .sheet(isPresented: $showsComponentCatalog) { ComponentCatalogScreen() }
                    #endif
            }
            .tabItem { Label("home.profile", systemImage: "person") }.tag(2)
        }
        .onAppear { updateActivity() }
        .onChange(of: selected) { _, _ in updateActivity() }
        .onChange(of: scenePhase) { _, _ in updateActivity() }
        .onDisappear { feedViewModel.deactivate() }
    }

    private func updateActivity() {
        if selected == 0 && scenePhase == .active { feedViewModel.activate() } else { feedViewModel.deactivate() }
    }
}
