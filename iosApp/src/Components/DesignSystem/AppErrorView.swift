import SwiftUI

/// A full-screen error's content and actions. AppScreenState owns centering and overflow.
struct AppErrorView<Actions: View>: View {
    let title: LocalizedStringKey
    var message: LocalizedStringKey?
    @ViewBuilder var actions: () -> Actions
    @ScaledMetric(relativeTo: .largeTitle) private var iconSize = 40

    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: "exclamationmark.circle")
                .font(.system(size: iconSize))
                .foregroundStyle(.secondary)
                .accessibilityHidden(true)
            VStack(spacing: 4) {
                Text(title).font(.headline)
                if let message { Text(message).foregroundStyle(.secondary) }
            }
            VStack(spacing: 8) { actions() }
        }
        .multilineTextAlignment(.center)
        .frame(maxWidth: .infinity)
    }
}
