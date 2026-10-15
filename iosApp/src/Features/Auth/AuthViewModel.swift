import Foundation
import Observation

/// One navigation flow owns its requests. Closing it cancels work and invalidates late completions.
@MainActor @Observable
final class AuthViewModel: Identifiable {
    let id = UUID()
    let initialStep: AuthStep
    private(set) var state: AuthUiState
    private(set) var errorFeedback = 0
    private let client: any AuthClient
    private let profileClient: (any ProfileClient)?
    private var recovery: any AuthRecoveryClient
    private var isClosed = false
    private var request: Task<Void, Never>?
    private var countdown: Task<Void, Never>?
    private var generation = 0
    private let now: () -> Date
    private var resendDeadlines: [String: Date] = [:]

    init(
        client: any AuthClient, step: AuthStep, profileClient: (any ProfileClient)? = nil,
        now: @escaping () -> Date = Date.init
    ) {
        self.client = client
        self.profileClient = profileClient
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
            state.canRetryCodeVerification = false
            if code.count == 6 && [.confirm, .recoveryCode].contains(state.step) { submit(resend: false) }

            return
        }

        state.errorKey = nil
        state.canRetryCodeVerification = false

        switch event {
        case .details(let value): state.details = value
        case .photo(let value): state.photo = value
        case .preparingPhoto(let value): state.isPreparingPhoto = value
        case .photoFailed:
            state.errorKey = "profile.photoInvalid"
            errorFeedback += 1
        case .skipPhoto:
            guard state.step == .registrationPhoto else { return }

            state.photo = nil
            state.isComplete = true
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
        case .submit:
            if state.step == .registrationPhoto {
                saveRegistrationPhoto()
            } else if !state.isPreparingPhoto {
                submit(resend: false)
            }
        case .resend: submit(resend: true)
        }
    }

    /// Native back navigation cancels the active request before restoring an earlier step.
    func pop(toDepth depth: Int) {
        guard !isClosed, state.step != .registrationPhoto, depth >= 0, depth < state.history.count else { return }

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
        state.photo = nil
        state.details = PersonalDetails()
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
        let details = state.details
        let photo = state.photo

        state = AuthUiState(step: step, history: history, email: email, details: details, photo: photo)
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

        guard state.errorKey == nil else {
            errorFeedback += 1
            return
        }

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
                    case .register:
                        problem = try await client.register(
                            email: email, password: input.password, details: input.details)
                    case .confirm: problem = try await client.confirm(email: email, code: input.code)
                    case .recovery: problem = try await recovery.requestCode(email: email)
                    case .recoveryCode: problem = try await recovery.verifyCode(email: email, code: input.code)
                    case .newPassword:
                        let result = try await recovery.resetPassword(input.password)
                        problem = result.problem
                        passwordChanged = result.passwordChanged
                    case .registrationPhoto: return
                    }
                }
                guard !Task.isCancelled, let self, self.generation == version else { return }

                self.state.isBusy = false
                if let problem {
                    self.errorFeedback += 1
                    self.state.canRetryCodeVerification =
                        !resend && [.confirm, .recoveryCode].contains(input.step)
                        && [.network, .service, .rateLimited].contains(problem)
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
                } else if input.step == .confirm && input.photo != nil {
                    self.navigate(to: .registrationPhoto)
                    self.saveRegistrationPhoto()
                } else {
                    self.state.isComplete = true
                }
            } catch {
                guard !Task.isCancelled, let self, self.generation == version else { return }

                self.state.isBusy = false
                self.state.errorKey = "auth.service"
                self.errorFeedback += 1
                self.state.canRetryCodeVerification = !resend && [.confirm, .recoveryCode].contains(input.step)
            }
        }
    }

    private func saveRegistrationPhoto() {
        guard let profileClient, let photo = state.photo else {
            state.errorKey = "auth.service"
            errorFeedback += 1
            return
        }

        state.isBusy = true
        state.errorKey = nil

        let details = state.details
        let version = generation

        request = Task { [weak self, profileClient] in
            do {
                let result = try await profileClient.save(details: details, photo: photo, removePhoto: false)

                guard !Task.isCancelled, let self, self.generation == version else { return }

                self.state.isBusy = false
                self.state.errorKey = result.errorKey
                if result.errorKey != nil { self.errorFeedback += 1 }
                if result.errorKey == nil {
                    self.state.photo = nil
                    self.state.isComplete = true
                }
            } catch {
                guard !Task.isCancelled, let self, self.generation == version else { return }

                self.state.isBusy = false
                self.state.errorKey = "auth.service"
                self.errorFeedback += 1
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
