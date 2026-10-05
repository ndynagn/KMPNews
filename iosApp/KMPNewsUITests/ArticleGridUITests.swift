import XCTest

final class ArticleGridUITests: XCTestCase {
    @MainActor func testUniformHeightsWithMissingImageAndMetadata() {
        assertVariedCardHeights(extra: [])
    }

    @MainActor func testUniformHeightsWithLargerText() {
        assertVariedCardHeights(extra: ["-UIPreferredContentSizeCategoryName", "UICTContentSizeCategoryXXXL"])
    }

    @MainActor func testIPadNarrowWindowKeepsUniformCards() throws {
        guard UIDevice.current.userInterfaceIdiom == .pad else { throw XCTSkip("iPad window resizing") }
        let app = launch(extra: ["--grid-ui-varied", "--article-ui-long-title"])
        let originalWidth = app.scrollViews["feed.list"].frame.width
        app.coordinate(withNormalizedOffset: CGVector(dx: 0.992, dy: 0.994)).press(
            forDuration: 0.2,
            thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.45, dy: 0.72)),
            withVelocity: .slow, thenHoldForDuration: 0.2)
        expectation(
            for: NSPredicate { _, _ in app.scrollViews["feed.list"].frame.width < originalWidth - 100 },
            evaluatedWith: app)
        waitForExpectations(timeout: 5)
        let first = app.buttons["article.open.fixture-0"].frame
        let second = app.buttons["article.open.fixture-1"].frame
        XCTAssertEqual(first.height, second.height, accuracy: 1)
        XCTAssertEqual(first.minX, second.minX, accuracy: 1)
        XCTAssertLessThanOrEqual(first.maxX, app.scrollViews["feed.list"].frame.maxX - 15)
        capture("grid-uniform-narrow-window")
    }

    @MainActor func testAllCollectionsUseAdaptiveColumns() {
        let app = launch()
        assertColumns(app, first: "fixture-0", second: "fixture-1")
        XCTAssertTrue(app.buttons["article.open.fixture-0"].exists)
        capture("grid-news-portrait")

        for id in ["fixture-0", "fixture-1"] {
            app.buttons["favorites.save.\(id)"].tap()
            let saved = app.buttons.matching(identifier: "favorites.save.\(id)")
                .matching(NSPredicate(format: "label == %@", "Remove from favorites")).firstMatch
            XCTAssertTrue(saved.waitForExistence(timeout: 5))
        }
        selectSection("Favorites", in: app)
        XCTAssertTrue(app.buttons["favorites.save.fixture-1"].waitForExistence(timeout: 5))
        assertColumns(app, first: "fixture-1", second: "fixture-0")
        XCTAssertTrue(app.buttons["article.open.fixture-1"].exists)
        capture("grid-favorites-portrait")

        selectSection("Search", in: app)
        let field = app.searchFields.firstMatch
        XCTAssertTrue(field.waitForExistence(timeout: 5))
        field.tap()
        field.typeText("space\n")
        XCTAssertTrue(app.buttons["favorites.save.search-0"].waitForExistence(timeout: 5))
        assertColumns(app, first: "search-0", second: "search-1")
        XCTAssertTrue(app.buttons["article.open.search-0"].exists)
        XCTAssertTrue(app.buttons["article.open.search-1"].exists)
        capture("grid-search-portrait")

        XCUIDevice.shared.orientation = .landscapeLeft
        XCTAssertTrue(app.buttons["favorites.save.search-0"].waitForExistence(timeout: 5))
        XCTAssertEqual(field.value as? String, "space")
        capture("grid-search-landscape")
        XCUIDevice.shared.orientation = .portrait
    }

    @MainActor func testAccessibilityTextUsesOneColumn() {
        let app = launch(extra: [
            "-UIPreferredContentSizeCategoryName", "UICTContentSizeCategoryAccessibilityXXXL",
            "--grid-ui-varied", "--article-ui-long-title",
        ])
        let first = app.buttons["favorites.save.fixture-0"]
        let second = app.buttons["favorites.save.fixture-1"]
        XCTAssertEqual(first.frame.minX, second.frame.minX, accuracy: 2)
        XCTAssertGreaterThanOrEqual(first.frame.width, 44)
        XCTAssertGreaterThanOrEqual(first.frame.height, 44)
        capture("grid-news-accessibility-text")
        let shortHeight = app.buttons["article.open.fixture-0"].frame.height
        let longCard = app.buttons["article.open.fixture-1"]
        for _ in 0..<6 where !longCard.isHittable { app.scrollViews["feed.list"].swipeUp() }
        XCTAssertGreaterThan(longCard.frame.height, shortHeight + 50)
        capture("grid-accessibility-long-title")
    }

    @MainActor private func assertColumns(_ app: XCUIApplication, first: String, second: String) {
        let firstStar = app.buttons["favorites.save.\(first)"]
        let secondStar = app.buttons["favorites.save.\(second)"]
        XCTAssertEqual(
            app.buttons["article.open.\(first)"].frame.height,
            app.buttons["article.open.\(second)"].frame.height, accuracy: 1)
        if UIDevice.current.userInterfaceIdiom == .pad {
            XCTAssertGreaterThan(secondStar.frame.minX, firstStar.frame.minX + 100)
        } else {
            XCTAssertEqual(secondStar.frame.minX, firstStar.frame.minX, accuracy: 2)
        }
    }

    @MainActor private func assertVariedCardHeights(extra: [String]) {
        let app = launch(extra: extra + ["--grid-ui-varied", "--article-ui-long-title", "--article-ui-fixture"])
        let first = app.buttons["article.open.fixture-0"]
        let height = first.frame.height
        let width = first.frame.width
        XCTAssertGreaterThan(height, 160)
        capture("grid-uniform-cards")
        for index in 1...3 {
            let card = app.buttons["article.open.fixture-\(index)"]
            for _ in 0..<6 where !card.isHittable { app.scrollViews["feed.list"].swipeUp() }
            XCTAssertTrue(card.exists)
            XCTAssertEqual(card.frame.height, height, accuracy: 1)
            XCTAssertEqual(card.frame.width, width, accuracy: 1)
        }
        app.scrollViews["feed.list"].swipeDown()
        let longTitle = app.buttons["article.open.fixture-1"]
        for _ in 0..<6 where !longTitle.isHittable { app.scrollViews["feed.list"].swipeDown() }
        longTitle.tap()
        XCTAssertTrue(app.staticTexts["article.title"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["article.title"].label.hasSuffix("without truncation."))
    }

    @MainActor private func launch(extra: [String] = []) -> XCUIApplication {
        XCUIDevice.shared.orientation = .portrait
        let app = XCUIApplication()
        app.launchArguments =
            [
                "--feed-ui-fixture", "--search-ui-fixture", "--auth-ui-fixture", "--auth-ui-signed-in",
                "-AppleLanguages", "(en)",
            ] + extra
        app.launch()
        XCTAssertTrue(app.buttons["favorites.save.fixture-0"].waitForExistence(timeout: 10))
        return app
    }
}
