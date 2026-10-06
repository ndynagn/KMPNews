#if DEBUG
    import Foundation
    import Observation

    /// Offline presentation fixture. No account, repository, storage or transport is reachable here.
    @MainActor @Observable
    final class CatalogAuthViewModel {
        enum Step: String, Hashable { case login, register, confirm, recovery }

        enum Outcome: String, CaseIterable { case success, error, network, slow }

        let initialStep: Step
        private(set) var path: [Step] = []
        private(set) var email = ""
        private(set) var password = ""
        private(set) var repeatedPassword = ""
        private(set) var code = ""
        private(set) var outcome = Outcome.success
        private(set) var isBusy = false
        private(set) var message: String?
        private(set) var errorFeedback = 0
        private(set) var canRetryCodeVerification = false
        private(set) var isCompleted = false
        private(set) var resendSeconds = 0
        private var deadlines: [String: Date] = [:]
        private var request: Task<Void, Never>?
        private var ticker: Task<Void, Never>?
        private var generation = 0
        private let now: () -> Date
        private let pause: (Duration) async throws -> Void

        var step: Step { path.last ?? initialStep }

        var hasError: Bool {
            message == "kit.codeError" || message == "kit.requestError" || message == "auth.network"
        }

        var shouldRestoreCodeFocus: Bool {
            !isCompleted && (message == "kit.codeError" || message == "kit.codeSent")
        }

        var canSubmit: Bool {
            guard !isBusy else { return false }

            switch step {
            case .login: return !email.isEmpty && !password.isEmpty
            case .register: return !email.isEmpty && !password.isEmpty && !repeatedPassword.isEmpty
            case .confirm: return code.count == 6
            case .recovery: return !email.isEmpty
            }
        }

        init(
            initialStep: Step = .login,
            now: @escaping () -> Date = Date.init,
            pause: @escaping (Duration) async throws -> Void = { try await Task.sleep(for: $0) }
        ) {
            self.initialStep = initialStep
            self.now = now
            self.pause = pause
        }

        func setEmail(_ value: String) {
            email = value
            message = nil
        }

        func setPassword(_ value: String) {
            password = value
            message = nil
        }

        func setRepeatedPassword(_ value: String) {
            repeatedPassword = value
            message = nil
        }

        func setCode(_ value: String) {
            guard !isBusy, !isCompleted else { return }

            let next = String(value.filter { $0.isASCII && $0.isNumber }.prefix(6))

            guard code != next else { return }

            code = next
            message = nil
            canRetryCodeVerification = false
            if step == .confirm && code.count == 6 { submit() }
        }

        func setOutcome(_ value: Outcome) { outcome = value }

        func navigate(_ target: Step) {
            guard !isBusy else { return }

            clearSecrets()
            message = nil
            path.append(target)
            updateCountdown()
        }

        /// Native back gestures invalidate pending work before clearing secrets.
        func setPath(_ value: [Step]) {
            guard value.count < path.count else { return }

            cancelRequest()
            path = value
            clearSecrets()
            message = nil
            updateCountdown()
        }

        func submit() {
            guard canSubmit else { return }

            perform(resending: false)
        }

        func resend() {
            guard !isBusy && step == .confirm && resendSeconds == 0 else { return }

            perform(resending: true)
        }

        func updateCountdown() {
            resendSeconds = max(0, Int(ceil((deadlines[email] ?? .distantPast).timeIntervalSince(now()))))
        }

        func beginDismissal() {
            generation += 1
            request?.cancel()
            request = nil
            ticker?.cancel()
            ticker = nil
        }

        func close() {
            beginDismissal()
            isBusy = false
            clearSecrets()
            email = ""
            path = []
            message = nil
            deadlines = [:]
            resendSeconds = 0
            isCompleted = false
        }

        private func perform(resending: Bool) {
            isBusy = true
            message = nil
            canRetryCodeVerification = false

            let revision = generation
            let submittedStep = step
            let selectedOutcome = outcome

            request = Task { [weak self, pause] in
                do {
                    try await pause(selectedOutcome == .slow ? .seconds(8) : .milliseconds(700))
                } catch {
                    return
                }

                guard let self, !Task.isCancelled, revision == generation else { return }

                isBusy = false

                if selectedOutcome == .error {
                    errorFeedback += 1
                    message = submittedStep == .confirm && !resending ? "kit.codeError" : "kit.requestError"
                } else if selectedOutcome == .network {
                    errorFeedback += 1
                    message = "auth.network"
                    canRetryCodeVerification = submittedStep == .confirm && !resending
                } else if resending {
                    code = ""
                    startCountdown()
                    message = "kit.codeSent"
                } else {
                    switch submittedStep {
                    case .login, .confirm:
                        isCompleted = true
                    case .register:
                        navigate(.confirm)
                        startCountdown()
                    case .recovery:
                        message = "kit.recoverySent"
                    }
                }
            }
        }

        private func startCountdown() {
            deadlines[email] = now().addingTimeInterval(60)
            updateCountdown()
            ticker?.cancel()

            ticker = Task { [weak self] in
                while !Task.isCancelled {
                    do {
                        try await Task.sleep(for: .seconds(1))
                    } catch {
                        return
                    }

                    guard let self else { return }

                    updateCountdown()

                    if resendSeconds == 0 { return }
                }
            }
        }

        private func cancelRequest() {
            generation += 1
            request?.cancel()
            request = nil
            isBusy = false
        }

        private func clearSecrets() {
            canRetryCodeVerification = false
            password = ""
            repeatedPassword = ""
            code = ""
        }
    }
#endif
