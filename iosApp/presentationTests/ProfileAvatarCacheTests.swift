import Nuke
import SwiftUI
import UIKit

@main
struct ProfileAvatarCacheTests {
    @MainActor
    static func main() async throws {
        let generation = UUID()

        func avatar(
            path: String = "first.jpg", token: String = "first", owner: String = "owner",
            session: UUID? = nil
        ) -> ProfileAvatar {
            ProfileAvatar(
                profile: AccountProfile(
                    userID: owner, email: "reader@example.test", details: PersonalDetails(),
                    avatarURL: URL(string: "https://example.test/avatars/owner/\(path)?token=\(token)")),
                generation: session ?? generation)
        }

        // Every request below uses a fixed, valid fixture URL.
        // swiftlint:disable:next force_unwrapping -- Every request uses a fixed valid fixture URL.
        let request = avatar().request!
        let pipeline = ProfileAvatar.pipeline
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = true
        format.preferredRange = .standard
        let image = UIGraphicsImageRenderer(size: CGSize(width: 1024, height: 1024), format: format).image { context in
            UIColor.blue.setFill()
            context.fill(CGRect(x: 0, y: 0, width: 1024, height: 1024))
        }
        precondition(image.cgImage?.width == 1024 && image.cgImage?.height == 1024)
        pipeline.cache[request] = ImageContainer(image: image)

        // The actual pipeline must return the cached image even when the signed URL changes.
        // swiftlint:disable:next force_unwrapping -- The fixed fixture URL remains valid after token rotation.
        let rotated = avatar(token: "rotated").request!
        precondition(pipeline.cache[rotated] != nil)
        let response = try await pipeline.image(for: rotated)

        precondition(response === image)
        // swiftlint:disable:next force_unwrapping -- Replacement uses the same valid fixture URL shape.
        precondition(pipeline.cache[avatar(path: "replacement.jpg").request!] == nil)
        // swiftlint:disable:next force_unwrapping -- A different owner still produces a valid fixture URL.
        precondition(pipeline.cache[avatar(owner: "other").request!] == nil)
        // swiftlint:disable:next force_unwrapping -- Session identity does not affect fixture URL validity.
        precondition(pipeline.cache[avatar(session: UUID()).request!] == nil)
        precondition(pipeline.configuration.dataCache == nil)

        pipeline.cache.removeAll()
        print("PASS: cached image survives token rotation; replacement, account and session are isolated")
    }
}
