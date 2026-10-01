import SwiftUI

/// A grouped-form password row whose visibility control does not affect row height.
struct AppPasswordField: View {
    @Binding var value: String
    var repeated = false
    var newPassword = false
    var accessibilityID: String?
    var isFocused: Binding<Bool>?
    @State private var visible = false

    var body: some View {
        AppPasswordInput(
            value: $value, repeated: repeated, newPassword: newPassword, visible: visible,
            accessibilityID: accessibilityID, isFocused: isFocused
        )
        .padding(.trailing, 44)
        .overlay(alignment: .trailing) {
            Button {
                visible.toggle()
            } label: {
                Image(systemName: visible ? "eye.slash" : "eye")
                    .font(.system(size: 18))
                    .frame(width: 44, height: 44).contentShape(Rectangle())
            }
            .buttonStyle(.borderless)
            .accessibilityLabel(visible ? Text("auth.hide") : Text("auth.show"))
        }
    }
}

/// A stable native input keeps focus, AutoFill and text intact while secure entry changes.
private struct AppPasswordInput: UIViewRepresentable {
    @Binding var value: String
    var repeated = false
    var newPassword = false
    let visible: Bool
    var accessibilityID: String?
    var isFocused: Binding<Bool>?
    @Environment(\.isEnabled) private var isEnabled

    func makeUIView(context: Context) -> UITextField {
        let field = UITextField()
        field.font = .preferredFont(forTextStyle: .body)
        field.textColor = .label
        field.adjustsFontForContentSizeCategory = true
        field.keyboardType = .asciiCapable
        field.returnKeyType = .done
        field.delegate = context.coordinator
        field.inputAccessoryView = AppKeyboardAccessory.makeToolbar(
            target: context.coordinator, action: #selector(Coordinator.dismissKeyboard))
        field.autocorrectionType = .no
        field.autocapitalizationType = .none
        field.spellCheckingType = .no
        field.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
        field.addTarget(context.coordinator, action: #selector(Coordinator.changed(_:)), for: .editingChanged)
        return field
    }

    func updateUIView(_ field: UITextField, context: Context) {
        context.coordinator.parent = self
        field.placeholder = String(localized: repeated ? "auth.repeat_password" : "auth.password")
        field.accessibilityLabel = field.placeholder
        field.accessibilityIdentifier = accessibilityID
        let contentType: UITextContentType = newPassword ? .newPassword : .password
        // Preserve the active AutoFill configuration while the user edits either password field.
        if field.textContentType != contentType { field.textContentType = contentType }
        field.isEnabled = isEnabled
        if field.isSecureTextEntry == visible {
            let wasFirstResponder = field.isFirstResponder
            let selection = field.selectedTextRange
            field.isSecureTextEntry = !visible
            field.text = value
            field.selectedTextRange = selection
            if wasFirstResponder { field.becomeFirstResponder() }
        } else if field.text != value {
            field.text = value
        }
        if let isFocused, isFocused.wrappedValue != field.isFirstResponder {
            DispatchQueue.main.async { [weak field, weak coordinator = context.coordinator] in
                guard let field, let coordinator, field.window != nil else { return }
                if coordinator.parent.isFocused?.wrappedValue == true && field.isEnabled {
                    field.becomeFirstResponder()
                } else if field.isFirstResponder {
                    field.resignFirstResponder()
                }
            }
        }
    }

    func sizeThatFits(_ proposal: ProposedViewSize, uiView: UITextField, context: Context) -> CGSize? {
        let width =
            proposal.width.flatMap { $0.isFinite ? max(0, $0) : nil }
            ?? max(0, uiView.intrinsicContentSize.width)
        return CGSize(
            width: width,
            height: max(22, uiView.font?.lineHeight ?? 22))
    }

    func makeCoordinator() -> Coordinator { Coordinator(parent: self) }

    final class Coordinator: NSObject, UITextFieldDelegate {
        var parent: AppPasswordInput

        init(parent: AppPasswordInput) { self.parent = parent }

        @objc func dismissKeyboard() {
            AppKeyboardAccessory.dismiss()
        }

        func textFieldDidBeginEditing(_ textField: UITextField) {
            if parent.isFocused?.wrappedValue == false { parent.isFocused?.wrappedValue = true }
        }

        func textFieldDidEndEditing(_ textField: UITextField) {
            if parent.isFocused?.wrappedValue == true { parent.isFocused?.wrappedValue = false }
        }

        func textFieldShouldReturn(_ textField: UITextField) -> Bool {
            textField.resignFirstResponder()
            return true
        }

        @objc func changed(_ field: UITextField) {
            parent.value = field.text ?? ""
        }
    }
}
