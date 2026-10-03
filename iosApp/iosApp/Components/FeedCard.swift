import NukeUI
import SwiftUI

struct FeedCard: View {
    let article: FeedArticle
    let isSaved: Bool
    let canSave: Bool
    let onSave: (() -> Void)?
    var onOpen: (() -> Void)? = nil

    var body: some View {
        Group {
            if let onOpen {
                Button(action: onOpen) { preview }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("article.open.\(article.id)")
            } else {
                preview
            }
        }
        .overlay(alignment: .topTrailing) {
            if let onSave {
                favoriteButton(action: onSave)
                    .accessibilityIdentifier("favorites.save.\(article.id)")
                    .padding(8)
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("feed.article.\(article.id)")
    }

    private var preview: some View {
        AppNewsPreview(
            title: article.title ?? String(localized: "feed.noTitle"), metadata: metadata,
            showsImage: article.imageURL != nil,
            captionTrailingInset: article.imageURL == nil && onSave != nil ? 50 : 0
        ) {
            if let imageURL = article.imageURL {
                LazyImage(url: imageURL) { state in
                    if let image = state.image {
                        image.resizable().scaledToFill()
                    } else {
                        AppImagePlaceholder(state: state.error == nil ? .loading : .unavailable)
                    }
                }
                .accessibilityHidden(true)
            }
        }
    }

    @ViewBuilder
    private func favoriteButton(action: @escaping () -> Void) -> some View {
        let button = Button(action: action) {
            Image(systemName: isSaved ? "star.fill" : "star")
                .font(.system(size: 20))
                .frame(width: 20, height: 20)
        }
        .foregroundStyle(Color.blue)
        .tint(.blue)
        .disabled(!canSave)
        .accessibilityLabel(isSaved ? Text("favorites.remove") : Text("favorites.save"))
        .accessibilityValue(isSaved ? Text("favorites.saved") : Text("favorites.notSaved"))

        if #available(iOS 26, *) {
            button
                .buttonStyle(.glass)
                .buttonBorderShape(.circle)
                .controlSize(.large)
        } else {
            button.buttonStyle(MaterialFavoriteButtonStyle())
        }
    }

    private var metadata: String {
        [article.source, article.publishedAt?.formatted(date: .numeric, time: .shortened)]
            .compactMap { $0 }.joined(separator: " · ")
    }
}

private struct MaterialFavoriteButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .frame(width: 44, height: 44)
            .background(.regularMaterial, in: Circle())
            .overlay {
                Circle()
                    .fill(.primary.opacity(configuration.isPressed ? 0.12 : 0))
                    .allowsHitTesting(false)
            }
            .contentShape(Circle())
    }
}
