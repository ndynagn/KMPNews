import SwiftUI

/// A readable image surface shared by cards and catalog specimens; no loading policy is owned here.
struct AppImagePlaceholder: View {
    enum State { case loading, unavailable, sample }

    let state: State

    var body: some View {
        Rectangle().fill(Color(uiColor: .tertiarySystemFill))
            .overlay {
                if state == .loading {
                    AppLoadingIndicator()
                } else {
                    Image(systemName: state == .unavailable ? "photo.badge.exclamationmark" : "photo")
                        .font(.largeTitle).foregroundStyle(.secondary)
                }
            }
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(label)
    }

    private var label: Text {
        switch state {
        case .loading: Text("common.loading")
        case .unavailable: Text("common.imageUnavailable")
        case .sample: Text("common.imagePlaceholder")
        }
    }
}
