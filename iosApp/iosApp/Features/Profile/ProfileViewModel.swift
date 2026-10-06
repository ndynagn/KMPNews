import Foundation
import Observation

@MainActor @Observable
final class ProfileViewModel {
    private(set) var state: ProfileState = .restoring
    private(set) var isBusy = false
    private(set) var notice: String?
    private(set) var profile: AccountProfile?
    private(set) var profileError: String?
    private(set) var isLoadingProfile = false
    private(set) var generation = UUID()
    private(set) var errorFeedback = 0
    private let client: any AuthClient
    private let profiles: (any ProfileClient)?
    private var operation: Task<Void, Never>?
    private var loadID = UUID()
    private var operationID = UUID()
    private var feedbackGeneration = 0

    init(client: any AuthClient, profiles: (any ProfileClient)? = nil) {
        self.client = client
        self.profiles = profiles
    }

    /// Called from an owned SwiftUI task, automatically canceled when its view disappears.
    func observe() async {
        for await value in client.observe() {
            if Task.isCancelled { return }

            if state != value {
                if case .authenticated = value {
                    operationID = UUID()
                    operation?.cancel()
                    isBusy = false
                }
                generation = UUID()
                profile = nil
                profileError = nil
                isLoadingProfile = false
            }
            state = value
            if case .authenticated = value { notice = nil }
        }
    }

    /// The screen owns cancellation; generation also rejects results from a previous login.
    func loadProfile(userInitiated: Bool = false) async {
        guard case .authenticated = state, let profiles else { return }

        let owner = generation
        let feedbackOwner = feedbackGeneration
        let request = UUID()

        loadID = request
        isLoadingProfile = true
        profileError = nil
        defer { if generation == owner && loadID == request { isLoadingProfile = false } }

        do {
            let result = try await profiles.fetch()

            guard !Task.isCancelled, generation == owner, loadID == request else { return }

            profile = result.profile
            profileError = result.errorKey
            if result.errorKey != nil, userInitiated, feedbackOwner == feedbackGeneration { errorFeedback += 1 }
        } catch {
            if !Task.isCancelled, generation == owner, loadID == request {
                profileError = "auth.network"
                if userInitiated, feedbackOwner == feedbackGeneration { errorFeedback += 1 }
            }
        }
    }

    func accept(_ saved: AccountProfile, generation owner: UUID) {
        guard generation == owner, profile?.userID == saved.userID else { return }

        loadID = UUID()
        isLoadingProfile = false
        profile = saved
        profileError = nil
    }

    func refreshAfterAuthentication() { generation = UUID() }

    /// Suppresses feedback from pending work after its visible owner changes, without cancelling the operation.
    func discardPendingFeedback() { feedbackGeneration += 1 }

    func restore(userInitiated: Bool = false) {
        guard !isBusy else { return }

        operation?.cancel()

        let request = UUID()
        let feedbackOwner = feedbackGeneration

        operationID = request
        operation = Task { [weak self, client] in
            let problem: AuthProblem?
            do { problem = try await client.restore() } catch { problem = .service }

            guard !Task.isCancelled, let self, self.operationID == request else { return }

            if problem != nil, userInitiated, feedbackOwner == self.feedbackGeneration { self.errorFeedback += 1 }
        }
    }

    func signOut() {
        guard !isBusy else { return }

        operation?.cancel()
        isBusy = true

        let request = UUID()
        let feedbackOwner = feedbackGeneration

        operationID = request
        operation = Task { [weak self, client] in
            let problem: AuthProblem?
            do {
                problem = try await client.signOut()
            } catch {
                problem = .network
            }

            guard !Task.isCancelled, let self, self.operationID == request else { return }

            self.isBusy = false
            self.notice = problem.map { $0 == .storage ? "auth.storage" : "auth.logout_warning" }
            if problem != nil, feedbackOwner == self.feedbackGeneration { self.errorFeedback += 1 }
        }
    }
}
