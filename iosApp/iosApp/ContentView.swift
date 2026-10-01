import SharedLogic
import SwiftUI

struct ContentView: View {
    @Environment(\.scenePhase) private var scenePhase
    @State private var selected = 0
    let homeViewModel: HomeViewModel
    private let authClient: any AuthClient
    @State private var profileViewModel: ProfileViewModel
    @State private var authStep: AuthStep?
    @State private var scrollID: String?
    #if DEBUG
        @State private var showsComponentCatalog = false
    #endif

    init(homeViewModel: HomeViewModel) {
        self.homeViewModel = homeViewModel
        let client: any AuthClient
        #if DEBUG
            if ProcessInfo.processInfo.arguments.contains("--auth-ui-fixture") {
                client = AuthUITestClient()
            } else {
                client = SharedAuthClient(authRepository: homeViewModel.authDependencies.authRepository)
            }
        #else
            client = SharedAuthClient(authRepository: homeViewModel.authDependencies.authRepository)
        #endif
        authClient = client
        _profileViewModel = State(initialValue: ProfileViewModel(client: client))
    }

    var body: some View {
        TabView(selection: $selected) {
            NavigationStack {
                Group {
                    if let feedViewModel = homeViewModel.feedViewModel {
                        FeedScreen(viewModel: feedViewModel, scrollID: $scrollID)
                    } else if homeViewModel.storageFailed {
                        VStack {
                            Text("feed.storageError")
                            AppButton(title: "feed.retry") {
                                homeViewModel.prepare()
                                updateActivity()
                            }
                        }
                    } else {
                        ProgressView()
                    }
                }
                .navigationTitle("home.news")
                .navigationBarTitleDisplayMode(.large)
            }
            .tabItem { Label("home.news", systemImage: "newspaper") }.tag(0)
            NavigationStack {
                Text("home.placeholder").navigationTitle("home.favorites").navigationBarTitleDisplayMode(.large)
            }
            .tabItem { Label("home.favorites", systemImage: "star") }.tag(1)
            NavigationStack {
                ProfileScreen(
                    state: profileViewModel.state, notice: profileViewModel.notice,
                    isBusy: profileViewModel.isBusy,
                    onLogin: { authStep = .signIn }, onRegister: { authStep = .register },
                    onRetry: profileViewModel.restore, onLogout: profileViewModel.signOut
                )
                .navigationTitle("home.profile").navigationBarTitleDisplayMode(.large)
                #if DEBUG
                    .toolbar {
                        ToolbarItem(placement: .topBarTrailing) {
                            Button("kit.title") { showsComponentCatalog = true }
                            .accessibilityIdentifier("kit.entry")
                        }
                    }
                    .sheet(isPresented: $showsComponentCatalog) {
                        ComponentCatalogScreen()
                    }
                #endif
            }
            .tabItem { Label("home.profile", systemImage: "person") }.tag(2)
        }
        .sheet(item: $authStep) { step in
            AuthScreen(client: authClient, step: step) { authStep = nil }
                .presentationSizing(.page)
                .presentationDetents([.large])
        }
        .task { await profileViewModel.observe() }
        .onAppear {
            homeViewModel.prepare()
            profileViewModel.restore()
            updateActivity()
        }
        .onChange(of: selected) { _, _ in updateActivity() }
        .onChange(of: scenePhase) { _, phase in
            updateActivity()
            if phase == .active { profileViewModel.restore() }
        }
        .onDisappear { homeViewModel.feedViewModel?.deactivate() }
    }

    private func updateActivity() {
        if selected == 0 && scenePhase == .active {
            homeViewModel.feedViewModel?.activate()
        } else {
            homeViewModel.feedViewModel?.deactivate()
        }
    }
}
