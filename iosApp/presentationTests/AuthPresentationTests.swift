import Foundation

@MainActor
private final class FakeAuthClient: AuthClient {
    var recoveries: [FakeRecoveryClient] = []
    var logins = 0
    var resends = 0
    var loginProblem: AuthProblem?
    var submittedCode = ""
    var confirmations = 0
    var shouldDelayLogin = false
    var shouldDelayRegistration = false
    var wasCancelled = false
    var validationIssue: String?

    var latestRecovery: FakeRecoveryClient {
        guard let recovery = recoveries.last else { preconditionFailure("A ViewModel must create its recovery owner") }

        return recovery
    }

    func makeRecovery() -> any AuthRecoveryClient {
        let recovery = FakeRecoveryClient()
        recoveries.append(recovery)

        return recovery
    }

    func validate(_ input: AuthUiState, resend: Bool) -> String? { validationIssue }

    func observe() -> AsyncStream<ProfileState> {
        AsyncStream {
            $0.yield(.guest)
            $0.finish()
        }
    }

    func restore() async throws -> AuthProblem? { nil }

    func signIn(email: String, password: String) async throws -> AuthProblem? {
        logins += 1
        if shouldDelayLogin {
            do {
                try await Task.sleep(for: .seconds(60))
            } catch {
                wasCancelled = true
                throw error
            }
        }

        return loginProblem
    }

    func register(email: String, password: String) async throws -> AuthProblem? {
        if shouldDelayRegistration {
            do {
                try await Task.sleep(for: .seconds(60))
            } catch {
                wasCancelled = true
                throw error
            }
        }

        return nil
    }

    func confirm(email: String, code: String) async throws -> AuthProblem? {
        confirmations += 1
        submittedCode = code

        return nil
    }

    func resend(email: String) async throws -> AuthProblem? {
        resends += 1

        return nil
    }

    func signOut() async throws -> AuthProblem? { .network }
}

@MainActor
private final class FakeRecoveryClient: AuthRecoveryClient {
    var requests = 0
    var verifications = 0
    var resets = 0
    var wasCancelled = false
    var problem: AuthProblem?
    var resetResult = AuthResetResult(problem: nil, passwordChanged: true)
    var code = ""
    var shouldDelay = false
    var continuation: CheckedContinuation<Void, Never>?

    func requestCode(email: String) async throws -> AuthProblem? {
        requests += 1

        return problem
    }

    func verifyCode(email: String, code: String) async throws -> AuthProblem? {
        verifications += 1
        self.code = code
        if shouldDelay { await withCheckedContinuation { continuation = $0 } }

        return problem
    }

    func resetPassword(_ password: String) async throws -> AuthResetResult {
        resets += 1

        return resetResult
    }

    func cancel() async { wasCancelled = true }
}

@main
struct AuthPresentationTests {
    @MainActor
    static func main() async {
        verifyPrimaryActions()
        await verifyRegistrationAndConfirmation()
        await verifyNavigationClearsSecrets()
        await verifyUnconfirmedLogin()
        await verifyLoginCancellation()
        await verifyRegistrationCancellation()
        await verifyRecoveryStorageFailure()
        await verifyRecoveryRetry()
        await verifyRecoverySuccess()
        await verifyRecoveryBackCancellation()
        await verifyLogoutWarning()

        print(
            "PASS: registration, validation, leading-zero code, cooldown, unconfirmed login, "
                + "duplicate suppression, cancellation, logout warning, recovery navigation, "
                + "storage failure and late-response isolation"
        )
    }

    private static func verifyPrimaryActions() {
        var state = AuthUiState(step: .signIn)
        precondition(AuthPrimaryAction.resolve(state)?.symbol == "checkmark")
        state.needsConfirmation = true
        guard case .confirmEmail = AuthPrimaryAction.resolve(state)?.event else {
            preconditionFailure("Unconfirmed sign-in must offer email confirmation")
        }
        state = AuthUiState(step: .register)
        precondition(AuthPrimaryAction.resolve(state)?.symbol == "arrow.right")
        state = AuthUiState(step: .recovery, resendSeconds: 30)
        precondition(AuthPrimaryAction.resolve(state)?.isEnabled == false)
        for step in [AuthStep.confirm, .recoveryCode] {
            precondition(AuthPrimaryAction.resolve(AuthUiState(step: step)) == nil)
        }
        state = AuthUiState(step: .newPassword, passwordWasChanged: true)
        guard case .returnToLogin = AuthPrimaryAction.resolve(state)?.event else {
            preconditionFailure("A changed password must never be submitted again")
        }
    }

