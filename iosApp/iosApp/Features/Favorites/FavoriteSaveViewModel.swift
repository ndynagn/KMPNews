import Foundation
import Observation

/// Owns one window's add/remove intent and membership. Closing authentication discards the deferred save.
@MainActor @Observable
final class FavoriteSaveViewModel {
    enum Presentation { case invitation, awaitingAuthentication(AuthStep), authentication(AuthStep) }

    private(set) var presentation: Presentation?
    private(set) var savingID: String?
    private(set) var savedIDs: Set<String> = []
    private(set) var hasError = false
    private(set) var addedFeedback = 0
    private(set) var errorFeedback = 0
    private(set) var showsAddedNotice = false
    private(set) var membershipFailed = false
    private var feedbackGeneration = 0
    private var noticeTask: Task<Void, Never>?
    private var knownIDs: Set<String> = []
    private var previousSaved = false
    private var pendingArticle: FeedArticle?
    private var pendingRemoval: Bool?
    private var membershipTask: Task<Void, Never>?
    private var membershipRevision = 0
    private var operation: Task<Void, Never>?
    private var generation = 0
    private var accountEmail: String?
    private var savesAfterDismissal = false
    private let client: any FavoriteSaveClient
    private let onRemove: () -> Void

    init(client: any FavoriteSaveClient, onRemove: @escaping () -> Void = {}) {
        self.client = client
        self.onRemove = onRemove
    }

    func isSaved(_ id: String, fallback: Bool) -> Bool {
        knownIDs.contains(id) ? savedIDs.contains(id) : fallback
    }

    func save(_ article: FeedArticle, profile: ProfileState, isSaved: Bool? = nil) {
        guard savingID == nil, presentation == nil, !savesAfterDismissal else { return }

        pendingArticle = article
        pendingRemoval = isSaved ?? savedIDs.contains(article.id)
        hasError = false
        switch profile {
        case .guest:
            pendingRemoval = false
            presentation = .invitation
        case .authenticated: startSave()
        case .restoring, .unavailable:
            hasError = true
            errorFeedback += 1
        }
    }

    func authenticate(_ step: AuthStep) {
        guard case .invitation = presentation else { return }

        presentation = .awaitingAuthentication(step)
    }

    func authenticationSucceeded() {
        guard case .authentication = presentation else { return }

        savesAfterDismissal = pendingArticle != nil
        presentation = nil
    }

    /// Present authentication or save only after the preceding native sheet has closed.
    func presentationDidDismiss() {
        if case .awaitingAuthentication(let step) = presentation {
            presentation = .authentication(step)
            return
        }
        guard savesAfterDismissal else { return }

        savesAfterDismissal = false
        startSave()
    }

    /// SwiftUI may write the presentation binding during a successful programmatic dismissal.
    func presentationBindingDismissed() {
        if case .awaitingAuthentication = presentation { return }
        if !savesAfterDismissal { dismiss() }
    }

    func dismiss() {
        savesAfterDismissal = false
        presentation = nil
        pendingArticle = nil
        pendingRemoval = nil
        hasError = false
    }

    func retry(profile: ProfileState) {
        guard let article = pendingArticle else { return }

        guard case .authenticated = profile else {
            save(article, profile: profile)
            return
        }
        startSave()
    }

    /// Resolve feed/search membership independently of the paginated favorites cache.
    func resolveMembership(_ ids: [String], profile: ProfileState) {
        guard savingID == nil else { return }
        membershipTask?.cancel()
        membershipRevision += 1
        guard case .authenticated = profile, !ids.isEmpty else { return }
        let revision = membershipRevision
        let session = generation
        membershipTask = Task { [weak self, client] in
            let result: FavoritesMembership
            do { result = try await client.membership(ids) } catch { result = .failed }
            guard !Task.isCancelled, let self, self.generation == session,
                self.membershipRevision == revision
            else { return }
            switch result {
            case .snapshot(let saved):
                self.savedIDs.subtract(ids)
                self.savedIDs.formUnion(saved)
                self.knownIDs.formUnion(ids)
                self.membershipFailed = false
            case .failed: self.membershipFailed = true
            }
        }
    }

    func accountChanged(_ profile: ProfileState) {
        switch profile {
        case .guest:
            if accountEmail != nil { dismiss() }
            accountEmail = nil
            cancelOperation()
            savedIDs.removeAll()
            knownIDs.removeAll()
        case .authenticated(let email):
            if let accountEmail, accountEmail != email {
                cancelOperation()
                dismiss()
                savedIDs.removeAll()
                knownIDs.removeAll()
            }
            accountEmail = email
        case .restoring, .unavailable: break
        }
    }

    func stop() {
        cancelOperation()
        dismiss()
    }

    /// Navigation drops feedback from an in-flight action without cancelling its persistence.
    func discardPendingFeedback() { feedbackGeneration += 1 }

    func dismissAddedNotice() {
        noticeTask?.cancel()
        showsAddedNotice = false
    }

    private func cancelOperation() {
        rollback()
        noticeTask?.cancel()
        showsAddedNotice = false
        membershipTask?.cancel()
        membershipTask = nil
        membershipFailed = false
        generation += 1
        operation?.cancel()
        operation = nil
        savingID = nil
    }

    private func rollback() {
        guard let id = savingID else { return }

        if previousSaved { savedIDs.insert(id) } else { savedIDs.remove(id) }
    }

    private func announceAddition() {
        addedFeedback += 1
        showsAddedNotice = true
        noticeTask?.cancel()
        noticeTask = Task { [weak self] in
            do { try await Task.sleep(for: .seconds(3)) } catch { return }
            self?.showsAddedNotice = false
        }
    }

    private func startSave() {
        guard let article = pendingArticle, operation == nil else { return }

        hasError = false
        membershipTask?.cancel()
        membershipRevision += 1
        noticeTask?.cancel()
        showsAddedNotice = false
        previousSaved = pendingRemoval == true
        knownIDs.insert(article.id)
        if previousSaved {
            onRemove()
            savedIDs.remove(article.id)
        } else {
            savedIDs.insert(article.id)
        }
        savingID = article.id
        let currentGeneration = generation
        let feedbackOwner = feedbackGeneration
        operation = Task { [weak self, client] in
            let result: FavoriteSaveResult
            do {
                guard let self else { return }
                result =
                    self.pendingRemoval == true
                    ? try await client.remove(article.id) : try await client.save(article)
            } catch {
                guard !Task.isCancelled else { return }
                result = .failed
            }
            guard !Task.isCancelled, let self, self.generation == currentGeneration else { return }

            self.operation = nil
            if result != .saved { self.rollback() }
            self.savingID = nil
            switch result {
            case .saved:
                if self.pendingRemoval == true {
                    self.savedIDs.remove(article.id)
                } else {
                    self.savedIDs.insert(article.id)
                    self.announceAddition()
                }
                self.pendingArticle = nil
                self.pendingRemoval = nil
            case .authenticationRequired:
                self.pendingRemoval = false
                self.presentation = .invitation
            case .busy:
                self.pendingArticle = nil
                self.pendingRemoval = nil
            case .failed:
                self.hasError = true
                if feedbackOwner == self.feedbackGeneration { self.errorFeedback += 1 }
            }
        }
    }
}
