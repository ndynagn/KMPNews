#if DEBUG
    import Foundation

    /// Explicit launch-argument fixture for native UI tests; never calls a server or secure storage.
    @MainActor
    final class AuthUITestClient: AuthClient {
        private var state: ProfileState = .guest
        private var continuation: AsyncStream<ProfileState>.Continuation?
        private let arguments = ProcessInfo.processInfo.arguments

        init() {
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

        func register(email: String, password: String) async throws -> AuthProblem? {
            try await pause()

            return nil
        }

        func confirm(email: String, code: String) async throws -> AuthProblem? {
            try await pause()
            guard code == "012345" else { return .invalidCode }

            authorize(email)

            return nil
        }

        func resend(email: String) async throws -> AuthProblem? {
            try await pause()

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

            init(owner: AuthUITestClient) { self.owner = owner }

            func requestCode(email: String) async throws -> AuthProblem? {
                try await owner?.pause()

                return isClosed ? .expired : nil
            }

            func verifyCode(email: String, code: String) async throws -> AuthProblem? {
                try await owner?.pause()
                guard !isClosed else { return .expired }

                guard code == "012345" else { return .invalidCode }

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
