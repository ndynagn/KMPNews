import SwiftUI

/// Full empty/error feedback or a compact notice alongside retained content. Actions belong to the caller.
struct AppStatusView: View {
    let title: LocalizedStringKey
    let systemImage: String
    var message: LocalizedStringKey?
    var compact = false

    var body: some View {
        if compact {
            VStack(spacing: 8) {
                Label(title, systemImage: systemImage)
                    .font(.subheadline)
                if let message { Text(message).font(.footnote).foregroundStyle(.secondary) }
            }
            .multilineTextAlignment(.center)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 12)
        } else {
            ContentUnavailableView {
                Label(title, systemImage: systemImage)
            } description: {
                if let message { Text(message) }
            }
        }
    }
}
