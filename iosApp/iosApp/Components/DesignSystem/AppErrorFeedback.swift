import SwiftUI

extension View {
    /// Plays new failures only while this owner is visible and active; missed events are not replayed.
    func errorFeedback(_ count: Int, isEnabled: Bool = true) -> some View {
        modifier(AppErrorFeedback(count: count, isEnabled: isEnabled))
    }
}

private struct AppErrorFeedback: ViewModifier {
    let count: Int
    let isEnabled: Bool
    @Environment(\.scenePhase) private var scenePhase
    @State private var isVisible = false

    private struct Trigger: Equatable {
        let count: Int
        let isEnabled: Bool
    }

    func body(content: Content) -> some View {
        content
            .sensoryFeedback(
                .error,
                trigger: Trigger(count: count, isEnabled: isEnabled && isVisible && scenePhase == .active)
            ) { previous, current in
                previous.isEnabled && current.isEnabled && current.count > previous.count
            }
            .onAppear { isVisible = true }
            .onDisappear { isVisible = false }
    }
}
