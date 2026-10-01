import SwiftUI

/// Shared spacing for grouped forms; native field insets and touch targets remain system-owned.
enum AppFormLayout {
    static let sectionSpacing: CGFloat = 12
}

/// One transparent Form row avoids accumulating native row padding between adjacent actions.
struct AppFormActions<Content: View>: View {
    @ViewBuilder var content: Content

    var body: some View {
        VStack(spacing: 12) { content }
            .listRowInsets(EdgeInsets(top: 0, leading: 0, bottom: 0, trailing: 0))
            .listRowBackground(Color.clear)
            .listRowSeparator(.hidden)
    }
}
