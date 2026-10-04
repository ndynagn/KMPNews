import Foundation

/// Presentation metadata for the single primary action of the visible form.
struct AuthPrimaryAction {
    let titleKey: String
    let symbol: String
    let identifier: String
    let event: AuthEvent
    var isEnabled = true

    static func resolve(_ state: AuthUiState) -> Self? {
        if state.passwordWasChanged {
            return Self(
                titleKey: "auth.return_to_login", symbol: "arrow.right",
                identifier: "auth.returnToLogin", event: .returnToLogin)
        }

        if state.step == .signIn && state.needsConfirmation {
            return Self(
                titleKey: "auth.confirm", symbol: "arrow.right",
                identifier: "auth.openConfirmation", event: .confirmEmail)
        }

        let titleKey: String
        let symbol: String
        switch state.step {
        case .signIn:
            titleKey = "auth.login"
            symbol = "checkmark"
        case .register:
            titleKey = "auth.register"
            symbol = "arrow.right"
        case .recovery:
            titleKey = "auth.send_code"
            symbol = "arrow.right"
        case .newPassword:
            titleKey = "auth.save_password"
            symbol = "checkmark"
        case .confirm, .recoveryCode: return nil
        }

        return Self(
            titleKey: titleKey, symbol: symbol, identifier: "auth.submit", event: .submit,
            isEnabled: state.step != .recovery || state.resendSeconds == 0)
    }
}
