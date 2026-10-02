import SwiftUI

struct ProfileScreen: View {
    let state: ProfileState
    let notice: String?
    let isBusy: Bool
    let onLogin: () -> Void
    let onRegister: () -> Void
    let onRetry: () -> Void
    let onLogout: () -> Void

    var body: some View {
        GeometryReader { geometry in
            ScrollView {
                VStack(spacing: 16) {
                    VStack(spacing: 12) {
                        switch state {
                        case .restoring: ProgressView("common.loading")
                        case .guest:
                            Text("profile.welcome").font(.largeTitle.bold())
                            Text("profile.guest_hint").foregroundStyle(.secondary)
                        case .authenticated(let email): Text(email).font(.title2)
                        case .unavailable(let problem):
                            AppStatusView(
                                title: "auth.profileUnavailable",
                                systemImage: "person.crop.circle.badge.exclamationmark",
                                message: LocalizedStringKey(problem.rawValue))
                        }
                    }
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .padding(.vertical, 32)
                    if let notice { Text(LocalizedStringKey(notice)).font(.footnote) }
                    switch state {
                    case .guest:
                        AppActionButton(title: "auth.login", action: onLogin)
                            .accessibilityIdentifier("profile.login")
                        RegistrationPrompt(action: onRegister)
                            .accessibilityIdentifier("profile.register")
                    case .authenticated:
                        AppActionButton(title: "auth.logout", emphasis: .secondary, isBusy: isBusy, action: onLogout)
                            .accessibilityIdentifier("profile.logout")
                    case .unavailable:
                        AppActionButton(title: "auth.retry", action: onRetry)
                        AppActionButton(title: "auth.logout", emphasis: .text, isBusy: isBusy, action: onLogout)
                    case .restoring: EmptyView()
                    }
                }
                .disabled(isBusy).padding(24)
                .frame(minHeight: geometry.size.height)
            }
        }
    }
}
