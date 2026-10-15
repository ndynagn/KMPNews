import SwiftUI

struct AuthScreen: View {
    let viewModel: AuthViewModel
    let onComplete: () -> Void
    private let onAuthenticated: (() -> Void)?
    private let initialStep: AuthStep

    init(
        viewModel: AuthViewModel, onAuthenticated: (() -> Void)? = nil,
        onComplete: @escaping () -> Void
    ) {
        self.viewModel = viewModel
        self.onComplete = onComplete
        self.onAuthenticated = onAuthenticated
        initialStep = viewModel.initialStep
    }

    private var path: Binding<[AuthStep]> {
        Binding(
            get: { Array((viewModel.state.history + [viewModel.state.step]).dropFirst()) },
            set: { viewModel.pop(toDepth: $0.count) })
    }

    var body: some View {
        NavigationStack(path: path) {
            AuthStepScreen(viewModel: viewModel, step: initialStep, onClose: onComplete)
                .navigationDestination(for: AuthStep.self) { step in
                    AuthStepScreen(viewModel: viewModel, step: step, onClose: onComplete)
                }
        }
        .presentationSizing(.form)
        .presentationDetents([.large])
        .presentationDragIndicator(.hidden)
        .interactiveDismissDisabled()
        .onChange(of: viewModel.state.isComplete) { _, complete in
            if complete {
                viewModel.beginDismissal()
                (onAuthenticated ?? onComplete)()
            }
        }
        .onDisappear { viewModel.beginDismissal() }
        .errorFeedback(viewModel.errorFeedback)
    }
}

private struct AuthStepScreen: View {
    let viewModel: AuthViewModel
    let step: AuthStep
    let onClose: () -> Void

    private enum Field: Hashable { case email, code }
    @FocusState private var focusedField: Field?
    @State private var passwordFocused = false
    @FocusState private var surnameFocused: Bool
    @State private var isVisible = false
    @State private var isResendingCode = false

    private var resendTitle: LocalizedStringKey {
        viewModel.state.resendSeconds > 0
            ? LocalizedStringKey(String(format: String(localized: "auth.resend_wait"), viewModel.state.resendSeconds))
            : "auth.resend"
    }

    private var isCodeStep: Bool { step == .confirm || step == .recoveryCode }
    private var isNewPassword: Bool { step == .register || step == .newPassword }
    private var screenTitle: LocalizedStringKey {
        switch step {
        case .signIn: "auth.login"
        case .register: "auth.registration"
        case .confirm: "auth.confirm"
        case .recovery: "auth.recovery_title"
        case .recoveryCode: "auth.recovery_confirm"
        case .newPassword: "auth.new_password"
        case .registrationPhoto: "profile.photo"
        }
    }

