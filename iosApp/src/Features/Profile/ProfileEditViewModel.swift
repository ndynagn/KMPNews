import Foundation
import Observation

@MainActor @Observable
final class ProfileEditViewModel: Identifiable {
    let id = UUID()
    let original: AccountProfile
    let generation: UUID
    private(set) var details: PersonalDetails
    private(set) var photo: Data?
    private(set) var removesPhoto = false
    private(set) var isPreparingPhoto = false
    private(set) var isSaving = false
    private(set) var error: String?
    private(set) var errorFeedback = 0
    private(set) var saved: AccountProfile?
    private let client: any ProfileClient
    private var operation: Task<Void, Never>?

    init(profile: AccountProfile, generation: UUID, client: any ProfileClient) {
        original = profile
        details = profile.details
        self.generation = generation
        self.client = client
    }

    var hasChanges: Bool { details != original.details || photo != nil || removesPhoto }

    func changeDetails(_ value: PersonalDetails) {
        details = value
        error = nil
    }

    func changePhoto(_ value: Data?) {
        photo = value
        removesPhoto = value == nil && original.hasAvatar
        error = nil
    }

    func preparingPhoto(_ value: Bool) { isPreparingPhoto = value }

    func photoFailed() {
        error = "profile.photoInvalid"
        errorFeedback += 1
    }

    func save() {
        guard !isSaving, !isPreparingPhoto else { return }

        if let problem = client.validate(details) {
            error = problem
            errorFeedback += 1
            return
        }

        isSaving = true
        error = nil

        let details = details
        let photo = photo
        let remove = removesPhoto

        operation = Task { [weak self, client] in
            do {
                let result = try await client.save(details: details, photo: photo, removePhoto: remove)

                guard !Task.isCancelled, let self else { return }

                self.isSaving = false
                if let profile = result.profile, profile.userID == self.original.userID {
                    self.saved = profile
                } else {
                    self.error = result.errorKey ?? "auth.session_expired"
                    self.errorFeedback += 1
                }
            } catch {
                guard !Task.isCancelled, let self else { return }

                self.isSaving = false
                self.error = "auth.network"
                self.errorFeedback += 1
            }
        }
    }

    func cancel() {
        operation?.cancel()
        operation = nil
    }
}
