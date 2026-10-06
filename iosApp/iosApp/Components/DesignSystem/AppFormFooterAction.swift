import SwiftUI

struct AppFormFooterAction: View {
    let title: LocalizedStringKey
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.body)
                .multilineTextAlignment(.leading)
                .padding(.top, 4)
                .frame(maxWidth: .infinity, minHeight: 44, alignment: .topLeading)
                .contentShape(Rectangle())
        }
        .buttonStyle(.borderless)
    }
}
