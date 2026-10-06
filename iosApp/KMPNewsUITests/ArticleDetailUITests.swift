import XCTest

final class ArticleDetailUITests: XCTestCase {
    @MainActor func testIndependentSectionsAndSearchReturn() {
        let app = launch(extra: ["--article-ui-long"])
        if #available(iOS 26, *), UIDevice.current.userInterfaceIdiom == .phone {
            let search = app.buttons["Search"].firstMatch.frame
            let profile = app.buttons["Profile"].firstMatch.frame
            XCTAssertGreaterThanOrEqual(search.minX - profile.maxX, 8)
            capture("search-separated-tab")
        }
        let card = app.buttons["article.open.fixture-0"]
        let originalY = card.frame.minY
        card.doubleTap()
        assertDetail(app)
        let headline = app.staticTexts["article.title"].label
        app.scrollViews["article.detail"].swipeUp()
        let summaryY = app.staticTexts["article.summary"].frame.minY
        tapSection("Search", app)
        let field = app.searchFields.firstMatch
        XCTAssertTrue(field.waitForExistence(timeout: 5))
        field.tap()
        field.typeText("space")
        XCTAssertTrue(app.buttons["article.open.search-0"].waitForExistence(timeout: 5))
        app.buttons["article.open.search-0"].tap()
        assertDetail(app)
        XCTAssertFalse(app.searchFields.firstMatch.exists)
        XCTAssertFalse(app.keyboards.firstMatch.exists)
        let searchHeadline = app.staticTexts["article.title"].label
        tapSection("News", app)
        assertDetail(app)
        XCTAssertEqual(app.staticTexts["article.title"].label, headline)
        XCTAssertEqual(app.staticTexts["article.summary"].frame.minY, summaryY, accuracy: 4)
        back(app).tap()
        XCTAssertTrue(card.waitForExistence(timeout: 5))
        XCTAssertEqual(card.frame.minY, originalY, accuracy: 4)
        tapSection("Search", app)
        assertDetail(app)
        XCTAssertEqual(app.staticTexts["article.title"].label, searchHeadline)
        XCTAssertFalse(app.keyboards.firstMatch.exists)
        back(app).tap()
        XCTAssertTrue(field.waitForExistence(timeout: 5))
        XCTAssertEqual(field.value as? String, "space")
        XCTAssertFalse(app.keyboards.firstMatch.exists)
        capture("classic-search-return")
    }

    @MainActor func testMenuAndMissingFields() throws {
        let app = launch()
        app.buttons["article.open.fixture-0"].tap()
        assertDetail(app)
        XCTAssertTrue(app.staticTexts["article.summary"].exists)
        XCTAssertFalse(app.buttons["article.readSource"].exists)
        let sidebar = app.buttons["ToggleSideBar"].firstMatch
        if UIDevice.current.userInterfaceIdiom == .pad, sidebar.exists {
            sidebar.tap()
            assertDetail(app)
            let content = app.scrollViews["article.detail"].frame
            let title = app.staticTexts["article.title"].frame
            XCTAssertGreaterThanOrEqual(title.minX, content.minX + 20)
            XCTAssertLessThanOrEqual(title.maxX, content.maxX - 20)
            capture("classic-ipad-sidebar")
            app.scrollViews["article.detail"].coordinate(withNormalizedOffset: CGVector(dx: 0.9, dy: 0.5)).tap()
        }
        let title = app.staticTexts["article.title"]
        let content = app.scrollViews["article.detail"].frame
        XCTAssertGreaterThanOrEqual(title.frame.minX, content.minX + 20)
        XCTAssertLessThanOrEqual(title.frame.maxX, content.maxX - 20)
        try app.performAccessibilityAudit(for: [.sufficientElementDescription, .trait])
        capture("classic-detail")
        app.buttons["article.actions"].tap()
        XCTAssertTrue(app.buttons["Open source"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["Share"].exists)
        capture("classic-detail-menu")
        title.tap()
        back(app).tap()
        let missing = app.buttons["article.open.fixture-2"]
        for _ in 0..<5 where !missing.isHittable { app.scrollViews["feed.list"].swipeUp() }
        missing.tap()
        assertDetail(app)
        XCTAssertTrue(app.staticTexts["article.noSummary"].exists)
        XCTAssertFalse(app.buttons["article.actions"].exists)
        XCTAssertTrue(app.buttons["article.save"].exists)
        capture("classic-detail-missing-fields")
    }

    @MainActor func testGuestAuthenticationAndFavoriteRemoval() {
        let app = launch()
        app.buttons["article.open.fixture-0"].tap()
        assertDetail(app)
        app.buttons["article.save"].tap()
        XCTAssertTrue(app.buttons["favorites.close"].waitForExistence(timeout: 5))
        app.buttons["favorites.close"].tap()
        assertDetail(app)
        app.buttons["article.save"].tap()
        app.buttons["favorites.login"].tap()
        XCTAssertTrue(app.textFields["auth.email"].waitForExistence(timeout: 5))
        app.buttons["auth.close"].tap()
        assertDetail(app)
        app.buttons["article.save"].tap()
        app.buttons["favorites.login"].tap()
        signIn(app)
        XCTAssertTrue(savedStar(app, saved: true).waitForExistence(timeout: 10))
        capture("classic-saved-after-auth")
        tapSection("Favorites", app)
        XCTAssertTrue(app.buttons["article.open.fixture-0"].waitForExistence(timeout: 5))
        app.buttons["article.open.fixture-0"].tap()
        assertDetail(app)
        app.buttons["article.save"].tap()
        XCTAssertTrue(savedStar(app, saved: false).waitForExistence(timeout: 5))
        back(app).tap()
        XCTAssertTrue(app.buttons["article.open.fixture-0"].waitForExistence(timeout: 5))
        XCTAssertEqual(app.buttons["favorites.save.fixture-0"].label, "Add to favorites")
        tapSection("News", app)
        assertDetail(app)
        XCTAssertTrue(savedStar(app, saved: false).exists)
    }

    @MainActor func testSaveErrorRetry() {
        let app = launch(extra: ["--auth-ui-signed-in", "--favorites-ui-error"])
        app.buttons["article.open.fixture-0"].tap()
        assertDetail(app)
        app.buttons["article.save"].tap()
        XCTAssertTrue(app.alerts.firstMatch.waitForExistence(timeout: 5))
        app.alerts.buttons["Retry"].tap()
        XCTAssertTrue(savedStar(app, saved: true).waitForExistence(timeout: 5))
        XCTAssertTrue(back(app).exists)
    }

    @MainActor func testLogoutClearsFavoritesReaderAndKeepsNews() {
        let app = launch(extra: ["--auth-ui-signed-in"])
        app.buttons["article.open.fixture-0"].tap()
        assertDetail(app)
        app.buttons["article.save"].tap()
        XCTAssertTrue(savedStar(app, saved: true).waitForExistence(timeout: 5))
        tapSection("Favorites", app)
        XCTAssertTrue(app.buttons["article.open.fixture-0"].waitForExistence(timeout: 5))
        app.buttons["article.open.fixture-0"].tap()
        assertDetail(app)
        tapSection("Profile", app)
        app.buttons["profile.logout"].tap()
        XCTAssertTrue(app.buttons["profile.login"].waitForExistence(timeout: 5))
        tapSection("Favorites", app)
        XCTAssertTrue(app.buttons["favorites.guest.login"].waitForExistence(timeout: 5))
        XCTAssertFalse(app.buttons["article.save"].exists)
        tapSection("News", app)
        assertDetail(app)
        XCTAssertTrue(savedStar(app, saved: false).exists)
    }

    @MainActor func testFailedImageAndLongTitle() {
        let app = launch(extra: ["--article-ui-long-title"])
        let card = app.buttons["article.open.fixture-1"]
        for _ in 0..<4 where !card.isHittable { app.scrollViews["feed.list"].swipeUp() }
        card.tap()
        assertDetail(app)
        let title = app.staticTexts["article.title"]
        XCTAssertTrue(title.label.hasSuffix("without truncation."))
        XCTAssertGreaterThanOrEqual(title.frame.minX, 20)
        XCTAssertLessThanOrEqual(title.frame.maxX, app.frame.maxX - 20)
        capture("classic-failed-image-long-title")
    }

    @MainActor func testSafariAndSharingReturnToSameArticle() {
        let app = launch(extra: ["--article-ui-long"])
        app.buttons["article.open.fixture-0"].tap()
        assertDetail(app)
        let title = app.staticTexts["article.title"].label
        app.scrollViews["article.detail"].swipeUp()
        let readingY = app.staticTexts["article.summary"].frame.minY
        for _ in 0..<2 {
            app.buttons["article.actions"].tap()
            app.buttons["Open source"].tap()
            let close = systemClose(app)
            XCTAssertTrue(close.waitForExistence(timeout: 10))
            capture("classic-source")
            close.tap()
            assertDetail(app)
            XCTAssertEqual(app.staticTexts["article.title"].label, title)
            XCTAssertEqual(app.staticTexts["article.summary"].frame.minY, readingY, accuracy: 4)
        }
        for _ in 0..<2 {
            app.buttons["article.actions"].tap()
            app.buttons["Share"].tap()
            let copy = app.descendants(matching: .any)
                .matching(NSPredicate(format: "label IN %@", ["Copy", "Скопировать"])).firstMatch
            XCTAssertTrue(copy.waitForExistence(timeout: 10))
            capture("classic-share")
            app.coordinate(withNormalizedOffset: CGVector(dx: 0.03, dy: 0.9)).tap()
            assertDetail(app)
            let enabled = NSPredicate(format: "enabled == true")
            expectation(for: enabled, evaluatedWith: app.buttons["article.save"])
            waitForExpectations(timeout: 5)
            XCTAssertEqual(app.staticTexts["article.title"].label, title)
            XCTAssertEqual(app.staticTexts["article.summary"].frame.minY, readingY, accuracy: 4)
        }
    }

    @MainActor func testCancelledAndCompletedEdgeReturn() {
        let app = launch()
        app.buttons["article.open.fixture-0"].tap()
        assertDetail(app)
        let edge = app.coordinate(withNormalizedOffset: CGVector(dx: 0.001, dy: 0.5))
        edge.press(
            forDuration: 0.1, thenDragTo: edge.withOffset(CGVector(dx: 25, dy: 0)),
            withVelocity: .slow, thenHoldForDuration: 0.5)
        XCTAssertTrue(back(app).exists)
        capture("classic-cancelled-back")
        edge.press(
            forDuration: 0.1,
            thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.9, dy: 0.5)),
            withVelocity: .slow, thenHoldForDuration: 0.2)
        XCTAssertTrue(app.buttons["article.open.fixture-0"].waitForExistence(timeout: 5))
        XCTAssertFalse(app.buttons["article.save"].exists)
    }

    @MainActor func testLargeTextAndRotation() {
        let app = launch(extra: ["-UIPreferredContentSizeCategoryName", "UICTContentSizeCategoryAccessibilityXXXL"])
        app.buttons["article.open.fixture-0"].tap()
        assertDetail(app)
        XCTAssertTrue(app.buttons["article.save"].isHittable)
        XCTAssertTrue(app.buttons["article.actions"].isHittable)
        capture("classic-large-text")
        XCUIDevice.shared.orientation = .landscapeLeft
        XCTAssertTrue(back(app).waitForExistence(timeout: 5))
        capture("classic-landscape")
        XCUIDevice.shared.orientation = .portrait
    }

    @MainActor func testImagePullReturnsToRestingLayout() {
        let app = launch(extra: ["--article-ui-long"])
        app.buttons["article.open.fixture-0"].tap()
        assertDetail(app)
        let title = app.staticTexts["article.title"]
        let originalY = title.frame.minY
        let content = app.scrollViews["article.detail"]
        content.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.35)).press(
            forDuration: 0.1,
            thenDragTo: content.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.8)),
            withVelocity: .slow, thenHoldForDuration: 2)
        XCTAssertEqual(title.frame.minY, originalY, accuracy: 4)
        XCTAssertTrue(app.buttons["article.actions"].isHittable)
        capture("classic-image-after-pull")
    }

    @MainActor func testWindowResizeKeepsReader() throws {
        guard UIDevice.current.userInterfaceIdiom == .pad else { throw XCTSkip("iPad window resizing") }
        let app = launch()
        app.buttons["article.open.fixture-0"].tap()
        assertDetail(app)
        let originalWidth = app.scrollViews["article.detail"].frame.width
        let corner = CGVector(dx: 0.992, dy: 0.994)
        let desktop = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        defer {
            app.scrollViews["article.detail"].coordinate(withNormalizedOffset: corner).press(
                forDuration: 0.2, thenDragTo: desktop.coordinate(withNormalizedOffset: corner),
                withVelocity: .slow, thenHoldForDuration: 0.2)
        }
        app.coordinate(withNormalizedOffset: corner).press(
            forDuration: 0.2,
            thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.45, dy: 0.72)),
            withVelocity: .slow, thenHoldForDuration: 0.2)
        let narrower = NSPredicate { _, _ in
            app.scrollViews["article.detail"].frame.width < originalWidth - 100
        }
        expectation(for: narrower, evaluatedWith: app)
        waitForExpectations(timeout: 5)
        assertDetail(app)
        let content = app.scrollViews["article.detail"].frame
        let title = app.staticTexts["article.title"].frame
        XCTAssertGreaterThanOrEqual(title.minX, content.minX + 20)
        XCTAssertLessThanOrEqual(title.maxX, content.maxX - 20)
        XCTAssertTrue(app.buttons["article.actions"].isHittable)
        capture("classic-ipad-narrow-window")
    }

    @MainActor private func launch(extra: [String] = []) -> XCUIApplication {
        XCUIDevice.shared.orientation = .portrait
        let app = XCUIApplication()
        app.launchArguments =
            [
                "--feed-ui-fixture", "--search-ui-fixture", "--auth-ui-fixture", "--article-ui-fixture",
                "-AppleLanguages", "(en)",
            ] + extra
        app.launch()
        XCTAssertTrue(app.buttons["article.open.fixture-0"].waitForExistence(timeout: 10))
        return app
    }

    @MainActor private func assertDetail(_ app: XCUIApplication) {
        XCTAssertTrue(app.buttons["article.save"].waitForExistence(timeout: 5))
        XCTAssertTrue(back(app).exists)
    }

    @MainActor private func back(_ app: XCUIApplication) -> XCUIElement {
        app.navigationBars.buttons["BackButton"].firstMatch
    }

    @MainActor private func tapSection(_ name: String, _ app: XCUIApplication) {
        let notice = app.staticTexts["News added to favorites"]
        if notice.exists { XCTAssertTrue(notice.waitForNonExistence(timeout: 5)) }
        if app.tabBars.buttons[name].exists {
            app.tabBars.buttons[name].tap()
        } else if app.cells[name].firstMatch.exists {
            app.cells[name].firstMatch.tap()
        } else {
            let items = app.buttons.matching(identifier: name).allElementsBoundByIndex
            (items.last(where: { $0.isHittable }) ?? app.buttons[name].firstMatch).tap()
        }
    }

    @MainActor private func savedStar(_ app: XCUIApplication, saved: Bool) -> XCUIElement {
        app.buttons.matching(identifier: "article.save")
            .matching(NSPredicate(format: "label == %@", saved ? "Remove from favorites" : "Add to favorites"))
            .firstMatch
    }

    @MainActor private func systemClose(_ app: XCUIApplication) -> XCUIElement {
        app.buttons.matching(NSPredicate(format: "label IN %@", ["Done", "Close", "Готово", "Закрыть"])).firstMatch
    }

    @MainActor private func signIn(_ app: XCUIApplication) {
        let email = app.textFields["auth.email"]
        XCTAssertTrue(email.waitForExistence(timeout: 5))
        email.tap()
        email.typeText("reader@example.test")
        app.secureTextFields["auth.password"].tap()
        app.secureTextFields["auth.password"].typeText("Fixture123!")
        app.buttons["auth.submit"].tap()
    }
}
