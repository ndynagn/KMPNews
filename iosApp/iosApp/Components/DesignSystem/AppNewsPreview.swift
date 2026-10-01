import SwiftUI

/// Presentation-only news composition; image loading and article policy belong to its caller.
struct AppNewsPreview<Thumbnail: View>: View {
    let title: String
    var metadata: String?
    var compact = false
    var showsImage = true
    @ViewBuilder var thumbnail: Thumbnail

    var body: some View {
        Group {
            if compact {
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
            Text(title).font(.headline).foregroundStyle(.primary)
            if let metadata, !metadata.isEmpty {
                Text(metadata).font(.caption).foregroundStyle(.secondary)
            }
        }
        .multilineTextAlignment(.leading)
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}
