import SwiftUI

/// Keeps the title's layout during loading; the indicator never contributes to button size.
struct AppButtonLabel: View {
    let title: LocalizedStringKey
    let isBusy: Bool
    var textAlignment: TextAlignment = .center

    private var contentAlignment: Alignment {
        switch textAlignment {
        case .leading: .leading
        case .trailing: .trailing
        case .center: .center
        }
    }

    var body: some View {
        Text(title)
            .multilineTextAlignment(textAlignment)
            .opacity(isBusy ? 0 : 1)
            .frame(maxWidth: .infinity, alignment: contentAlignment)
            .overlay(alignment: contentAlignment) {
                if isBusy {
                    AppLoadingIndicator()
                        .accessibilityHidden(true)
                }
            }
    }
}
