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
                        case .restoring: ProgressView()
                        case .guest:
                            Text("profile.welcome").font(.largeTitle.bold())
                            Text("profile.guest_hint").foregroundStyle(.secondary)
                        case .authenticated(let email): Text(email).font(.title2)
                        case .unavailable(let problem): Text(LocalizedStringKey(problem.rawValue)).foregroundStyle(.red)
                        }
                    }
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .padding(.vertical, 32)
                    if let notice { Text(LocalizedStringKey(notice)).font(.footnote) }
                    switch state {
                    case .guest:
                        AppButton(title: "auth.login", action: onLogin)
                            .accessibilityIdentifier("profile.login")
                        AppButton(title: "auth.register", appearance: .text, action: onRegister)
                            .accessibilityIdentifier("profile.register")
                    case .authenticated:
                        AppButton(title: "auth.logout", appearance: .secondary, action: onLogout)
                            .accessibilityIdentifier("profile.logout")
                    case .unavailable:
                        AppButton(title: "auth.retry", action: onRetry)
                        AppButton(title: "auth.logout", appearance: .text, action: onLogout)
                    case .restoring: EmptyView()
                    }
                }
                .disabled(isBusy).padding(24)
                .frame(minHeight: geometry.size.height)
            }
        }
    }
}
