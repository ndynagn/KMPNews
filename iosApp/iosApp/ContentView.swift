import AlertToast
import SharedLogic
import SwiftUI

struct ContentView: View {
    @Environment(\.scenePhase) private var scenePhase
    @State private var selected: HomeSection = .news
    @State private var previousSection: HomeSection = .news
    @State private var homeViewModel: HomeViewModel
    private let authClient: any AuthClient
    private let profileClient: any ProfileClient
    @State private var profileViewModel: ProfileViewModel
    @State private var authViewModel: AuthViewModel?
    @State private var favoriteAuthViewModel: AuthViewModel?
    @State private var dismissingAuthViewModel: AuthViewModel?
    @State private var scrollID: String?
    @State private var searchScrollID: String?
    @State private var articleNavigation = ArticleNavigationState()
    #if DEBUG
        @State private var showsComponentCatalog = false
    #endif

    init(services: AppServices) {
        let homeViewModel = HomeViewModel(services: services)
        _homeViewModel = State(initialValue: homeViewModel)
        let client: any AuthClient
        let profiles: any ProfileClient
        #if DEBUG
            if ProcessInfo.processInfo.arguments.contains("--auth-ui-fixture") {
                let fixture = ProfileUITestClient()
                profiles = fixture
                client = AuthUITestClient(profileClient: fixture)
            } else {
                client = SharedAuthClient(authRepository: homeViewModel.authDependencies.authRepository)
                profiles = SharedProfileClient(repository: homeViewModel.authDependencies.profileRepository)
            }
        #else
            client = SharedAuthClient(authRepository: homeViewModel.authDependencies.authRepository)
            profiles = SharedProfileClient(repository: homeViewModel.authDependencies.profileRepository)
        #endif
        authClient = client
        profileClient = profiles
        _profileViewModel = State(initialValue: ProfileViewModel(client: client, profiles: profiles))
    }

