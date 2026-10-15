import Foundation

enum ArticleGridLayout {
    static func columnCount(width: CGFloat, minimumCardWidth: CGFloat, accessibilitySize: Bool) -> Int {
        guard !accessibilitySize else { return 1 }
        let contentWidth = max(0, width - 32)
        return max(1, min(3, Int((contentWidth + 16) / (minimumCardWidth + 16))))
    }
}
