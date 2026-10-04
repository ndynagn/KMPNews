#if DEBUG
    import SwiftUI

    struct CatalogAuthScreen: View {
        @Environment(\.dismiss) private var dismiss
        let viewModel: CatalogAuthViewModel
        let onSuccess: () -> Void

        var body: some View {
            NavigationStack(path: Binding(get: { viewModel.path }, set: viewModel.setPath)) {
                CatalogAuthStepScreen(viewModel: viewModel, step: viewModel.initialStep, onClose: close)
                    .navigationDestination(for: CatalogAuthViewModel.Step.self) { step in
                        CatalogAuthStepScreen(viewModel: viewModel, step: step, onClose: close)
                    }
            }
            .onChange(of: viewModel.isCompleted) { _, complete in
                if complete {
                    onSuccess()
                    close()
                }
            }
            .onDisappear { viewModel.beginDismissal() }
            .presentationSizing(.form)
            .presentationDetents([.large])
            .presentationDragIndicator(.hidden)
            .interactiveDismissDisabled()
        }

        private func close() {
            viewModel.beginDismissal()
            dismiss()
        }
    }

    private struct CatalogAuthStepScreen: View {
        let viewModel: CatalogAuthViewModel
        let step: CatalogAuthViewModel.Step
        let onClose: () -> Void
        @FocusState private var firstFieldFocused: Bool

        private var title: LocalizedStringKey {
            switch step {
            case .login: "auth.login"
            case .register: "auth.registration"
            case .confirm: "auth.confirm"
            case .recovery: "auth.recovery_title"
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
                                isNewPassword: step == .register, accessibilityID: "kit.password")
                            if step == .register {
                                AppPasswordField(
                                    value: Binding(
                                        get: { viewModel.repeatedPassword }, set: viewModel.setRepeatedPassword),
                                    isRepeatedPassword: true, isNewPassword: true, accessibilityID: "kit.repeatPassword"
                                )
                            }
                        }
                    }
                } footer: {
                    VStack(alignment: .leading, spacing: 8) {
                        if let message = viewModel.message {
                            AppFormMessage(title: LocalizedStringKey(message), isError: message.hasSuffix("Error"))
                                .accessibilityIdentifier("kit.message")
                        } else if step != .login {
                            Text(
                                step == .confirm
                                    ? "kit.codeRule" : step == .recovery ? "kit.recoveryRule" : "kit.inputRule"
                            )
                        }
                        if step == .login {
                            AppFormFooterAction(title: "auth.forgot_password") {
                                viewModel.navigate(.recovery)
                            }
                            .accessibilityIdentifier("kit.recovery")
                            Text("kit.inputRule")
                        }
                    }
                }
                .disabled(viewModel.isBusy)
                if step == .confirm {
                    Section {
                        AppFormActions {
                            if step == .confirm && viewModel.isBusy {
                                ProgressView("common.loading").frame(maxWidth: .infinity)
                            } else if viewModel.message == "kit.codeError" && viewModel.code.count == 6 {
                                AppActionButton(title: "auth.retry", emphasis: .text, action: viewModel.submit)
                            }
                            if step == .confirm {
                                AppActionButton(
                                    title: resendTitle, emphasis: .text,
                                    isEnabled: !viewModel.isBusy && viewModel.resendSeconds == 0,
                                    action: viewModel.resend
                                )
                                .accessibilityIdentifier("kit.resend")
                            }
                        }
                    }
                }
                Section("kit.simulation") {
                    Picker("kit.result", selection: Binding(get: { viewModel.outcome }, set: viewModel.setOutcome)) {
                        ForEach(CatalogAuthViewModel.Outcome.allCases, id: \.self) { outcome in
                            Text(LocalizedStringKey("kit.outcome." + outcome.rawValue)).tag(outcome)
                        }
                    }.disabled(viewModel.isBusy)
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
            .onChange(of: viewModel.isBusy) { _, busy in
                if !busy && step == .confirm && step == viewModel.step { firstFieldFocused = true }
            }
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    if step == viewModel.initialStep {
                        Button("auth.close", systemImage: "xmark", action: onClose)
                            .labelStyle(.iconOnly).accessibilityIdentifier("kit.authClose")
                    }
                }
                if step != .confirm && step == viewModel.step {
                    AppToolbarAction(
                        title: step == .register
                            ? "auth.register" : step == .recovery ? "kit.sendLink" : "auth.login",
                        systemImage: step == .login ? "checkmark" : "arrow.right",
                        isBusy: viewModel.isBusy, isEnabled: viewModel.canSubmit,
                        identifier: "kit.submit", action: viewModel.submit
                    )
                }
                ToolbarItemGroup(placement: .keyboard) {
                    Spacer()
                    AppKeyboardDismissButton()
                }
            }
        }
    }
#endif
