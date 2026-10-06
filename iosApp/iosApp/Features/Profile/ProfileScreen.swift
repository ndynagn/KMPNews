import SwiftUI

struct ProfileScreen: View {
    @State private var showsLogoutConfirmation = false
    @State private var editor: ProfileEditViewModel?
    @State private var reload = UUID()
    @State private var reloadIsUserInitiated = false
    @ScaledMetric private var skeletonLineHeight = 16
    let viewModel: ProfileViewModel
    let profileClient: any ProfileClient
    let state: ProfileState
    let notice: String?
    let isBusy: Bool
    let onLogin: () -> Void
    let onRegister: () -> Void
    let onRetry: () -> Void
    let onLogout: () -> Void

    var body: some View {
        content
            .toolbar {
                if let profile = viewModel.profile {
                    ToolbarItem(placement: .topBarTrailing) {
                        Button("profile.edit", systemImage: "pencil") {
                            editor = ProfileEditViewModel(
                                profile: profile, generation: viewModel.generation, client: profileClient)
                        }
                        .labelStyle(.titleOnly)
                        .tint(.accentColor)
                        .disabled(isBusy)
                        .accessibilityIdentifier("profile.edit")
                    }
                }
            }
            .sheet(
                item: $editor,
                onDismiss: {
                    reloadIsUserInitiated = false
                    reload = UUID()
                }
            ) { editor in
                ProfileEditScreen(viewModel: editor) {
                    viewModel.accept($0, generation: editor.generation)
                }
            }
            .task(id: "\(viewModel.generation)-\(reload)") {
                let userInitiated = reloadIsUserInitiated
                reloadIsUserInitiated = false
                await viewModel.loadProfile(userInitiated: userInitiated)
            }
            .errorFeedback(viewModel.errorFeedback, isEnabled: editor == nil)
            .onDisappear { viewModel.discardPendingFeedback() }
            .onChange(of: editor?.id) { _, _ in viewModel.discardPendingFeedback() }
            .alert("profile.logoutTitle", isPresented: $showsLogoutConfirmation) {
                Button("profile.cancel", role: .cancel) {}
                Button("auth.logout", role: .destructive, action: onLogout).disabled(isBusy)
            } message: {
                Text("profile.logoutMessage")
            }
            .onChange(of: state) { _, _ in
                showsLogoutConfirmation = false
                editor?.cancel()
                editor = nil
            }
    }

    @ViewBuilder
    private var content: some View {
        switch state {
        case .guest:
            profileContent
        case .restoring, .unavailable, .authenticated:
            authenticatedContent
        }
    }

    private var authenticatedContent: some View {
        List {
            if case .unavailable(let problem) = state {
                Section {
                    Text("auth.profileUnavailable").font(.headline)
                    Text(LocalizedStringKey(problem.rawValue))
                    Button("auth.retry", action: onRetry)
                        .disabled(isBusy)
                        .accessibilityIdentifier("profile.retry")
                }
            } else if state == .restoring {
                skeletonSections
            } else if let profile = viewModel.profile {
                Section {
                    VStack(spacing: 14) {
                        ProfileAvatar(profile: profile, generation: viewModel.generation)
                        if !profile.details.fullName.isEmpty {
                            Text(profile.details.fullName).font(.title2.bold())
                                .multilineTextAlignment(.center)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                }
                .listRowBackground(Color.clear)
                Section("profile.personalDetails") {
                    detail("profile.lastName", value: profile.details.lastName)
                    detail("profile.firstName", value: profile.details.firstName)
                    detail("profile.middleNameLabel", value: profile.details.middleName)
                }
                Section("profile.contacts") { detail("profile.email", value: profile.email) }
            } else if viewModel.profileError == nil {
                skeletonSections
            }
            if case .authenticated = state, let error = viewModel.profileError {
                Section {
                    Text(LocalizedStringKey(error))
                    Button("auth.retry") {
                        reloadIsUserInitiated = true
                        reload = UUID()
                    }
                    .disabled(viewModel.isLoadingProfile || isBusy)
                    .accessibilityIdentifier("profile.retry")
                }
            }
            Section {
                Button(role: .destructive) {
                    showsLogoutConfirmation = true
                } label: {
                    HStack {
                        Text("auth.logout")
                        if isBusy { Spacer(); ProgressView() }
                    }
                    .frame(minHeight: 28)
                }
                .disabled(isBusy)
                .accessibilityIdentifier("profile.logout")
            }
            if let notice { Section { Text(LocalizedStringKey(notice)).font(.footnote) } }
        }
        .listStyle(.insetGrouped)
        .contentMargins(.horizontal, 16, for: .scrollContent)
        .frame(maxWidth: 680)
        .frame(maxWidth: .infinity)
        .background(Color(uiColor: .systemGroupedBackground))
        .refreshable { await viewModel.loadProfile(userInitiated: true) }
    }

    @ViewBuilder
    private var skeletonSections: some View {
        Section {
            VStack(spacing: 14) {
                Circle().fill(.quaternary).frame(width: 112, height: 112)
                skeletonLine(width: 200)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 12)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("common.loading")
            .accessibilityIdentifier("profile.loading")
        }
        .listRowBackground(Color.clear)
        Section("profile.personalDetails") {
            skeletonDetail("profile.lastName")
            skeletonDetail("profile.firstName")
            skeletonDetail("profile.middleNameLabel")
        }
        Section("profile.contacts") { skeletonDetail("profile.email") }
    }

    private func skeletonDetail(_ label: LocalizedStringKey) -> some View {
        ViewThatFits(in: .horizontal) {
            HStack(spacing: 16) {
                Text(label).fixedSize()
                Spacer(minLength: 12)
                skeletonLine(width: 100)
            }
            VStack(alignment: .leading, spacing: 6) {
                Text(label)
                skeletonLine(width: 100)
            }
        }
        .padding(.vertical, 3)
        .accessibilityHidden(true)
    }

    private func skeletonLine(width: CGFloat) -> some View {
        RoundedRectangle(cornerRadius: 5)
            .fill(.quaternary)
            .frame(width: width, height: skeletonLineHeight)
    }

    private func detail(_ label: LocalizedStringKey, value: String) -> some View {
        ViewThatFits(in: .horizontal) {
            HStack(alignment: .firstTextBaseline, spacing: 16) {
                Text(label).fixedSize()
                Spacer(minLength: 12)
                detailValue(value).fixedSize()
            }
            VStack(alignment: .leading, spacing: 6) {
                Text(label)
                detailValue(value)
            }
        }
        .padding(.vertical, 3)
        .accessibilityElement(children: .combine)
    }

    private func detailValue(_ value: String) -> some View {
        Group {
            if value.isEmpty { Text("profile.notSpecified") } else { Text(value).textSelection(.enabled) }
        }
        .foregroundStyle(.secondary)
    }

    private var profileContent: some View {
        GeometryReader { geometry in
            ScrollView {
                VStack(spacing: 16) {
                    VStack(spacing: 12) {
                        Text("profile.welcome").font(.largeTitle.bold())
                        Text("profile.guest_hint").foregroundStyle(.secondary)
                    }
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .padding(.vertical, 32)
                    if let notice { Text(LocalizedStringKey(notice)).font(.footnote) }
                    AppActionButton(title: "auth.login", action: onLogin)
                        .accessibilityIdentifier("profile.login")
                    RegistrationPrompt(action: onRegister)
                        .accessibilityIdentifier("profile.register")
                }
                .disabled(isBusy).padding(24)
                .frame(minHeight: geometry.size.height)
            }
        }
    }
}
