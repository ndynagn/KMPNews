#if DEBUG
    import UIKit

    /// Local imagery shared by deterministic feed and search UI fixtures.
    @MainActor
    enum ArticleUITestImage {
        static func make() -> URL? {
            let renderer = UIGraphicsImageRenderer(size: CGSize(width: 600, height: 320))
            let image = renderer.image { context in
                UIColor.systemTeal.setFill()
                context.fill(CGRect(x: 0, y: 0, width: 600, height: 320))
                UIImage(systemName: "newspaper")?.withTintColor(.white, renderingMode: .alwaysOriginal)
                    .draw(in: CGRect(x: 235, y: 95, width: 130, height: 130))
            }
            let url = FileManager.default.temporaryDirectory.appendingPathComponent("article-ui-fixture.png")
            guard let data = image.pngData() else { return nil }
            do {
                try data.write(to: url, options: .atomic)
                return url
            } catch {
                return nil
            }
        }
    }
#endif