    var body: some View {
        Form {
            if step == .register {
                Section {
                    ProfileNameFields(
                        details: Binding(
                            get: { viewModel.state.details },
                            set: { viewModel.onEvent(.details($0)) }), surnameFocus: $surnameFocused)
                } header: {
                    Text("profile.personalDetails")
                } footer: {
                    if viewModel.state.errorKey == "profile.namesRequired" {
                        AppFormMessage(title: "profile.namesRequired")
                    }
                }
                .disabled(viewModel.state.isBusy)
                Section {
                    ProfilePhotoField(
                        photo: viewModel.state.photo,
                        onPhoto: { viewModel.onEvent(.photo($0)) },
                        onLoading: { viewModel.onEvent(.preparingPhoto($0)) },
                        onError: { viewModel.onEvent(.photoFailed) })
                    if viewModel.state.isPreparingPhoto { ProgressView("profile.preparingPhoto") }
                } header: {
                    Text("profile.photo")
                } footer: {
                    Text("profile.photoOptional")
                }
                .disabled(viewModel.state.isBusy)
            }
            if step == .registrationPhoto {
                Section {
                    if viewModel.state.isBusy {
                        ProgressView("profile.uploadingPhoto")
                    } else {
                        AppFormMessage(
                            title: LocalizedStringKey(viewModel.state.errorKey ?? "profile.photoUploadFailed"))
                        AppActionButton(title: "auth.retry") { viewModel.onEvent(.submit) }
                        AppActionButton(title: "profile.skipPhoto", emphasis: .text) { viewModel.onEvent(.skipPhoto) }
                    }
                } footer: {
                    Text("profile.accountCreated")
                }
            } else {
                Section {
                    if isCodeStep {
                        TextField("auth.code", text: codeBinding)
                            .keyboardType(.numberPad).textContentType(.oneTimeCode)
                            .focused($focusedField, equals: .code)
                            .accessibilityIdentifier("auth.code")
                    } else {
                        if step != .newPassword {
                            TextField("auth.email", text: binding(\.email, AuthEvent.email))
                                .keyboardType(.emailAddress).textContentType(
                                    step == .signIn ? .username : .emailAddress
                                )
                                .textInputAutocapitalization(.never).autocorrectionDisabled()
                                .focused($focusedField, equals: .email)
                                .accessibilityIdentifier("auth.email")
                        }
                        if step != .recovery {
                            AppPasswordField(
                                value: binding(\.password, AuthEvent.password), isNewPassword: isNewPassword,
                                accessibilityID: "auth.password",
                                isFocused: step == .newPassword ? $passwordFocused : nil)
                            if isNewPassword {
                                AppPasswordField(
                                    value: binding(\.repeatPassword, AuthEvent.repeatPassword),
                                    isRepeatedPassword: true,
                                    isNewPassword: true, accessibilityID: "auth.repeatPassword")
                            }
                        }
                    }
                } header: {
                    if step == .register { Text("profile.credentials") }
                } footer: {
                    VStack(alignment: .leading, spacing: 8) {
                        if let key = viewModel.state.errorKey, key != "profile.namesRequired" {
                            AppFormMessage(title: LocalizedStringKey(key)).accessibilityIdentifier("auth.error")
                        } else if isCodeStep {
                            Text(
                                String(
                                    format: String(
                                        localized: step == .recoveryCode ? "auth.recovery_sent" : "auth.code_hint"),
                                    viewModel.state.email))
                        } else if isNewPassword {
                            Text("auth.password_hint")
                        } else if step == .recovery {
                            Text("auth.recovery_hint")
                        }
                        if step == .signIn {
                            AppFormFooterAction(title: "auth.forgot_password") {
                                viewModel.onEvent(.recovery)
                            }
                            .accessibilityIdentifier("auth.forgotPassword")
                        }
                    }
                }
                .disabled(viewModel.state.isBusy || viewModel.state.passwordWasChanged)
            }
            if isCodeStep || (step == .recovery && viewModel.state.resendSeconds > 0) {
                Section {
                    AppFormActions {
                        if step == .recovery && viewModel.state.resendSeconds > 0 {
                            Text(resendTitle).foregroundStyle(.secondary)
                        }
                        if isCodeStep {
                            if viewModel.state.isBusy {
                                ProgressView("common.loading")
                                    .frame(maxWidth: .infinity)
                            } else if viewModel.state.canRetryCodeVerification {
                                AppActionButton(title: "auth.retryCodeVerification", emphasis: .text) {
                                    sendCodeRequest(.submit)
                                }
                                .accessibilityIdentifier("auth.retryCode")
                            }
                            AppActionButton(
                                title: resendTitle, emphasis: .text,
                                isEnabled: viewModel.state.resendSeconds == 0
                            ) { sendCodeRequest(.resend) }
                            .accessibilityIdentifier("auth.resend")
                        }
                    }
                }
                .disabled(viewModel.state.isBusy)
            }
        }
        .listSectionSpacing(AppFormLayout.sectionSpacing)
        .scrollDismissesKeyboard(.interactively)
        .onAppear { isVisible = true }
        .task {
            // Wait for the sheet/navigation transition before requesting the keyboard.
            do { try await Task.sleep(for: .milliseconds(300)) } catch { return }
            guard isVisible, step == viewModel.state.step, !viewModel.state.isBusy,
                !viewModel.state.isComplete
            else { return }
            focusFirstField()
        }
        .onDisappear {
            isVisible = false
            isResendingCode = false
            focusedField = nil
            surnameFocused = false
            passwordFocused = false
        }
        .onChange(of: viewModel.state.isBusy) { _, busy in
            updateCodeFocus(isBusy: busy)
        }
        .onChange(of: viewModel.state.errorKey) { _, key in
            if step == viewModel.state.step, let key {
                UIAccessibility.post(
                    notification: .announcement, argument: String(localized: String.LocalizationValue(key)))
            }
        }
        .navigationTitle(screenTitle)
        .navigationBarTitleDisplayMode(.inline)
        .navigationBarBackButtonHidden(step == .registrationPhoto)
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                if step == viewModel.initialStep {
                    Button("auth.close", systemImage: "xmark") {
                        viewModel.beginDismissal()
                        onClose()
                    }
                    .labelStyle(.iconOnly).accessibilityIdentifier("auth.close")
                }
            }
            if let action = AuthPrimaryAction.resolve(viewModel.state), step == viewModel.state.step {
                AppToolbarAction(
                    title: LocalizedStringKey(action.titleKey), systemImage: action.symbol,
                    isBusy: viewModel.state.isBusy, isEnabled: action.isEnabled, identifier: action.identifier
                ) { viewModel.onEvent(action.event) }
            }
        }
    }

    private var codeBinding: Binding<String> {
        Binding(
            get: { viewModel.state.code },
            set: {
                viewModel.onEvent(.code($0))
                if viewModel.state.isBusy { focusedField = nil }
            })
    }

    private func sendCodeRequest(_ event: AuthEvent) {
        if case .resend = event { isResendingCode = true }
        viewModel.onEvent(event)

        if viewModel.state.isBusy {
            focusedField = nil
        } else {
            isResendingCode = false
        }
    }

    private func updateCodeFocus(isBusy: Bool) {
        guard isVisible, isCodeStep, step == viewModel.state.step, !viewModel.state.isComplete else { return }

        if isBusy {
            focusedField = nil
            return
        }

        defer { isResendingCode = false }

        let error = viewModel.state.errorKey
        if isResendingCode {
            if error == nil && viewModel.state.code.isEmpty { focusedField = .code }
        } else if error == AuthProblem.invalidCode.rawValue || error == AuthProblem.expired.rawValue {
            focusedField = .code
        }
    }

    private func focusFirstField() {
        if step == .register || step == .registrationPhoto {
            focusedField = nil
            surnameFocused = step == .register
        } else if step == .newPassword {
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
