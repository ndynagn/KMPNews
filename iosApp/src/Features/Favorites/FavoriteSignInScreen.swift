import SwiftUI

struct FavoriteSignInScreen: View {
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    let onLogin: () -> Void
    let onRegister: () -> Void
    let onClose: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Spacer()
                Button("auth.close", systemImage: "xmark", action: onClose)
                    .labelStyle(.iconOnly)
                    .font(.system(size: 20, weight: .semibold))
                    .foregroundStyle(.primary)
                    .frame(width: 44, height: 44)
                    .background(.quaternary, in: Circle())
                    .accessibilityIdentifier("favorites.close")
            }
            .padding(12)
            content
        }
        .presentationDetents(dynamicTypeSize.isAccessibilitySize ? [.large] : [.height(450)])
        .presentationDragIndicator(.hidden)
        .interactiveDismissDisabled()
    }

    private var content: some View {
        ScrollView {
            VStack(spacing: 24) {
                Image(systemName: "star")
                    .font(.system(size: 32, weight: .medium))
                    .foregroundStyle(.tint)
                    .frame(width: 72, height: 72)
                    .background(.tint.opacity(0.1), in: Circle())
                    .accessibilityHidden(true)
                VStack(spacing: 12) {
                    Text("favorites.invitation.title")
                        .font(.title2.bold())
                        .accessibilityAddTraits(.isHeader)
                    Text("favorites.invitation.message")
                        .font(.body)
                        .foregroundStyle(.secondary)
                }
                .fixedSize(horizontal: false, vertical: true)
                .multilineTextAlignment(.center)
                VStack(spacing: 8) {
                    AppActionButton(title: "auth.login", action: onLogin)
                        .accessibilityIdentifier("favorites.login")
                    RegistrationPrompt(action: onRegister)
                        .accessibilityIdentifier("favorites.register")
                }
            }
            .padding(.horizontal, 24)
            .padding(.bottom, 24)
            .frame(maxWidth: .infinity)
        }
        .scrollBounceBehavior(.basedOnSize)
    }
}