    var body: some View {
        HomeTabs(
            selection: selection, usesSearchRole: usesSearchRole
        ) {
            NavigationStack(path: articlePath(for: .news)) {
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
                            }, onOpen: { openArticle($0, in: .news) })
                    } else if homeViewModel.storageFailed {
                        AppScreenState {
                            AppErrorView(title: "feed.unavailableTitle", message: "feed.storageError") {
                                AppActionButton(title: "feed.retry", emphasis: .text) {
                                    homeViewModel.prepare()
                                    updateActivity()
                                }
                            }
                        }
                    } else {
                        AppScreenState { ProgressView("feed.loading") }
                    }
                }
                .navigationTitle("home.news")
                .navigationBarTitleDisplayMode(.large)
                .navigationDestination(for: ArticleRoute.self) { articleDestination($0.model) }
            }
        } favorites: {
            NavigationStack(path: articlePath(for: .favorites)) {
                Group {
                    if let list = homeViewModel.favoritesViewModel, let actions = homeViewModel.favoriteSaveViewModel {
                        FavoritesScreen(
                            viewModel: list, actions: actions, profile: profileViewModel.state,
                            onLogin: { presentAuth(.signIn) }, onRegister: { presentAuth(.register) },
                            onOpen: { openArticle($0, in: .favorites) },
                            isShowingArticle: articleNavigation.destinations[.favorites] != nil)
                    } else {
                        AppScreenState { ProgressView("feed.loading") }
                    }
                }
                .navigationTitle("home.favorites").navigationBarTitleDisplayMode(.large)
                .navigationDestination(for: ArticleRoute.self) { articleDestination($0.model) }
            }
        } profile: {
            NavigationStack {
                ProfileScreen(
                    viewModel: profileViewModel, profileClient: profileClient,
                    state: profileViewModel.state, notice: profileViewModel.notice,
                    isBusy: profileViewModel.isBusy,
                    onLogin: { presentAuth(.signIn) }, onRegister: { presentAuth(.register) },
                    onRetry: { profileViewModel.restore(userInitiated: true) }, onLogout: profileViewModel.signOut
                )
                .navigationTitle("home.profile").navigationBarTitleDisplayMode(.large)
                #if DEBUG
                    .toolbar {
                        ToolbarItem(placement: .topBarTrailing) {
                            if profileViewModel.state == .guest {
                                Button("kit.title") { showsComponentCatalog = true }
                                .accessibilityIdentifier("kit.entry")
                            }
                        }
                    }
                    .sheet(isPresented: $showsComponentCatalog) {
                        ComponentCatalogScreen()
                    }
                #endif
            }
        } search: {
            SearchNavigationDestination(
                query: Binding(
                    get: { homeViewModel.searchViewModel.state.query },
                    set: { homeViewModel.searchViewModel.onEvent(.queryChanged($0)) }),
                isSelected: selected == .search, usesSearchRole: usesSearchRole,
                articlePath: articlePath(for: .search),
                isShowingArticle: articleNavigation.destinations[.search] != nil,
                onClose: { selected = previousSection },
                onSubmit: { homeViewModel.searchViewModel.onEvent(.submit) }
            ) {
                SearchScreen(
                    viewModel: homeViewModel.searchViewModel, scrollID: $searchScrollID,
                    savedIDs: homeViewModel.favoriteSaveViewModel?.savedIDs ?? [],
                    canSave: homeViewModel.favoriteSaveViewModel?.savingID == nil
                        && homeViewModel.favoritesViewModel?.isLoading != true,
                    onSave: { article in
                        guard let actions = homeViewModel.favoriteSaveViewModel else { return }
                        actions.save(
                            article, profile: profileViewModel.state,
                            isSaved: actions.savedIDs.contains(article.id))
                    }, onOpen: { openArticle($0, in: .search) }
                )
                .navigationDestination(for: ArticleRoute.self) { articleDestination($0.model) }
            }
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
                    favoriteAuthViewModel = AuthViewModel(client: authClient, step: step, profileClient: profileClient)
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
        .errorFeedback(
            homeViewModel.favoriteSaveViewModel?.errorFeedback ?? 0,
            isEnabled: authViewModel == nil && favoriteAuthViewModel == nil
        )
        .toast(isPresenting: favoriteAddedNotice, duration: 0, tapToDismiss: false) {
            AlertToast(
                displayMode: .hud, type: .complete(.blue),
                title: String(localized: "favorites.addedNotice"),
                style: .style(titleFont: .subheadline))
        }
        .onChange(of: homeViewModel.favoriteSaveViewModel?.addedFeedback) { _, _ in
            if homeViewModel.favoriteSaveViewModel?.showsAddedNotice == true {
                UIAccessibility.post(notification: .announcement, argument: String(localized: "favorites.addedNotice"))
            }
        }
        .alert("favorites.updateError", isPresented: favoriteError) {
            Button("feed.retry") {
                homeViewModel.favoriteSaveViewModel?.retry(profile: profileViewModel.state)
            }
            Button("auth.close", role: .cancel) { homeViewModel.favoriteSaveViewModel?.dismiss() }
        }
        .onChange(of: profileViewModel.state) { _, state in
            articleNavigation.accountChanged(state)
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
        #if DEBUG
            .preferredColorScheme(profileFixtureColorScheme)
        #endif
        .onAppear {
            homeViewModel.prepare()
            articleNavigation.accountChanged(profileViewModel.state)
            homeViewModel.favoritesViewModel?.accountChanged(profileViewModel.state)
            profileViewModel.restore()
            updateActivity()
        }
        .onChange(of: selected) { _, _ in
            homeViewModel.favoriteSaveViewModel?.discardPendingFeedback()
            homeViewModel.favoritesViewModel?.discardPendingFeedback()
            profileViewModel.discardPendingFeedback()
            updateActivity()
        }
        .onChange(of: articleNavigation.articleIDs) { _, _ in
            homeViewModel.favoriteSaveViewModel?.discardPendingFeedback()
            homeViewModel.favoritesViewModel?.discardPendingFeedback()
            updateMembership()
        }
        .onChange(of: homeViewModel.feedViewModel?.state.snapshot?.articles.map(\.id)) { _, _ in updateMembership() }
        .onChange(of: homeViewModel.searchViewModel.state.articles.map(\.id)) { _, _ in updateMembership() }
        .onChange(of: homeViewModel.favoriteSaveViewModel?.savingID) { _, id in
            if id == nil { updateMembership() }
        }
        .onChange(of: homeViewModel.favoritesViewModel?.isLoading) { _, loading in
            if loading == false { updateMembership() }
        }
        .onChange(of: scenePhase) { _, phase in
            if phase != .active {
                homeViewModel.favoriteSaveViewModel?.discardPendingFeedback()
                homeViewModel.favoritesViewModel?.discardPendingFeedback()
                profileViewModel.discardPendingFeedback()
            }
            updateActivity()
            if phase == .active { profileViewModel.restore() }
        }
        .onDisappear {
            homeViewModel.feedViewModel?.deactivate()
            homeViewModel.searchViewModel.onEvent(.deactivate)
            homeViewModel.favoriteSaveViewModel?.stop()
            homeViewModel.favoritesViewModel?.stop()
        }
    }

    private var usesSearchRole: Bool {
        if #available(iOS 26, *) { return true }
        return false
    }

    private var canPresentArticleAction: Bool {
        authViewModel == nil && favoriteAuthViewModel == nil
            && homeViewModel.favoriteSaveViewModel?.presentation == nil
            && homeViewModel.favoriteSaveViewModel?.savingID == nil
            && homeViewModel.favoriteSaveViewModel?.hasError != true
    }

    private func openArticle(_ article: FeedArticle, in section: HomeSection) {
        guard canPresentArticleAction else { return }
        articleNavigation.open(article, in: section)
    }

    private func articlePath(for section: HomeSection) -> Binding<[ArticleRoute]> {
        Binding(
            get: { articleNavigation.destinations[section].map { [ArticleRoute(model: $0)] } ?? [] },
            set: { if $0.isEmpty { articleNavigation.close(section) } })
    }

    private func articleDestination(_ model: ArticleDetailViewModel) -> some View {
        ArticleDetailScreen(
            viewModel: model,
            favoriteActions: homeViewModel.favoriteSaveViewModel,
            favorites: homeViewModel.favoritesViewModel,
            canPresentActions: { canPresentArticleAction },
            onSave: {
                guard let actions = homeViewModel.favoriteSaveViewModel else { return }
                actions.save(
                    model.article, profile: profileViewModel.state,
                    isSaved: actions.isSaved(
                        model.article.id,
                        fallback: homeViewModel.favoritesViewModel?.snapshot?.articles.contains {
                            $0.id == model.article.id
                        } == true))
            })
    }

    private var selection: Binding<HomeSection> {
        Binding(
            get: { selected },
            set: { value in
                if value == .search, selected != .search { previousSection = selected }
                selected = value
            })
    }

    private func presentAuth(_ step: AuthStep) {
        authViewModel = AuthViewModel(client: authClient, step: step, profileClient: profileClient)
    }

    #if DEBUG
        private var profileFixtureColorScheme: ColorScheme? {
            let arguments = ProcessInfo.processInfo.arguments
            guard arguments.contains("--auth-ui-fixture") else { return nil }
            if arguments.contains("--profile-ui-light") { return .light }
            if arguments.contains("--profile-ui-dark") { return .dark }
            return nil
        }
    #endif

    private func finishAuthDismissal() {
        dismissingAuthViewModel?.close()
        dismissingAuthViewModel = nil
        profileViewModel.refreshAfterAuthentication()
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
        if selected == .news && scenePhase == .active {
            homeViewModel.feedViewModel?.activate()
            updateMembership()
        } else {
            homeViewModel.feedViewModel?.deactivate()
        }
        homeViewModel.searchViewModel.onEvent(
            selected == .search && scenePhase == .active ? .activate : .deactivate)
        if selected == .search && scenePhase == .active { updateMembership() }
    }

    private var favoriteAddedNotice: Binding<Bool> {
        Binding(
            get: { homeViewModel.favoriteSaveViewModel?.showsAddedNotice == true },
            set: { isPresented in
                if !isPresented { homeViewModel.favoriteSaveViewModel?.dismissAddedNotice() }
            })
    }

    private func updateMembership() {
        let ids =
            (homeViewModel.feedViewModel?.state.snapshot?.articles.map(\.id) ?? [])
            + homeViewModel.searchViewModel.state.articles.map(\.id)
            + articleNavigation.articleIDs
        homeViewModel.favoriteSaveViewModel?.resolveMembership(ids, profile: profileViewModel.state)
    }
}
