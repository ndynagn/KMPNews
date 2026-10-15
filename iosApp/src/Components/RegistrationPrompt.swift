import SwiftUI

struct RegistrationPrompt: View {
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            (Text("auth.noAccount").foregroundStyle(.secondary)
                + Text(" ")
                + Text("auth.registerPrompt").foregroundStyle(Color.accentColor))
                .font(.footnote)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
                .frame(maxWidth: .infinity, minHeight: 44)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}
