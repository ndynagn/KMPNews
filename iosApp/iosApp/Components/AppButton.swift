import SwiftUI

/// Shared action geometry; text can grow beyond the minimum at accessibility sizes.
struct AppButton: View {
    enum Appearance { case prominent, secondary, text }
    let title: LocalizedStringKey
    var appearance: Appearance = .prominent
    var isBusy = false
    var isEnabled = true
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            AppButtonLabel(title: title, isBusy: isBusy)
                .tint(appearance == .prominent ? .white : .accentColor)
                .padding(.horizontal, 16).padding(.vertical, 8)
                .frame(maxWidth: .infinity, minHeight: 50)
                .foregroundStyle(appearance == .prominent ? Color.white : Color.accentColor)
                .background {
                    if appearance == .prominent { Capsule().fill(Color.accentColor) }
                    if appearance == .secondary { Capsule().fill(Color.accentColor.opacity(0.12)) }
                }
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain).disabled(!isEnabled || isBusy)
        .accessibilityLabel(title)
        .accessibilityValue(isBusy ? Text("common.loading") : Text(""))
        .opacity(isEnabled && !isBusy ? 1 : 0.6)
    }
}
