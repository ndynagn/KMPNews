import SwiftUI

/// Presentation-only news composition; image loading and article policy belong to its caller.
struct AppNewsPreview: View {
    let title: String
    let source: String
    var compact = false
    var imageUnavailable = false
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            if compact {
                HStack(alignment: .top, spacing: 12) {
                    thumbnail.frame(width: 64, height: 64).clipShape(.rect(cornerRadius: 8))
                    caption
                }
            } else {
                VStack(alignment: .leading, spacing: 12) {
                    thumbnail.frame(height: 160).clipShape(.rect(cornerRadius: 12))
                    caption
                }
            }
        }
        .buttonStyle(.plain)
    }

    private var caption: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title).font(.headline).foregroundStyle(.primary)
            Text(source).font(.caption).foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var thumbnail: some View {
        Rectangle().fill(Color(uiColor: .tertiarySystemFill))
            .overlay {
                Image(systemName: imageUnavailable ? "photo.badge.exclamationmark" : "photo")
                    .font(.largeTitle).foregroundStyle(.secondary)
            }
            .accessibilityLabel(Text(imageUnavailable ? "kit.imageUnavailable" : "kit.sampleImage"))
    }
}
