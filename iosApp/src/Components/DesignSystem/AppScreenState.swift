import SwiftUI

/// Centers a complete status and its actions in the usable viewport, scrolling when they no longer fit.
struct AppScreenState<Content: View>: View {
    @ViewBuilder var content: () -> Content

    var body: some View {
        GeometryReader { geometry in
            ScrollView {
                VStack(spacing: 16) {
                    content()
                }
                .frame(maxWidth: .infinity)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityElement(children: .contain)
                .accessibilityIdentifier("screenState.content")
                .frame(minHeight: max(0, geometry.size.height - 32))
                .padding(16)
            }
            .accessibilityIdentifier("screenState.viewport")
        }
    }
}
