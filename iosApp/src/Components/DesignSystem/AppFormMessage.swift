import SwiftUI

/// Inline feedback belongs in a Section footer beside the inputs, outside the action stack.
struct AppFormMessage: View {
    let title: LocalizedStringKey
    var isError = true

    var body: some View {
        Text(title)
            .font(.footnote)
            .foregroundStyle(isError ? Color.red : Color.secondary)
            .frame(maxWidth: .infinity, alignment: .leading)
            .fixedSize(horizontal: false, vertical: true)
    }
}
