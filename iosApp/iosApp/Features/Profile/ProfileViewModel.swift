import Foundation
import Observation

@MainActor @Observable
final class ProfileViewModel {
    private(set) var state: ProfileState = .restoring
    private(set) var isBusy = false
    private(set) var notice: String?
    private let client: any AuthClient
    private var operation: Task<Void, Never>?

    init(client: any AuthClient) { self.client = client }

    /// Called from an owned SwiftUI task, automatically canceled when its view disappears.
    func observe() async {
        for await value in client.observe() {
            if Task.isCancelled { return }
            state = value
            if case .authenticated = value { notice = nil }
        }
    }

    func restore() {
        guard !isBusy else { return }
        operation?.cancel()
        operation = Task { [client] in _ = try? await client.restore() }
    }

    func signOut() {
        guard !isBusy else { return }
        operation?.cancel()
        isBusy = true
        operation = Task { [weak self, client] in
            let problem: AuthProblem?
            do { problem = try await client.signOut() } catch { problem = .network }
            guard let self else { return }
            self.isBusy = false
            self.notice = problem.map { $0 == .storage ? "auth.storage" : "auth.logout_warning" }
        }
    }
}
