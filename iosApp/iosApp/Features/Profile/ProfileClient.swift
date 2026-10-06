import Foundation

struct PersonalDetails: Equatable {
    var firstName = ""
    var lastName = ""
    var middleName = ""

    var fullName: String {
        [lastName, firstName, middleName].filter { !$0.isEmpty }.joined(separator: " ")
    }
}

struct AccountProfile: Equatable {
    let userID: String
    let email: String
    let details: PersonalDetails
    let avatarURL: URL?
    var hasAvatar = false
}

struct ProfileLoadResult {
    var profile: AccountProfile?
    var errorKey: String?
}

/// Native bridge for private profile operations; no credentials or transport objects enter presentation.
/// Expected failures return a localized error key; callers must also preserve cancellation from thrown work.
@MainActor
protocol ProfileClient {
    func validate(_ details: PersonalDetails) -> String?

    func fetch() async throws -> ProfileLoadResult

    /// Saves the current account's details and optionally replaces or removes its photo.
    /// A nil photo with `removePhoto == false` preserves the existing photo. Prepared JPEG data
    /// replaces it and takes precedence over `removePhoto`; removal applies only with a nil photo.
    func save(details: PersonalDetails, photo: Data?, removePhoto: Bool) async throws -> ProfileLoadResult
}