    @MainActor
    private static func verifyRegistrationAndConfirmation() async {
        let client = FakeAuthClient()
        var now = Date()
        let registration = AuthViewModel(client: client, step: .register, now: { now })
        client.validationIssue = "auth.invalid_email"
        registration.onEvent(.submit)

        precondition(registration.state.errorKey == "auth.invalid_email")

        client.validationIssue = "auth.password_match"
        registration.onEvent(.email("reader@example.test"))
        registration.onEvent(.password("password"))
        registration.onEvent(.repeatPassword("different"))
        registration.onEvent(.submit)

        precondition(registration.state.errorKey == "auth.password_match")

        client.validationIssue = nil
        registration.onEvent(.repeatPassword("password"))
        registration.onEvent(.submit)
        await settle()

        precondition(registration.state.step == .confirm)
        precondition(registration.state.password.isEmpty)
        precondition(registration.state.resendSeconds == 60)

        registration.onEvent(.resend)
        await settle()

        precondition(client.resends == 0)

        registration.onEvent(.back)

        precondition(registration.state.step == .register && registration.state.email == "reader@example.test")
        precondition(registration.state.password.isEmpty && registration.state.code.isEmpty)

        registration.onEvent(.confirmEmail)

        precondition(registration.state.resendSeconds == 60)

        registration.onEvent(.back)
        now = now.addingTimeInterval(61)
        registration.onEvent(.confirmEmail)

        precondition(registration.state.resendSeconds == 0)

        registration.onEvent(.code("01234"))

        precondition(!registration.state.isBusy && client.confirmations == 0)

        registration.onEvent(.code("012345"))
        registration.onEvent(.code("012345"))
        registration.onEvent(.submit)
        await settle()

        precondition(client.submittedCode == "012345" && client.confirmations == 1)
        precondition(registration.state.isComplete)

        registration.close()
    }

    @MainActor
    private static func verifyNavigationClearsSecrets() async {
        let client = FakeAuthClient()

        let navigation = AuthViewModel(client: client, step: .signIn)
        navigation.onEvent(.email("reader@example.test"))
        navigation.onEvent(.password("discard-on-navigation"))
        navigation.onEvent(.recovery)

        precondition(navigation.state.history == [.signIn] && navigation.state.password.isEmpty)

        navigation.onEvent(.repeatPassword("discard-on-back"))
        navigation.pop(toDepth: 0)

        precondition(navigation.state.step == .signIn && navigation.state.history.isEmpty)
        precondition(navigation.state.email == "reader@example.test" && navigation.state.repeatPassword.isEmpty)

        navigation.close()
    }

    @MainActor
    private static func verifyUnconfirmedLogin() async {
        let client = FakeAuthClient()

        client.loginProblem = .unconfirmed
        let login = AuthViewModel(client: client, step: .signIn)
        login.onEvent(.email("reader@example.test"))
        login.onEvent(.password("password"))
        login.onEvent(.submit)
        await settle()

        precondition(login.state.needsConfirmation)

        login.onEvent(.confirmEmail)

        precondition(login.state.step == .confirm && login.state.resendSeconds == 0)
        precondition(client.resends == 0)

        login.onEvent(.resend)
        await settle()

        precondition(client.resends == 1)

        login.close()
    }

    @MainActor
    private static func verifyLoginCancellation() async {
        let client = FakeAuthClient()

        client.shouldDelayLogin = true
        let pending = AuthViewModel(client: client, step: .signIn)
        pending.onEvent(.email("reader@example.test"))
        pending.onEvent(.password("password"))
        pending.onEvent(.submit)
        pending.onEvent(.submit)
        await settle()

        precondition(client.logins == 1)

        pending.beginDismissal()
        await settle()

        precondition(client.wasCancelled && !pending.state.isComplete)
        precondition(pending.state.password == "password", "Keep the form intact during dismissal")
        pending.onEvent(.password("late edit"))
        precondition(pending.state.password == "password", "A closing flow must reject edits")
        pending.close()
        precondition(pending.state.password.isEmpty)
    }

    @MainActor
    private static func verifyRegistrationCancellation() async {
        let client = FakeAuthClient()

        client.shouldDelayRegistration = true
        let pendingRegistration = AuthViewModel(client: client, step: .register)
        pendingRegistration.onEvent(.email("reader@example.test"))
        pendingRegistration.onEvent(.password("discard-on-back"))
        pendingRegistration.onEvent(.submit)
        await settle()

        precondition(pendingRegistration.state.isBusy)

        pendingRegistration.close()
        await settle()

        precondition(client.wasCancelled && !pendingRegistration.state.isBusy)
        precondition(pendingRegistration.state.step == .register && pendingRegistration.state.history.isEmpty)
        precondition(pendingRegistration.state.password.isEmpty && !pendingRegistration.state.isComplete)

        pendingRegistration.close()
    }

