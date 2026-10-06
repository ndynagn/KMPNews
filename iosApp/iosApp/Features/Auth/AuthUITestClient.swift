#if DEBUG
    import Foundation

    /// Explicit launch-argument fixture for native UI tests; never calls a server or secure storage.
    @MainActor
    final class AuthUITestClient: AuthClient {
        private var state: ProfileState = .guest
        private var continuation: AsyncStream<ProfileState>.Continuation?
        private let arguments = ProcessInfo.processInfo.arguments
        private let profileClient: ProfileUITestClient?
        private var resendAttempts = 0

        init(profileClient: ProfileUITestClient? = nil) {
            self.profileClient = profileClient
            if arguments.contains("--auth-ui-profile-error") {
                state = .unavailable(.storage)
            } else if arguments.contains("--auth-ui-signed-in") {
                state = .authenticated(email: "reader@example.test")
            }
        }

        func validate(_ input: AuthUiState, resend: Bool) -> String? {
            SharedAuthClient.validateInput(input, resend: resend)
        }

        func observe() -> AsyncStream<ProfileState> {
            AsyncStream { continuation in
                self.continuation = continuation
                continuation.yield(state)
            }
        }

        func restore() async throws -> AuthProblem? { nil }

        func signIn(email: String, password: String) async throws -> AuthProblem? {
            try await pause()

            if arguments.contains("--auth-ui-unconfirmed") { return .unconfirmed }

            authorize(email)

            return nil
        }

        func register(email: String, password: String, details: PersonalDetails) async throws -> AuthProblem? {
            try await pause()
            profileClient?.register(email: email, details: details)

            return nil
        }

        func confirm(email: String, code: String) async throws -> AuthProblem? {
            if let problem = try await checkCode(code) { return problem }

            authorize(email)

            return nil
        }

        func resend(email: String) async throws -> AuthProblem? {
            try await pause()
            resendAttempts += 1
            if arguments.contains("--auth-ui-resend-error-once"), resendAttempts == 1 { return .network }

            return nil
        }

        func signOut() async throws -> AuthProblem? {
            state = .guest
            continuation?.yield(state)

            return nil
        }

        func makeRecovery() -> any AuthRecoveryClient { Recovery(owner: self) }

        private func pause() async throws {
            try await Task.sleep(for: .milliseconds(arguments.contains("--auth-ui-slow") ? 8_000 : 150))
        }

        private func checkCode(_ code: String) async throws -> AuthProblem? {
            if arguments.contains("--auth-ui-code-errors") {
                try await Task.sleep(for: .seconds(arguments.contains("--auth-ui-slow-code") ? 8 : 3))
                switch code {
                case "222222": return .expired
                case "333333": return .network
                case "444444": return .service
                case "555555": return .rateLimited
                default: break
                }
            } else {
                try await pause()
            }

            return code == "012345" ? nil : .invalidCode
        }

        private func authorize(_ email: String) {
            state = .authenticated(email: email)
            continuation?.yield(state)
        }

        @MainActor
        private final class Recovery: AuthRecoveryClient {
            private weak var owner: AuthUITestClient?
            private var email = ""
            private var isVerified = false
            private var isClosed = false
            private var requestAttempts = 0

            init(owner: AuthUITestClient) { self.owner = owner }

            func requestCode(email: String) async throws -> AuthProblem? {
                try await owner?.pause()
                requestAttempts += 1
                if owner?.arguments.contains("--auth-ui-resend-error-once") == true, requestAttempts == 2 {
                    return .network
                }

                return isClosed ? .expired : nil
            }

            func verifyCode(email: String, code: String) async throws -> AuthProblem? {
                let problem = try await owner?.checkCode(code)
                guard !isClosed else { return .expired }

                if let problem { return problem }

                self.email = email
                isVerified = true

                return nil
            }

            func resetPassword(_ password: String) async throws -> AuthResetResult {
                try await owner?.pause()
                guard !isClosed && isVerified else { return AuthResetResult(problem: .expired) }

                if owner?.arguments.contains("--auth-ui-storage-failure") == true {
                    return AuthResetResult(problem: .storage, passwordChanged: true)
                }

                owner?.authorize(email)

                return AuthResetResult(problem: nil, passwordChanged: true)
            }

            func cancel() async {
                isClosed = true
                email = ""
                isVerified = false
            }
        }
    }
#endif
