import SwiftUI

/// Presentation-only news composition; image loading and article policy belong to its caller.
struct AppNewsPreview<Thumbnail: View>: View {
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    let title: String
    var metadata: String?
    var isCompact = false
    var showsImage = true
    /// Reserves three headline and two metadata lines, except at accessibility text sizes.
    var usesUniformCaptionHeight = false
    /// Reserves space for caller-owned trailing controls without covering the caption.
    var captionTrailingInset: CGFloat = 0
    @ViewBuilder var thumbnail: Thumbnail

    var body: some View {
        Group {
            if isCompact {
                HStack(alignment: .top, spacing: 12) {
                    if showsImage {
                        thumbnail.frame(width: 64, height: 64).clipShape(.rect(cornerRadius: 8))
                    }
                    caption
                }
                .padding(16)
            } else {
                VStack(alignment: .leading, spacing: 0) {
                    if showsImage {
                        thumbnail.frame(height: 160).clipped()
                    }
                    caption.padding(16)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(uiColor: .secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 12))
        .clipShape(.rect(cornerRadius: 12))
    }

    private var caption: some View {
        VStack(alignment: .leading, spacing: 4) {
            if usesUniformCaptionHeight && !dynamicTypeSize.isAccessibilitySize {
                Text(title).font(.headline).foregroundStyle(.primary)
                    .lineLimit(3, reservesSpace: true)
                if let metadata, !metadata.isEmpty {
                    Text(metadata).font(.caption).foregroundStyle(.secondary)
                        .lineLimit(2, reservesSpace: true)
                } else {
                    // Empty Text drops a reserved line; blank lines retain the caption's native metrics.
                    Text(verbatim: "\n").font(.caption)
                        .lineLimit(2, reservesSpace: true)
                        .hidden().accessibilityHidden(true)
                }
            } else {
                Text(title).font(.headline).foregroundStyle(.primary)
                if let metadata, !metadata.isEmpty {
                    Text(metadata).font(.caption).foregroundStyle(.secondary)
                }
            }
        }
        .multilineTextAlignment(.leading)
        .padding(.trailing, captionTrailingInset)
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}