    @MainActor
    private static func verifyRecoveryStorageFailure() async {
        let client = FakeAuthClient()
        let now = Date()

        let recoveryFlow = AuthViewModel(client: client, step: .signIn, now: { now })
        let recoveryClient = client.latestRecovery
        recoveryFlow.onEvent(.email("reader@example.test"))
        recoveryFlow.onEvent(.recovery)
        recoveryFlow.onEvent(.submit)
        recoveryFlow.onEvent(.submit)
        await settle()

        precondition(recoveryClient.requests == 1 && recoveryFlow.state.step == .recoveryCode)
        precondition(recoveryFlow.state.resendSeconds == 60)

        recoveryFlow.onEvent(.resend)

        precondition(recoveryClient.requests == 1)

        recoveryClient.problem = .invalidCode
        recoveryFlow.onEvent(.code("012345"))
        recoveryFlow.onEvent(.submit)
        await settle()

        precondition(recoveryFlow.state.errorKey == "auth.invalid_code")

        recoveryFlow.onEvent(.code("012345"))
        await settle()

        precondition(recoveryClient.verifications == 1 && recoveryFlow.state.errorKey == "auth.invalid_code")
        precondition(!recoveryFlow.state.isComplete)

        recoveryClient.problem = nil
        recoveryFlow.onEvent(.submit)
        await settle()

        precondition(recoveryClient.code == "012345" && recoveryFlow.state.step == .newPassword)
        precondition(recoveryFlow.state.code.isEmpty && !recoveryFlow.state.isComplete)

        recoveryFlow.onEvent(.password(" new password "))
        recoveryFlow.onEvent(.repeatPassword(" new password "))
        recoveryClient.resetResult = AuthResetResult(problem: .storage, passwordChanged: true)
        recoveryFlow.onEvent(.submit)
        await settle()

        precondition(recoveryFlow.state.passwordWasChanged && !recoveryFlow.state.isComplete)
        precondition(recoveryFlow.state.password.isEmpty && recoveryFlow.state.errorKey == "auth.reset_storage")

        recoveryFlow.onEvent(.submit)

        precondition(recoveryClient.resets == 1)

        recoveryFlow.onEvent(.returnToLogin)

        precondition(recoveryFlow.state.step == .signIn && recoveryFlow.state.history.isEmpty)
        precondition(recoveryFlow.state.email == "reader@example.test")

        recoveryFlow.close()
    }

    @MainActor
    private static func verifyRecoveryRetry() async {
        let client = FakeAuthClient()
        var now = Date()

        let retryRecovery = AuthViewModel(client: client, step: .signIn, now: { now })
        let retryClient = client.latestRecovery
        retryRecovery.onEvent(.email("reader@example.test"))
        retryRecovery.onEvent(.recovery)
        retryClient.problem = .network
        retryRecovery.onEvent(.submit)
        await settle()

        precondition(retryRecovery.state.errorKey == "auth.network" && retryRecovery.state.step == .recovery)

        retryClient.problem = nil
        retryRecovery.onEvent(.submit)
        await settle()

        precondition(retryRecovery.state.resendSeconds == 60)

        now = now.addingTimeInterval(61)
        retryRecovery.onEvent(.resend)
        await settle()

        precondition(retryClient.requests == 3 && retryRecovery.state.resendSeconds == 60)

        retryRecovery.close()
    }

    @MainActor
    private static func verifyRecoverySuccess() async {
        let client = FakeAuthClient()

        let success = AuthViewModel(client: client, step: .signIn)
        success.onEvent(.email("reader@example.test"))
        success.onEvent(.recovery)
        success.onEvent(.submit)
        await settle()
        success.onEvent(.code("012345"))
        success.onEvent(.submit)
        await settle()
        success.onEvent(.password("new password"))
        success.onEvent(.repeatPassword("new password"))
        success.onEvent(.submit)
        await settle()

        precondition(success.state.isComplete && success.state.password == "new password")

        success.close()
        precondition(success.state.password.isEmpty)
    }

    @MainActor
    private static func verifyRecoveryBackCancellation() async {
        let client = FakeAuthClient()
        let now = Date()

        let abandoned = AuthViewModel(client: client, step: .signIn, now: { now })
        let abandonedClient = client.latestRecovery
        abandoned.onEvent(.email("reader@example.test"))
        abandoned.onEvent(.recovery)
        abandoned.onEvent(.submit)
        await settle()
        abandonedClient.shouldDelay = true
        abandoned.onEvent(.code("012345"))
        abandoned.onEvent(.submit)
        await settle()
        abandoned.pop(toDepth: 1)
        abandonedClient.continuation?.resume()
        await settle()

        precondition(abandonedClient.wasCancelled && abandoned.state.step == .recovery)
        precondition(abandoned.state.code.isEmpty && abandoned.state.resendSeconds == 60)

        abandoned.onEvent(.submit)

        precondition(client.latestRecovery.requests == 0)

        abandoned.close()

        precondition(abandoned.state.email.isEmpty && !abandoned.state.isBusy)
    }

    @MainActor
    private static func verifyLogoutWarning() async {
        let client = FakeAuthClient()

        let profile = ProfileViewModel(client: client)
        await profile.observe()
        profile.signOut()
        await settle()

        precondition(profile.notice == "auth.logout_warning")
    }

    private static func settle() async {
        for _ in 0..<30 { await Task.yield() }
    }
}
