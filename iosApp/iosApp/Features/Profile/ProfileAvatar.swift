import Nuke
import NukeUI
import SwiftUI

struct ProfileAvatar: View {
    let profile: AccountProfile
    let generation: UUID
    var size: CGFloat = 112

    // Private images stay in a bounded, memory-only cache, isolated by login generation.
    static let pipeline: ImagePipeline = {
        let session = URLSessionConfiguration.ephemeral
        session.urlCache = nil

        var configuration = ImagePipeline.Configuration(dataLoader: DataLoader(configuration: session))
        configuration.dataCache = nil

        let cache = ImageCache(costLimit: 16 * 1024 * 1024, countLimit: 8)
        // A decoded 1024-square avatar takes about 4 MiB, above Nuke's default per-entry allowance.
        cache.entryCostLimit = 0.5
        configuration.imageCache = cache

        return ImagePipeline(configuration: configuration)
    }()

    var body: some View {
        LazyImage(request: request) { state in
            if let image = state.image {
                image.resizable().scaledToFill()
            } else {
                ZStack {
                    Color.accentColor.opacity(0.12)
                    if initials.isEmpty {
                        Image(systemName: "person.fill").font(.system(size: size * 0.38))
                    } else {
                        Text(initials).font(.system(size: size * 0.34, weight: .medium))
                    }
                }
                .foregroundStyle(Color.accentColor)
            }
        }
        .pipeline(Self.pipeline)
        .frame(width: size, height: size)
        .clipShape(Circle())
        .accessibilityHidden(true)
    }

    var request: ImageRequest? {
        guard let url = profile.avatarURL else { return nil }

        var identity = URLComponents(url: url, resolvingAgainstBaseURL: false)
        identity?.query = nil
        identity?.fragment = nil

        // Storage uses a new UUID path for each replacement; signed tokens may rotate independently.
        let key = "\(generation)/\(profile.userID)/\(identity?.string ?? url.absoluteString)"

        return ImageRequest(url: url, userInfo: [.imageIdKey: key])
    }

    private var initials: String {
        [profile.details.firstName, profile.details.lastName]
            .compactMap { $0.first.map(String.init) }.joined().uppercased()
    }
}
