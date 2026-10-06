#if DEBUG
    import Foundation

    @MainActor
    final class ProfileUITestClient: ProfileClient {
        private var fetchAttempts = 0
        private var profile = AccountProfile(
            userID: "fixture", email: "reader@example.test",
            details: PersonalDetails(
                firstName: "Александр", lastName: "Иванов",
                middleName: "Сергеевич"), avatarURL: nil)

        init() {
            if ProcessInfo.processInfo.arguments.contains("--profile-ui-long-name") {
                profile = AccountProfile(
                    userID: "fixture", email: "reader.with.a.long.address@example.test",
                    details: PersonalDetails(
                        firstName: "Александра-Мария", lastName: "Константинопольская",
                        middleName: "Александровна"), avatarURL: nil)
            }
        }

        func validate(_ details: PersonalDetails) -> String? {
            SharedProfileClient.shared(details).isValid() ? nil : "profile.namesRequired"
        }

        func register(email: String, details: PersonalDetails) {
            profile = AccountProfile(userID: "fixture", email: email, details: details, avatarURL: nil)
        }

        func fetch() async throws -> ProfileLoadResult {
            fetchAttempts += 1
            let arguments = ProcessInfo.processInfo.arguments
            let delay = arguments.contains("--profile-ui-slow-fetch") ? 15_000 : 100

            try await Task.sleep(for: .milliseconds(delay))

            if arguments.contains("--profile-ui-fetch-error-once"), fetchAttempts == 1 {
                return ProfileLoadResult(errorKey: "auth.network")
            }

            return ProfileLoadResult(profile: profile)
        }

        func save(details: PersonalDetails, photo: Data?, removePhoto: Bool) async throws -> ProfileLoadResult {
            try await Task.sleep(for: .milliseconds(150))

            if let key = validate(details) { return ProfileLoadResult(errorKey: key) }
            if ProcessInfo.processInfo.arguments.contains("--profile-ui-save-error") {
                return ProfileLoadResult(errorKey: "auth.network")
            }

            profile = AccountProfile(userID: profile.userID, email: profile.email, details: details, avatarURL: nil)

            return ProfileLoadResult(profile: profile)
        }
    }
#endif
