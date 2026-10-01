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
        @FocusState private var firstFieldFocused: Bool

        private var title: LocalizedStringKey {
            switch step {
            case .login: "auth.login"
            case .register: "kit.createAccount"
            case .confirm: "auth.confirm"
            case .recovery: "kit.recovery"
            }
        }

        private var resendTitle: LocalizedStringKey {
            viewModel.resendSeconds > 0
                ? LocalizedStringKey(String(format: String(localized: "auth.resend_wait"), viewModel.resendSeconds))
                : "auth.resend"
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
                            .focused($firstFieldFocused)
                            .accessibilityIdentifier("kit.code")
                    } else {
                        TextField("auth.email", text: Binding(get: { viewModel.email }, set: viewModel.setEmail))
                            .textContentType(step == .register ? .emailAddress : .username)
                            .keyboardType(.emailAddress).textInputAutocapitalization(.never).autocorrectionDisabled()
                            .focused($firstFieldFocused).submitLabel(.next).onSubmit { firstFieldFocused = false }
                            .accessibilityIdentifier("kit.email")
                        if step != .recovery {
                            AppPasswordField(
                                value: Binding(get: { viewModel.password }, set: viewModel.setPassword),
                                newPassword: step == .register, accessibilityID: "kit.password")
                            if step == .register {
                                AppPasswordField(
                                    value: Binding(
                                        get: { viewModel.repeatedPassword }, set: viewModel.setRepeatedPassword),
                                    repeated: true, newPassword: true, accessibilityID: "kit.repeatPassword")
                            }
                        }
                    }
                } footer: {
                    if let message = viewModel.message {
                        AppFormMessage(title: LocalizedStringKey(message), isError: message.hasSuffix("Error"))
                            .accessibilityIdentifier("kit.message")
                    } else {
                        Text(
                            step == .confirm ? "kit.codeRule" : step == .recovery ? "kit.recoveryRule" : "kit.inputRule"
                        )
                    }
                }
                .disabled(viewModel.busy)
                Section {
                    AppFormActions {
                        if step != .confirm {
                            AppActionButton(
                                title: step == .recovery ? "kit.sendLink" : title,
                                isBusy: viewModel.busy, isEnabled: viewModel.canSubmit, action: viewModel.submit
                            )
                            .accessibilityIdentifier("kit.submit")
                        } else if viewModel.busy {
                            ProgressView("common.loading").frame(maxWidth: .infinity)
                        } else if viewModel.message == "kit.codeError" && viewModel.code.count == 6 {
                            AppActionButton(title: "auth.retry", emphasis: .text, action: viewModel.submit)
                        }
                        if step == .login {
                            AppActionButton(title: "kit.createAccount", emphasis: .text, isEnabled: !viewModel.busy) {
                                viewModel.navigate(.register)
                            }.accessibilityIdentifier("kit.register")
                            AppActionButton(
                                title: "auth.forgot_password", emphasis: .text, textAlignment: .leading,
                                isEnabled: !viewModel.busy
                            ) {
                                viewModel.navigate(.recovery)
                            }.accessibilityIdentifier("kit.recovery")
                        }
                        if step == .confirm {
                            AppActionButton(
                                title: resendTitle, emphasis: .text,
                                isEnabled: !viewModel.busy && viewModel.resendSeconds == 0, action: viewModel.resend
                            )
                            .accessibilityIdentifier("kit.resend")
                        }
                    }
                }
                Section("kit.simulation") {
                    Picker("kit.result", selection: Binding(get: { viewModel.outcome }, set: viewModel.setOutcome)) {
                        ForEach(CatalogAuthViewModel.Outcome.allCases, id: \.self) { outcome in
                            Text(LocalizedStringKey("kit.outcome.\(outcome.rawValue)")).tag(outcome)
                        }
                    }.disabled(viewModel.busy)
                }
            }
            .listSectionSpacing(AppFormLayout.sectionSpacing)
            .scrollDismissesKeyboard(.interactively)
            .task {
                do { try await Task.sleep(for: .milliseconds(300)) } catch { return }
                guard step == viewModel.step else { return }
                firstFieldFocused = true
            }
            .onDisappear { firstFieldFocused = false }
            .onChange(of: viewModel.busy) { _, busy in
                if !busy && step == .confirm && step == viewModel.step { firstFieldFocused = true }
            }
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
                    AppKeyboardDismissButton()
                }
            }
        }
    }
#endif
