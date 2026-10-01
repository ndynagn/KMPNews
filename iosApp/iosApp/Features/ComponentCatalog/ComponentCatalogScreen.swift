#if DEBUG
    import SwiftUI

    struct ComponentCatalogScreen: View {
        @Environment(\.dismiss) private var dismiss
        @State private var viewModel = ComponentCatalogViewModel()
        @State private var showsAuth = false
        @State private var showsConfirmation = false
        @State private var selectedTab = 0

        var body: some View {
            NavigationStack { catalog }
                .sheet(isPresented: $showsAuth) {
                    CatalogAuthScreen(onSuccess: viewModel.completeAuth)
                }
                .alert(
                    "kit.demo",
                    isPresented: Binding(
                        get: { viewModel.feedback != nil }, set: { if !$0 { viewModel.clearFeedback() } })
                ) {
                    Button("common.ok") { viewModel.clearFeedback() }
                } message: {
                    Text(LocalizedStringKey(viewModel.feedback ?? "kit.actionFeedback"))
                }
        }

        private var catalog: some View {
            List {
                Section { Text("kit.catalogRule").foregroundStyle(.secondary) }
                NavigationLink("kit.foundations") { foundations.navigationTitle("kit.foundations") }
                NavigationLink("kit.actions") { actions.navigationTitle("kit.actions") }
                    .accessibilityIdentifier("kit.actionsLink")
                NavigationLink("kit.navigation") { navigation.navigationTitle("kit.navigation") }
                NavigationLink("kit.content") { content.navigationTitle("kit.content") }
                    .accessibilityIdentifier("kit.contentLink")
                NavigationLink("kit.states") { states.navigationTitle("kit.states") }
                    .accessibilityIdentifier("kit.statesLink")
                NavigationLink("kit.profile") { profile.navigationTitle("kit.profile") }
                    .accessibilityIdentifier("kit.profileLink")
                Section("kit.forms") {
                    Button("kit.openAuth") { showsAuth = true }.accessibilityIdentifier("kit.openAuth")
                    Text("kit.formsRule").font(.footnote).foregroundStyle(.secondary)
                }
            }
            .navigationTitle("kit.title")
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("auth.close", systemImage: "xmark") { dismiss() }
                        .labelStyle(.iconOnly).accessibilityIdentifier("kit.close")
                }
            }
        }

        private var foundations: some View {
            List {
                Section("kit.colors") {
                    Label("kit.accent", systemImage: "circle.fill").foregroundStyle(Color.accentColor)
                    Text("kit.primaryText").foregroundStyle(.primary)
                    Text("kit.secondaryText").foregroundStyle(.secondary)
                    Text("kit.surface").listRowBackground(Color(uiColor: .secondarySystemGroupedBackground))
                    Text("kit.colorsRule").font(.footnote)
                }
                Section("kit.typography") {
                    Text("kit.largeTitle").font(.largeTitle)
                    Text("kit.headline").font(.headline)
                    Text("kit.body").font(.body)
                    Text("kit.caption").font(.caption)
                    Text("kit.typeRule").font(.footnote).foregroundStyle(.secondary)
                }
                Section("kit.symbols") {
                    Label("home.news", systemImage: "newspaper")
                    Label("home.favorites", systemImage: "star")
                    Label("home.profile", systemImage: "person")
                }
                Section("kit.layout") {
                    Text("kit.layoutRule"); Text("kit.glassRule")
                }
            }
        }

        private var actions: some View {
            Form {
                Section("kit.states") {
                    Toggle("kit.loading", isOn: Binding(get: { viewModel.isLoading }, set: viewModel.setLoading))
                        .accessibilityIdentifier("kit.loadingToggle")
                    Toggle("kit.disabled", isOn: Binding(get: { viewModel.isDisabled }, set: viewModel.setDisabled))
                }
                Section {
                    AppActionButton(
                        title: "auth.login", isBusy: viewModel.isLoading,
                        isEnabled: !viewModel.isDisabled, action: viewModel.showFeedback)
                    AppActionButton(
                        title: "auth.registration", emphasis: .secondary, isBusy: viewModel.isLoading,
                        isEnabled: !viewModel.isDisabled, action: viewModel.showFeedback)
                    AppActionButton(
                        title: "auth.forgot_password", emphasis: .text, isBusy: viewModel.isLoading,
                        isEnabled: !viewModel.isDisabled, action: viewModel.showFeedback)
                    AppActionButton(
                        title: "auth.logout", emphasis: .destructive, isBusy: viewModel.isLoading,
                        isEnabled: !viewModel.isDisabled
                    ) { showsConfirmation = true }
                } footer: {
                    Text("kit.actionsRule")
                }
                .listRowBackground(Color.clear).listRowSeparator(.hidden)
                Section("kit.icons") {
                    Button("auth.close", systemImage: "xmark", action: viewModel.showFeedback)
                        .labelStyle(.iconOnly).disabled(viewModel.isDisabled || viewModel.isLoading)
                    Text("kit.iconRule").font(.footnote).foregroundStyle(.secondary)
                }
            }
            .confirmationDialog("kit.confirmAction", isPresented: $showsConfirmation, titleVisibility: .visible) {
                Button("auth.logout", role: .destructive, action: viewModel.showFeedback)
                Button("kit.cancel", role: .cancel) {}
            }
        }

        private var navigation: some View {
            List {
                Section { Text("kit.navigationRule") }
                Button("kit.openAuth") { showsAuth = true }
                Button("kit.showAlert", action: viewModel.showFeedback)
                Section("kit.tabs") {
                    TabView(selection: $selectedTab) {
                        Text("kit.newsTab").tabItem { Label("home.news", systemImage: "newspaper") }.tag(0)
                        Text("kit.favoritesTab").tabItem { Label("home.favorites", systemImage: "star") }.tag(1)
                        Text("kit.profileTab").tabItem { Label("home.profile", systemImage: "person") }.tag(2)
                    }.frame(minHeight: 220)
                }
            }
        }

        private var content: some View {
            List {
                Section {
                    Toggle(
                        "kit.imageUnavailable",
                        isOn: Binding(get: { viewModel.imageUnavailable }, set: viewModel.setImageUnavailable))
                }
                Section("kit.card") {
                    news(compact: false)
                }
                Section("kit.row") {
                    news(compact: true)
                }
                Section { Text("kit.contentRule").font(.footnote).foregroundStyle(.secondary) }
            }
        }

        private func news(compact: Bool) -> some View {
            Button(action: viewModel.showFeedback) {
                AppNewsPreview(
                    title: String(localized: "kit.newsTitle"), metadata: String(localized: "kit.newsSource"),
                    compact: compact
                ) {
                    AppImagePlaceholder(state: viewModel.imageUnavailable ? .unavailable : .sample)
                }
            }
            .buttonStyle(.plain)
        }

        private var states: some View {
            List {
                Section("kit.loading") { ProgressView("kit.loading") }
                Section("kit.empty") {
                    AppStatusView(title: "kit.empty", systemImage: "newspaper", message: "kit.emptyHint")
                }
                Section("kit.error") {
                    AppStatusView(title: "kit.requestError", systemImage: "exclamationmark.circle", compact: true)
                    AppActionButton(title: "feed.retry", emphasis: .secondary, action: viewModel.showFeedback)
                }
                Section("kit.imageUnavailable") {
                    AppImagePlaceholder(state: .unavailable).frame(height: 160)
                }
                Section { Text("kit.statesRule").font(.footnote).foregroundStyle(.secondary) }
            }
        }

        private var profile: some View {
            List {
                Section {
                    Text("kit.demo").font(.caption).foregroundStyle(.secondary)
                    if viewModel.authenticated {
                        Label("kit.signedIn", systemImage: "person.crop.circle.badge.checkmark")
                        AppActionButton(title: "auth.logout", emphasis: .secondary, action: viewModel.resetProfile)
                    } else {
                        Text("profile.welcome").font(.title.bold())
                        Text("kit.profileHint").foregroundStyle(.secondary)
                        AppActionButton(title: "auth.login") { showsAuth = true }
                    }
                }
            }
        }
    }
#endif
