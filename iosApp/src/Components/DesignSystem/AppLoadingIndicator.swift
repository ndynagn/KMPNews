import SwiftUI

/// Uses the standard system size, independently of the containing button's control size.
struct AppLoadingIndicator: View {
    var body: some View {
        ProgressView()
            .progressViewStyle(.circular)
            .controlSize(.regular)
    }
}
