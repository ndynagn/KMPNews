import SwiftUI

/// A content action using native control sizing and platform button styles.
struct AppActionButton: View {
    enum Emphasis { case primary, secondary, text, destructive }
    let title: LocalizedStringKey
    var emphasis: Emphasis = .primary
    var isBusy = false
    var isEnabled = true
    let action: () -> Void

    var body: some View {
        styledButton
            .controlSize(.large)
            .disabled(isBusy || !isEnabled)
            .accessibilityLabel(title)
            .accessibilityValue(isBusy ? Text("common.loading") : Text(""))
    }

    @ViewBuilder private var styledButton: some View {
        switch emphasis {
        case .primary:
            if #available(iOS 26, *) {
                button.buttonStyle(.glassProminent)
            } else {
                button.buttonStyle(.borderedProminent)
            }
        case .secondary:
            if #available(iOS 26, *) { button.buttonStyle(.glass) } else { button.buttonStyle(.bordered) }
        case .text: button.buttonStyle(.borderless)
        case .destructive: button.buttonStyle(.bordered)
        }
    }

    private var button: some View {
        Button(role: emphasis == .destructive ? .destructive : nil, action: action) {
            AppButtonLabel(title: title, isBusy: isBusy)
        }
    }
}
