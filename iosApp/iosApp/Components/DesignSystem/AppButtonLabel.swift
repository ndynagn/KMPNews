import SwiftUI

/// Keeps the title's layout during loading; the indicator never contributes to button size.
struct AppButtonLabel: View {
    let title: LocalizedStringKey
    let isBusy: Bool

    var body: some View {
        Text(title)
            .multilineTextAlignment(.center)
            .opacity(isBusy ? 0 : 1)
            .frame(maxWidth: .infinity)
            .overlay {
                if isBusy {
                    AppLoadingIndicator()
                        .accessibilityHidden(true)
                }
            }
    }
}
