import Foundation

@main
struct ArticleGridLayoutTests {
    static func main() {
        let widths: [(CGFloat, Int)] = [
            (0, 1), (320, 1), (390, 1), (687, 1), (688, 2), (834, 2), (1023, 2), (1024, 3), (2000, 3),
        ]
        for (width, expected) in widths {
            precondition(
                ArticleGridLayout.columnCount(width: width, minimumCardWidth: 320, accessibilitySize: false)
                    == expected,
                "Column count must include outer padding and gaps at width \(width)")
            precondition(
                ArticleGridLayout.columnCount(width: width, minimumCardWidth: 320, accessibilitySize: true) == 1,
                "Accessibility text must use one column at width \(width)")
        }
        precondition(ArticleGridLayout.columnCount(width: 834, minimumCardWidth: 420, accessibilitySize: false) == 1)
        print("PASS: grid width thresholds, three-column cap, scaled minimum width and accessibility layout")
    }
}
