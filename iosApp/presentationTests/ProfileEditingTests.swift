import Foundation

@MainActor
private final class ProfileEditingStub: ProfileClient {
    var fails = true
    var saves = 0
    var removed = false
    var delay = false
    let profile = AccountProfile(
        userID: "owner", email: "owner@example.test",
        details: PersonalDetails(firstName: "First", lastName: "Last"), avatarURL: nil, hasAvatar: true)

    func validate(_ details: PersonalDetails) -> String? {
        details.firstName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "profile.namesRequired" : nil
    }

    func fetch() async throws -> ProfileLoadResult { ProfileLoadResult(profile: profile) }

    func save(details: PersonalDetails, photo: Data?, removePhoto: Bool) async throws -> ProfileLoadResult {
        saves += 1
        removed = removePhoto
        if delay { try await Task.sleep(for: .seconds(60)) }

        if fails { return ProfileLoadResult(errorKey: "auth.network") }

        return ProfileLoadResult(
            profile: AccountProfile(
                userID: profile.userID, email: profile.email, details: details,
                avatarURL: nil, hasAvatar: !removePhoto))
    }
}

@main
struct ProfileEditingTests {
    @MainActor
    static func main() async {
        let client = ProfileEditingStub()
        let editor = ProfileEditViewModel(profile: client.profile, generation: UUID(), client: client)

        editor.changeDetails(PersonalDetails(firstName: "Анна-Мария", lastName: "O’Connor"))
        editor.changePhoto(nil)
        editor.save()
        editor.save()
        try? await Task.sleep(for: .milliseconds(50))

        precondition(client.saves == 1 && client.removed)
        precondition(editor.saved == nil && editor.error == "auth.network")
        precondition(editor.details.firstName == "Анна-Мария" && editor.removesPhoto)

        client.fails = false
        editor.save()
        try? await Task.sleep(for: .milliseconds(50))

        precondition(editor.saved?.details == editor.details && editor.saved?.hasAvatar == false)
        precondition(editor.saved?.email == client.profile.email)

        client.delay = true
        let canceled = ProfileEditViewModel(profile: client.profile, generation: UUID(), client: client)
        canceled.changeDetails(PersonalDetails(firstName: "Changed", lastName: "Last"))
        canceled.save()
        await Task.yield()
        canceled.cancel()
        try? await Task.sleep(for: .milliseconds(50))

        precondition(canceled.saved == nil)
        print("PASS: draft preservation, retry, duplicate suppression, removal, immutable email and cancellation")
    }
}
