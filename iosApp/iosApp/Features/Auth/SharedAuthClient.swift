import Foundation
import SharedLogic

/// SKIE observation belongs to the consuming task; termination cancels the Kotlin collector.
@MainActor
final class SharedAuthClient: AuthClient {
    private let authRepository: any AuthRepository

    init(authRepository: any AuthRepository) { self.authRepository = authRepository }

    func validate(_ input: AuthUiState, resend: Bool) -> String? { Self.validateInput(input, resend: resend) }

    static func validateInput(_ input: AuthUiState, resend: Bool) -> String? {
        let validator = AuthInputValidator()
        let issue: AuthInputIssue?
        if resend {
            issue = validator.email(email: input.email)
        } else {
            switch input.step {
            case .signIn: issue = validator.login(email: input.email, password: input.password)
            case .register, .newPassword:
                issue = validator.registration(
                    email: input.email, password: input.password, repeatedPassword: input.repeatPassword)
            case .recovery: issue = validator.email(email: input.email)
            case .confirm, .recoveryCode: issue = validator.confirmation(email: input.email, code: input.code)
            }
        }
        guard let issue else { return nil }
        switch issue {
        case .email: return "auth.invalid_email"
        case .passwordRequired: return "auth.password_required"
        case .passwordTooShort: return "auth.password_hint"
        case .passwordMismatch: return "auth.password_match"
        default: return "auth.invalid_code"
        }
    }

    func observe() -> AsyncStream<ProfileState> {
        AsyncStream { continuation in
            let task = Task { @MainActor [authRepository] in
                for await session in authRepository.session {
                    if Task.isCancelled { break }
                    if let account = session as? AuthSessionAuthenticated {
                        continuation.yield(.authenticated(email: account.user.email))
                    } else if let unavailable = session as? AuthSessionUnavailable {
                        continuation.yield(.unavailable(Self.problem(unavailable.failure) ?? .service))
                    } else if session is AuthSessionGuest {
                        continuation.yield(.guest)
                    } else {
                        continuation.yield(.restoring)
                    }
                }
                continuation.finish()
            }
            continuation.onTermination = { _ in task.cancel() }
        }
    }

    func restore() async throws -> AuthProblem? { Self.problem(try await authRepository.restore().failure) }

    func signIn(email: String, password: String) async throws -> AuthProblem? {
        Self.problem(try await authRepository.signIn(email: email, password: password).failure)
    }

    func register(email: String, password: String) async throws -> AuthProblem? {
        Self.problem(try await authRepository.register(email: email, password: password).failure)
    }

    func confirm(email: String, code: String) async throws -> AuthProblem? {
        Self.problem(try await authRepository.confirm(email: email, code: code).failure)
    }

    func resend(email: String) async throws -> AuthProblem? {
        Self.problem(try await authRepository.resend(email: email).failure)
    }

    func signOut() async throws -> AuthProblem? { Self.problem(try await authRepository.signOut().failure) }

    func makeRecovery() -> any AuthRecoveryClient {
        SharedAuthRecoveryClient(recovery: authRepository.passwordRecovery())
    }

    fileprivate static func problem(_ failure: AuthFailure?) -> AuthProblem? {
        guard let failure else { return nil }
        switch failure {
        case .invalidCredentials: return .invalidCredentials
        case .emailUnconfirmed: return .unconfirmed
        case .weakPassword: return .weakPassword
        case .invalidCode: return .invalidCode
        case .rateLimited: return .rateLimited
        case .network: return .network
        case .storage: return .storage
        case .notConfigured: return .notConfigured
        case .sessionExpired: return .expired
        default: return .service
        }
    }
}

@MainActor
private final class SharedAuthRecoveryClient: AuthRecoveryClient {
    private let recovery: any PasswordRecovery

    init(recovery: any PasswordRecovery) { self.recovery = recovery }

    func requestCode(email: String) async throws -> AuthProblem? {
        SharedAuthClient.problem(try await recovery.requestCode(email: email).failure)
    }

    func verifyCode(email: String, code: String) async throws -> AuthProblem? {
        SharedAuthClient.problem(try await recovery.verifyCode(email: email, code: code).failure)
    }

    func resetPassword(_ password: String) async throws -> AuthResetResult {
        let result = try await recovery.resetPassword(password: password)
        return AuthResetResult(
            problem: SharedAuthClient.problem(result.failure), passwordChanged: result.passwordChanged)
    }

    func cancel() async { _ = try? await recovery.cancel() }
}
