import SwiftUI

struct AuthScreen: View {
    @State private var viewModel: AuthViewModel
    let onComplete: () -> Void
    private let initialStep: AuthStep

    init(client: any AuthClient, step: AuthStep, onComplete: @escaping () -> Void) {
        _viewModel = State(initialValue: AuthViewModel(client: client, step: step))
        self.onComplete = onComplete
        initialStep = step
    }

    private var path: Binding<[AuthStep]> {
        Binding(
            get: { Array((viewModel.state.history + [viewModel.state.step]).dropFirst()) },
            set: { viewModel.pop(toDepth: $0.count) })
    }

    var body: some View {
        NavigationStack(path: path) {
            AuthStepScreen(viewModel: viewModel, step: initialStep, isRoot: true, onClose: onComplete)
                .navigationDestination(for: AuthStep.self) { step in
                    AuthStepScreen(viewModel: viewModel, step: step, isRoot: false, onClose: onComplete)
                }
        }
        .onChange(of: viewModel.state.isComplete) { _, complete in if complete { onComplete() } }
        .onDisappear { viewModel.close() }
    }
}

private struct AuthStepScreen: View {
    let viewModel: AuthViewModel
    let step: AuthStep
    let isRoot: Bool
    let onClose: () -> Void

    private enum Field: Hashable { case email, code }
    @FocusState private var focusedField: Field?
    @State private var passwordFocused = false

    private var actionTitle: LocalizedStringKey {
        switch step {
        case .signIn: "auth.login"
        case .register: "auth.register"
        case .confirm: "auth.confirm"
        case .recoveryCode: "auth.verify_code"
        case .recovery: "auth.send_code"
        case .newPassword: "auth.save_password"
        }
    }

    private var resendTitle: LocalizedStringKey {
        viewModel.state.resendSeconds > 0
            ? LocalizedStringKey(String(format: String(localized: "auth.resend_wait"), viewModel.state.resendSeconds))
            : "auth.resend"
    }

    private var isCodeStep: Bool { step == .confirm || step == .recoveryCode }
    private var isNewPassword: Bool { step == .register || step == .newPassword }
    private var screenTitle: LocalizedStringKey {
        switch step {
        case .register: "auth.registration"
        case .recovery: "auth.recovery_title"
        case .recoveryCode: "auth.recovery_confirm"
        case .newPassword: "auth.new_password"
        default: actionTitle
        }
    }

