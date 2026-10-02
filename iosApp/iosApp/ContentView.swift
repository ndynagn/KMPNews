import SharedLogic
import SwiftUI

struct ContentView: View {
    @Environment(\.scenePhase) private var scenePhase
    @State private var selected = 0
    let homeViewModel: HomeViewModel
    private let authClient: any AuthClient
    @State private var profileViewModel: ProfileViewModel
    @State private var authViewModel: AuthViewModel?
    @State private var favoriteAuthViewModel: AuthViewModel?
    @State private var dismissingAuthViewModel: AuthViewModel?
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
                        FeedScreen(
                            viewModel: feedViewModel, scrollID: $scrollID,
                            savedIDs: homeViewModel.favoriteSaveViewModel?.savedIDs ?? [],
                            savingID: homeViewModel.favoriteSaveViewModel?.savingID,
                            favoritesBusy: homeViewModel.favoritesViewModel?.isLoading == true,
                            onSave: { article in
                                if let actions = homeViewModel.favoriteSaveViewModel {
                                    actions.save(
                                        article, profile: profileViewModel.state,
                                        isSaved: actions.savedIDs.contains(article.id))
                                }
                            })
                    } else if homeViewModel.storageFailed {
                        VStack {
                            AppStatusView(
                                title: "feed.unavailableTitle", systemImage: "exclamationmark.circle",
                                message: "feed.storageError")
                            AppActionButton(title: "feed.retry") {
                                homeViewModel.prepare()
                                updateActivity()
                            }
                        }
                        .padding(16)
                    } else {
                        ProgressView("feed.loading")
                    }
                }
                .navigationTitle("home.news")
                .navigationBarTitleDisplayMode(.large)
            }
            .tabItem { Label("home.news", systemImage: "newspaper") }.tag(0)
            NavigationStack {
                Group {
                    if let list = homeViewModel.favoritesViewModel, let actions = homeViewModel.favoriteSaveViewModel {
                        FavoritesScreen(
                            viewModel: list, actions: actions, profile: profileViewModel.state,
                            onLogin: { presentAuth(.signIn) }, onRegister: { presentAuth(.register) })
                    } else {
                        ProgressView("feed.loading")
                    }
                }
                .navigationTitle("home.favorites").navigationBarTitleDisplayMode(.large)
            }
            .tabItem { Label("home.favorites", systemImage: "star") }.tag(1)
            NavigationStack {
                ProfileScreen(
                    state: profileViewModel.state, notice: profileViewModel.notice,
                    isBusy: profileViewModel.isBusy,
                    onLogin: { presentAuth(.signIn) }, onRegister: { presentAuth(.register) },
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
        .sheet(item: $authViewModel, onDismiss: finishAuthDismissal) { model in
            AuthScreen(viewModel: model) {
                dismissingAuthViewModel = model
                authViewModel = nil
            }
        }
        .sheet(
            isPresented: favoritePresentation,
            onDismiss: {
                homeViewModel.favoriteSaveViewModel?.presentationDidDismiss()
                if case .authentication(let step) = homeViewModel.favoriteSaveViewModel?.presentation {
                    favoriteAuthViewModel = AuthViewModel(client: authClient, step: step)
                }
            }
        ) {
            if let model = homeViewModel.favoriteSaveViewModel {
                FavoriteSignInScreen(
                    onLogin: { model.authenticate(.signIn) },
                    onRegister: { model.authenticate(.register) }, onClose: model.dismiss)
            }
        }
        .sheet(
            item: $favoriteAuthViewModel,
            onDismiss: {
                finishAuthDismissal()
                homeViewModel.favoriteSaveViewModel?.presentationDidDismiss()
            }
        ) { authModel in
            if let model = homeViewModel.favoriteSaveViewModel {
                AuthScreen(
                    viewModel: authModel,
                    onAuthenticated: {
                        model.authenticationSucceeded()
                        dismissingAuthViewModel = authModel
                        favoriteAuthViewModel = nil
                    },
                    onComplete: {
                        model.dismiss()
                        dismissingAuthViewModel = authModel
                        favoriteAuthViewModel = nil
                    })
            }
        }
        .sensoryFeedback(.success, trigger: homeViewModel.favoriteSaveViewModel?.addedFeedback ?? 0)
        .alert("favorites.updateError", isPresented: favoriteError) {
            Button("feed.retry") {
                homeViewModel.favoriteSaveViewModel?.retry(profile: profileViewModel.state)
            }
            Button("auth.close", role: .cancel) { homeViewModel.favoriteSaveViewModel?.dismiss() }
        }
        .onChange(of: profileViewModel.state) { _, state in
            homeViewModel.favoriteSaveViewModel?.accountChanged(state)
            homeViewModel.favoritesViewModel?.accountChanged(state)
            updateMembership()
            if homeViewModel.favoriteSaveViewModel?.presentation == nil, let favoriteAuthViewModel {
                favoriteAuthViewModel.beginDismissal()
                dismissingAuthViewModel = favoriteAuthViewModel
                self.favoriteAuthViewModel = nil
            }
        }
        .task { await profileViewModel.observe() }
        .onAppear {
            homeViewModel.prepare()
            homeViewModel.favoritesViewModel?.accountChanged(profileViewModel.state)
            profileViewModel.restore()
            updateActivity()
        }
        .onChange(of: selected) { _, _ in updateActivity() }
        .onChange(of: homeViewModel.feedViewModel?.state.snapshot?.articles.map(\.id)) { _, _ in updateMembership() }
        .onChange(of: scenePhase) { _, phase in
            updateActivity()
            if phase == .active { profileViewModel.restore() }
        }
        .onDisappear {
            homeViewModel.feedViewModel?.deactivate()
            homeViewModel.favoriteSaveViewModel?.stop()
            homeViewModel.favoritesViewModel?.stop()
        }
    }

    private func presentAuth(_ step: AuthStep) {
        authViewModel = AuthViewModel(client: authClient, step: step)
    }

    private func finishAuthDismissal() {
        dismissingAuthViewModel?.close()
        dismissingAuthViewModel = nil
    }

    private var favoritePresentation: Binding<Bool> {
        Binding(
            get: {
                if case .invitation = homeViewModel.favoriteSaveViewModel?.presentation { return true }
                return false
            },
            set: { if !$0 { homeViewModel.favoriteSaveViewModel?.presentationBindingDismissed() } })
    }

    private var favoriteError: Binding<Bool> {
        Binding(get: { homeViewModel.favoriteSaveViewModel?.hasError == true }, set: { _ in })
    }

    private func updateActivity() {
        if selected == 0 && scenePhase == .active {
            homeViewModel.feedViewModel?.activate()
            updateMembership()
        } else {
            homeViewModel.feedViewModel?.deactivate()
        }
    }

    private func updateMembership() {
        homeViewModel.favoriteSaveViewModel?.resolveMembership(
            homeViewModel.feedViewModel?.state.snapshot?.articles.map(\.id) ?? [], profile: profileViewModel.state)
    }
}
