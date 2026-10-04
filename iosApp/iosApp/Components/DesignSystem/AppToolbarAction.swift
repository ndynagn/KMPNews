import SwiftUI

/// A primary toolbar action whose progress indicator preserves its touch target.
struct AppToolbarAction: ToolbarContent {
    let title: LocalizedStringKey
    let systemImage: String
    var isBusy = false
    var isEnabled = true
    let identifier: String
    let action: () -> Void

    var body: some ToolbarContent {
        if #available(iOS 26, *) {
            ToolbarItem(placement: .confirmationAction) {
                button.buttonStyle(.plain).foregroundStyle(.tint)
                    .glassEffect(.regular.interactive(), in: .circle)
            }
            // The shared iPad toolbar background clips taps to its compact 36 pt height.
            .sharedBackgroundVisibility(.hidden)
        } else {
            ToolbarItem(placement: .confirmationAction) {
                button.buttonStyle(.borderedProminent)
            }
        }
    }

    private var button: some View {
        Button(action: action) {
            ZStack {
                Image(systemName: systemImage).opacity(isBusy ? 0 : 1)
                if isBusy { ProgressView().accessibilityHidden(true) }
            }
            .frame(width: 44, height: 44)
            .contentShape(Rectangle())
        }
        .buttonBorderShape(.circle)
        .disabled(isBusy || !isEnabled)
        .accessibilityLabel(title)
        .accessibilityValue(isBusy ? Text("common.loading") : Text(""))
        .accessibilityIdentifier(identifier)
    }
}
