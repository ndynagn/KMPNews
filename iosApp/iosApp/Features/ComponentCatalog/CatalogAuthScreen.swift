#if DEBUG
    import SwiftUI

    struct CatalogAuthScreen: View {
        @Environment(\.dismiss) private var dismiss
        @State private var viewModel = CatalogAuthViewModel()
        let onSuccess: () -> Void

        var body: some View {
            NavigationStack(path: Binding(get: { viewModel.path }, set: viewModel.setPath)) {
                CatalogAuthStepScreen(viewModel: viewModel, step: .login, onClose: close)
                    .navigationDestination(for: CatalogAuthViewModel.Step.self) { step in
                        CatalogAuthStepScreen(viewModel: viewModel, step: step, onClose: close)
                    }
            }
            .onChange(of: viewModel.completed) { _, complete in
                if complete { onSuccess(); close() }
            }
            .onDisappear { viewModel.close() }
            .presentationDetents([.large])
        }

        private func close() { viewModel.close(); dismiss() }
    }

    private struct CatalogAuthStepScreen: View {
        let viewModel: CatalogAuthViewModel
        let step: CatalogAuthViewModel.Step
        let onClose: () -> Void
        @FocusState private var emailFocused: Bool

        private var title: LocalizedStringKey {
            switch step {
            case .login: "auth.login"
            case .register: "kit.createAccount"
            case .confirm: "auth.confirm"
            case .recovery: "kit.recovery"
            }
        }

        var body: some View {
            Form {
                Section {
                    Label("kit.demo", systemImage: "testtube.2").font(.subheadline).foregroundStyle(.secondary)
                } footer: {
                    Text("kit.demoRule")
                }
                Section {
                    if step == .confirm {
                        Text(viewModel.email).foregroundStyle(.secondary)
                        TextField("auth.code", text: Binding(get: { viewModel.code }, set: viewModel.setCode))
                            .keyboardType(.numberPad).textContentType(.oneTimeCode)
                            .accessibilityIdentifier("kit.code")
                    } else {
                        TextField("auth.email", text: Binding(get: { viewModel.email }, set: viewModel.setEmail))
                            .textContentType(step == .register ? .emailAddress : .username)
                            .keyboardType(.emailAddress).textInputAutocapitalization(.never).autocorrectionDisabled()
                            .focused($emailFocused).submitLabel(.next).onSubmit { emailFocused = false }
                            .accessibilityIdentifier("kit.email")
                        if step != .recovery {
                            AppPasswordField(
                                value: Binding(get: { viewModel.password }, set: viewModel.setPassword),
                                newPassword: step == .register)
                            if step == .register {
                                AppPasswordField(
                                    value: Binding(
                                        get: { viewModel.repeatedPassword }, set: viewModel.setRepeatedPassword),
                                    repeated: true, newPassword: true)
                            }
                        }
                    }
                } footer: {
                    Text(step == .confirm ? "kit.codeRule" : step == .recovery ? "kit.recoveryRule" : "kit.inputRule")
                }
                .disabled(viewModel.busy)
                Section {
                    if let message = viewModel.message {
                        Label(
                            LocalizedStringKey(message),
                            systemImage: message.hasSuffix("Error") ? "exclamationmark.circle" : "info.circle"
                        )
                        .foregroundStyle(message.hasSuffix("Error") ? Color.red : Color.secondary)
                        .accessibilityIdentifier("kit.message")
                    }
                    AppActionButton(
                        title: step == .recovery ? "kit.sendLink" : title,
                        isBusy: viewModel.busy, isEnabled: viewModel.canSubmit, action: viewModel.submit
                    )
                    .accessibilityIdentifier("kit.submit")
                    if step == .login {
                        AppActionButton(title: "kit.createAccount", emphasis: .text, isEnabled: !viewModel.busy) {
                            viewModel.navigate(.register)
                        }.accessibilityIdentifier("kit.register")
                        AppActionButton(title: "auth.forgot_password", emphasis: .text, isEnabled: !viewModel.busy) {
                            viewModel.navigate(.recovery)
                        }.accessibilityIdentifier("kit.recovery")
                    }
                    if step == .confirm {
                        AppActionButton(
                            title: "auth.resend", emphasis: .text,
                            isEnabled: !viewModel.busy && viewModel.resendSeconds == 0, action: viewModel.resend
                        )
                        .accessibilityIdentifier("kit.resend")
                        if viewModel.resendSeconds > 0 {
                            Text(String(format: String(localized: "auth.resend_wait"), viewModel.resendSeconds))
                                .font(.footnote).foregroundStyle(.secondary)
                        }
                    }
                }
                .listRowBackground(Color.clear).listRowSeparator(.hidden)
                Section("kit.simulation") {
                    Picker("kit.result", selection: Binding(get: { viewModel.outcome }, set: viewModel.setOutcome)) {
                        ForEach(CatalogAuthViewModel.Outcome.allCases, id: \.self) { outcome in
                            Text(LocalizedStringKey("kit.outcome.\(outcome.rawValue)")).tag(outcome)
                        }
                    }.disabled(viewModel.busy)
                }
            }
            .scrollDismissesKeyboard(.interactively)
            .navigationTitle(title)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    if step == .login {
                        Button("auth.close", systemImage: "xmark", action: onClose)
                            .labelStyle(.iconOnly).accessibilityIdentifier("kit.authClose")
                    }
                }
                ToolbarItemGroup(placement: .keyboard) {
                    Spacer()
                    Button("kit.hideKeyboard") {
                        UIApplication.shared.sendAction(
                            #selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
                    }
                }
            }
        }
    }
#endif