    var body: some View {
        Form {
            Section {
                if isCodeStep {
                    TextField("auth.code", text: binding(\.code, AuthEvent.code))
                        .keyboardType(.numberPad).textContentType(.oneTimeCode)
                        .focused($focusedField, equals: .code)
                        .accessibilityIdentifier("auth.code")
                } else {
                    if step != .newPassword {
                        TextField("auth.email", text: binding(\.email, AuthEvent.email))
                            .keyboardType(.emailAddress).textContentType(step == .signIn ? .username : .emailAddress)
                            .textInputAutocapitalization(.never).autocorrectionDisabled()
                            .focused($focusedField, equals: .email)
                            .accessibilityIdentifier("auth.email")
                    }
                    if step != .recovery {
                        AppPasswordField(
                            value: binding(\.password, AuthEvent.password), isNewPassword: isNewPassword,
                            accessibilityID: "auth.password", isFocused: step == .newPassword ? $passwordFocused : nil)
                        if isNewPassword {
                            AppPasswordField(
                                value: binding(\.repeatPassword, AuthEvent.repeatPassword), isRepeatedPassword: true,
                                isNewPassword: true, accessibilityID: "auth.repeatPassword")
                        }
                    }
                }
            } footer: {
                if let key = viewModel.state.errorKey {
                    AppFormMessage(title: LocalizedStringKey(key)).accessibilityIdentifier("auth.error")
                } else if isCodeStep {
                    Text(
                        String(
                            format: String(localized: step == .recoveryCode ? "auth.recovery_sent" : "auth.code_hint"),
                            viewModel.state.email))
                } else if isNewPassword {
                    Text("auth.password_hint")
                } else if step == .recovery {
                    Text("auth.recovery_hint")
                }
            }
            .disabled(viewModel.state.isBusy || viewModel.state.passwordWasChanged)
            Section {
                AppFormActions {
                    if viewModel.state.needsConfirmation {
                        AppActionButton(title: "auth.confirm", emphasis: .text) { viewModel.onEvent(.confirmEmail) }
                            .accessibilityIdentifier("auth.openConfirmation")
                    }
                    if step == .signIn {
                        AppActionButton(title: "auth.forgot_password", emphasis: .text, textAlignment: .leading) {
                            viewModel.onEvent(.recovery)
                        }
                        .accessibilityIdentifier("auth.forgotPassword")
                    }
                    if viewModel.state.passwordWasChanged {
                        AppActionButton(title: "auth.login") { viewModel.onEvent(.returnToLogin) }
                            .accessibilityIdentifier("auth.returnToLogin")
                    } else if !isCodeStep {
                        AppActionButton(
                            title: step == .recovery && viewModel.state.resendSeconds > 0 ? resendTitle : actionTitle,
                            isBusy: viewModel.state.isBusy,
                            isEnabled: step != .recovery || viewModel.state.resendSeconds == 0
                        ) { viewModel.onEvent(.submit) }
                        .accessibilityIdentifier("auth.submit")
                    }
                    if step == .signIn {
                        AppActionButton(title: "auth.registration", emphasis: .secondary) {
                            viewModel.onEvent(.register)
                        }
                        .accessibilityIdentifier("auth.register")
                    }
                    if isCodeStep {
                        if viewModel.state.isBusy {
                            ProgressView("common.loading")
                                .frame(maxWidth: .infinity)
                        } else if viewModel.state.errorKey != nil && viewModel.state.code.count == 6 {
                            AppActionButton(title: "auth.retry", emphasis: .text) { viewModel.onEvent(.submit) }
                                .accessibilityIdentifier("auth.retryCode")
                        }
                        AppActionButton(
                            title: resendTitle, emphasis: .text,
                            isEnabled: viewModel.state.resendSeconds == 0
                        ) { viewModel.onEvent(.resend) }
                        .accessibilityIdentifier("auth.resend")
                    }
                }
            }
            .disabled(viewModel.state.isBusy)
        }
        .listSectionSpacing(AppFormLayout.sectionSpacing)
        .scrollDismissesKeyboard(.interactively)
        .task {
            // Wait for the sheet/navigation transition before requesting the keyboard.
            do { try await Task.sleep(for: .milliseconds(300)) } catch { return }
            guard step == viewModel.state.step else { return }
            focusFirstField()
        }
        .onDisappear {
            focusedField = nil; passwordFocused = false
        }
        .onChange(of: viewModel.state.isBusy) { _, busy in
            if !busy && isCodeStep && step == viewModel.state.step { focusedField = .code }
        }
        .onChange(of: viewModel.state.errorKey) { _, key in
            if step == viewModel.state.step, let key {
                UIAccessibility.post(
                    notification: .announcement, argument: String(localized: String.LocalizationValue(key)))
            }
        }
        .navigationTitle(screenTitle)
        .navigationBarTitleDisplayMode(.large)
        .toolbar {
            ToolbarItemGroup(placement: .keyboard) {
                Spacer()
                AppKeyboardDismissButton()
            }
            if isRoot {
                ToolbarItem(placement: .cancellationAction) {
                    Button("auth.close", systemImage: "xmark") {
                        viewModel.close()
                        onClose()
                    }
                    .labelStyle(.iconOnly).accessibilityIdentifier("auth.close")
                }
            }
        }
    }

    private func focusFirstField() {
        if step == .newPassword {
            passwordFocused = true
        } else {
            focusedField = isCodeStep ? .code : .email
        }
    }

    private func binding(_ key: KeyPath<AuthUiState, String>, _ event: @escaping (String) -> AuthEvent) -> Binding<
        String
    > {
        Binding(get: { viewModel.state[keyPath: key] }, set: { viewModel.onEvent(event($0)) })
    }
}
