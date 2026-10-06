import AppKit
import Nuke
import SwiftUI

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
        let request = avatar().request!
        let pipeline = ProfileAvatar.pipeline
        let image = NSImage(size: NSSize(width: 1024, height: 1024))
        guard
            let bitmap = NSBitmapImageRep(
                bitmapDataPlanes: nil, pixelsWide: 1024, pixelsHigh: 1024,
                bitsPerSample: 8, samplesPerPixel: 4, hasAlpha: true, isPlanar: false,
                colorSpaceName: .deviceRGB, bytesPerRow: 4096, bitsPerPixel: 32)
        else { preconditionFailure("Fixture bitmap allocation failed") }

        image.addRepresentation(bitmap)
        pipeline.cache[request] = ImageContainer(image: image)

        // The actual pipeline must return the cached image even when the signed URL changes.
        let rotated = avatar(token: "rotated").request!
        precondition(pipeline.cache[rotated] != nil)
        let response = try await pipeline.image(for: rotated)

        precondition(response === image)
        precondition(pipeline.cache[avatar(path: "replacement.jpg").request!] == nil)
        precondition(pipeline.cache[avatar(owner: "other").request!] == nil)
        precondition(pipeline.cache[avatar(session: UUID()).request!] == nil)
        precondition(pipeline.configuration.dataCache == nil)

        pipeline.cache.removeAll()
        print("PASS: cached image survives token rotation; replacement, account and session are isolated")
    }
}
