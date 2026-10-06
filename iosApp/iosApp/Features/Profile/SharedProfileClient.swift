import Foundation
import SharedLogic

@MainActor
final class SharedProfileClient: ProfileClient {
    private let repository: any ProfileRepository

    init(repository: any ProfileRepository) { self.repository = repository }

    func validate(_ details: PersonalDetails) -> String? {
        Self.shared(details).isValid() ? nil : "profile.namesRequired"
    }

    func fetch() async throws -> ProfileLoadResult { Self.native(try await repository.fetch()) }

    func save(details: PersonalDetails, photo: Data?, removePhoto: Bool) async throws -> ProfileLoadResult {
        let bytes = photo.map { data in
            let result = KotlinByteArray(size: Int32(data.count))
            for (index, byte) in data.enumerated() { result.set(index: Int32(index), value: Int8(bitPattern: byte)) }

            return result
        }

        return Self.native(
            try await repository.save(details: Self.shared(details), avatarJpeg: bytes, removeAvatar: removePhoto))
    }

    static func shared(_ details: PersonalDetails) -> ProfileDetails {
        ProfileDetails(firstName: details.firstName, lastName: details.lastName, middleName: details.middleName)
    }

    private static func native(_ result: ProfileResult) -> ProfileLoadResult {
        if let failure = result.failure {
            let key: String
            switch failure {
            case .invalidName: key = "profile.namesRequired"
            case .invalidPhoto: key = "profile.photoInvalid"
            case .network: key = "auth.network"
            case .sessionExpired: key = "auth.session_expired"
            case .rateLimited: key = "auth.rate_limited"
            default: key = "auth.service"
            }

            return ProfileLoadResult(errorKey: key)
        }

        guard let value = result.profile else { return ProfileLoadResult(errorKey: "auth.service") }

        return ProfileLoadResult(
            profile: AccountProfile(
                userID: value.userId, email: value.email,
                details: PersonalDetails(
                    firstName: value.details.firstName, lastName: value.details.lastName,
                    middleName: value.details.middleName ?? ""),
                avatarURL: value.avatarUrl.flatMap(URL.init(string:)), hasAvatar: value.hasAvatar))
    }
}
