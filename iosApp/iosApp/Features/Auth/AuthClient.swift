import Foundation

/// Native values keep Kotlin transport/session details out of Swift screens.
enum AuthProblem: String {
    case invalidCredentials = "auth.invalid_credentials"
    case unconfirmed = "auth.unconfirmed"
    case weakPassword = "auth.password_hint"
    case invalidCode = "auth.invalid_code"
    case rateLimited = "auth.rate_limited"
    case network = "auth.network"
    case service = "auth.service"
    case storage = "auth.storage"
    case notConfigured = "auth.not_configured"
    case expired = "auth.session_expired"
}

enum ProfileState: Equatable {
    case restoring
    case guest
    case authenticated(email: String)
    case unavailable(AuthProblem)
}

/// Form/profile bridge: nil operation problems indicate success; task cancellation propagates.
/// Observation belongs to the caller and does not start session restoration.
@MainActor
protocol AuthClient {
    func validate(_ input: AuthUiState, resend: Bool) -> String?
    func observe() -> AsyncStream<ProfileState>
    func restore() async throws -> AuthProblem?
    func signIn(email: String, password: String) async throws -> AuthProblem?
    func register(email: String, password: String, details: PersonalDetails) async throws -> AuthProblem?
    func confirm(email: String, code: String) async throws -> AuthProblem?
    func resend(email: String) async throws -> AuthProblem?
    func signOut() async throws -> AuthProblem?
    func makeRecovery() -> any AuthRecoveryClient
}

struct AuthResetResult {
    var problem: AuthProblem?
    var passwordChanged = false
}

/// A form-owned bridge; no credentials are exposed to Swift presentation.
@MainActor
protocol AuthRecoveryClient {
    func requestCode(email: String) async throws -> AuthProblem?
    func verifyCode(email: String, code: String) async throws -> AuthProblem?
    func resetPassword(_ password: String) async throws -> AuthResetResult
    func cancel() async
}
