import Foundation
import Observation

/// One navigation flow owns its requests. Closing it cancels work and invalidates late completions.
@MainActor @Observable
final class AuthViewModel: Identifiable {
    let id = UUID()
    let initialStep: AuthStep
    private(set) var state: AuthUiState
    private let client: any AuthClient
    private var recovery: any AuthRecoveryClient
    private var isClosed = false
    private var request: Task<Void, Never>?
    private var countdown: Task<Void, Never>?
    private var generation = 0
    private let now: () -> Date
    private var resendDeadlines: [String: Date] = [:]

    init(client: any AuthClient, step: AuthStep, now: @escaping () -> Date = Date.init) {
        self.client = client
        initialStep = step
        recovery = client.makeRecovery()
        self.now = now
        state = AuthUiState(step: step)
    }

    func onEvent(_ event: AuthEvent) {
        guard !isClosed, !state.isBusy, !state.isComplete else { return }

        if case .code(let value) = event {
            let code = String(value.filter { "0123456789".contains($0) }.prefix(6))

            guard code != state.code else { return }

            state.code = code
            state.errorKey = nil
            if code.count == 6 && [.confirm, .recoveryCode].contains(state.step) { submit(resend: false) }

            return
        }

        state.errorKey = nil

        switch event {
        case .email(let value):
            state.email = value
            state.needsConfirmation = false
            updateCountdown()
        case .password(let value): state.password = value
        case .repeatPassword(let value): state.repeatPassword = value
        case .code: break
        case .recovery: navigate(to: .recovery)
        case .returnToLogin:
            renewRecovery()

            let email = state.email

            state = AuthUiState(step: .signIn, email: email)
        case .confirmEmail:
            navigate(to: .confirm)
        case .back:
            if [.newPassword, .recoveryCode, .recovery].contains(state.step) { renewRecovery() }
            guard let previous = state.history.last else { return }
            navigate(to: previous, goingBack: true)
        case .submit: submit(resend: false)
        case .resend: submit(resend: true)
        }
    }

    /// Native back navigation cancels the active request before restoring an earlier step.
    func pop(toDepth depth: Int) {
        guard !isClosed, depth >= 0, depth < state.history.count else { return }

        generation += 1
        request?.cancel()
        state.isBusy = false

        while state.history.count > depth {
            onEvent(.back)
        }
    }

    /// Stops work without changing the form rendered during the sheet dismissal animation.
    func beginDismissal() {
        guard !isClosed else { return }

        isClosed = true
        generation += 1
        request?.cancel()
        countdown?.cancel()

        let abandoned = recovery

        // This bounded cleanup owns only the old flow, never a newly opened form.
        Task { await abandoned.cancel() }
    }

    /// Called by the presenting view after the native sheet has finished dismissing.
    func close() {
        beginDismissal()
        state.isBusy = false
        state.email = ""
        state.password = ""
        state.repeatPassword = ""
        state.code = ""
    }

    private func renewRecovery() {
        let abandoned = recovery
        recovery = client.makeRecovery()
        Task { await abandoned.cancel() }
    }

    private func cooldownKey(for step: AuthStep, email: String) -> String {
        let purpose = [.recovery, .recoveryCode, .newPassword].contains(step) ? "recovery" : "signup"

        return "\(purpose):\(email)"
    }

    private func navigate(to step: AuthStep, goingBack: Bool = false) {
        let history = goingBack ? Array(state.history.dropLast()) : state.history + [state.step]
        let email = state.email.trimmingCharacters(in: .whitespacesAndNewlines)

        state = AuthUiState(step: step, history: history, email: email)
        updateCountdown()
    }

    private func updateCountdown() {
        let deadline = resendDeadlines[cooldownKey(for: state.step, email: state.email)] ?? .distantPast
        state.resendSeconds = min(60, max(0, Int(ceil(deadline.timeIntervalSince(now())))))
    }

    private func submit(resend: Bool) {
        guard !state.passwordWasChanged else { return }

        updateCountdown()

        guard !(resend || state.step == .recovery) || state.resendSeconds == 0 else { return }

        let email = state.email.trimmingCharacters(in: .whitespacesAndNewlines)
        state.errorKey = client.validate(state, resend: resend)

        guard state.errorKey == nil else { return }

        state.isBusy = true

        let input = state
        let version = generation

        request = Task { [weak self, client, recovery] in
            do {
                let problem: AuthProblem?
                var passwordChanged = false

                if resend {
                    problem =
                        input.step == .recoveryCode
                        ? try await recovery.requestCode(email: email)
                        : try await client.resend(email: email)
                } else {
                    switch input.step {
                    case .signIn: problem = try await client.signIn(email: email, password: input.password)
                    case .register: problem = try await client.register(email: email, password: input.password)
                    case .confirm: problem = try await client.confirm(email: email, code: input.code)
                    case .recovery: problem = try await recovery.requestCode(email: email)
                    case .recoveryCode: problem = try await recovery.verifyCode(email: email, code: input.code)
                    case .newPassword:
                        let result = try await recovery.resetPassword(input.password)
                        problem = result.problem
                        passwordChanged = result.passwordChanged
                    }
                }
                guard !Task.isCancelled, let self, self.generation == version else { return }

                self.state.isBusy = false
                if let problem {
                    self.state.passwordWasChanged = passwordChanged
                    self.state.errorKey = passwordChanged ? "auth.reset_storage" : problem.rawValue
                    if passwordChanged {
                        self.state.password = ""
                        self.state.repeatPassword = ""
                    }
                    if input.step == .newPassword && problem == .expired && !passwordChanged {
                        self.renewRecovery()
                        self.navigate(to: .recoveryCode, goingBack: true)
                        self.state.errorKey = "auth.session_expired"
                    }
                    self.state.needsConfirmation = problem == .unconfirmed
                } else if resend || input.step == .register || input.step == .recovery {
                    let destination: AuthStep =
                        input.step == .recovery || input.step == .recoveryCode ? .recoveryCode : .confirm
                    if input.step != destination { self.navigate(to: destination) }
                    self.resendDeadlines[self.cooldownKey(for: destination, email: email)] = self.now()
                        .addingTimeInterval(60)
                    self.state.code = ""
                    self.updateCountdown()
                    self.startCountdown()
                } else if input.step == .recoveryCode {
                    self.navigate(to: .newPassword)
                } else {
                    self.state.isComplete = true
                }
            } catch {
                guard !Task.isCancelled, let self, self.generation == version else { return }
                self.state.isBusy = false
                self.state.errorKey = "auth.service"
            }
        }
    }

    private func startCountdown() {
        countdown?.cancel()
        countdown = Task { [weak self] in
            while !Task.isCancelled {
                do {
                    try await Task.sleep(for: .seconds(1))
                } catch {
                    return
                }

                guard !Task.isCancelled, let self else { return }

                self.updateCountdown()
                if self.resendDeadlines.values.allSatisfy({ $0 <= self.now() }) { return }
            }
        }
    }
}
