import XCTest

final class SearchUITests: XCTestCase {
    @MainActor
    func testCenteredSearchAndGuestStates() {
        let app = launch()
        app.buttons["Search"].firstMatch.tap()
        let field = app.searchFields.firstMatch
        XCTAssertTrue(field.waitForExistence(timeout: 5))
        assertCentered(app, includesSearchField: true)
        capture("centered-search-prompt")
        field.tap()
        field.typeText("slow\n")
        XCTAssertTrue(app.activityIndicators.firstMatch.waitForExistence(timeout: 3))
        assertCentered(app, includesSearchField: true)
        capture("centered-search-loading")
        XCTAssertTrue(app.buttons["favorites.save.search-0"].waitForExistence(timeout: 15))
        field.tap()
        field.buttons.firstMatch.tap()
        field.tap()
        field.typeText("empty\n")
        XCTAssertTrue(app.staticTexts["No results"].waitForExistence(timeout: 5))
        assertCentered(app, includesSearchField: true)
        capture("centered-search-empty")
        field.tap()
        field.buttons.firstMatch.tap()
        field.tap()
        field.typeText("error\n")
        XCTAssertTrue(app.buttons["search.retry"].waitForExistence(timeout: 5))
        assertCentered(app, includesSearchField: true)
        let explanation = app.staticTexts["Too many requests. Try again later."]
        XCTAssertTrue(explanation.exists)
        XCTAssertLessThanOrEqual(app.buttons["search.retry"].frame.minY - explanation.frame.maxY, 24)
        capture("centered-search-error")
        close(app)
        app.buttons["Favorites"].firstMatch.tap()
        XCTAssertTrue(app.buttons["favorites.guest.register"].waitForExistence(timeout: 5))
        capture("favorites-guest-bottom-actions")
    }

    @MainActor
    func testCardsSearchClearReturnAndAuthentication() {
        let app = launch()
        app.buttons["Profile"].firstMatch.tap()
        app.buttons["Search"].firstMatch.tap()
        let field = app.searchFields.firstMatch
        XCTAssertTrue(field.waitForExistence(timeout: 5))
        if UIDevice.current.userInterfaceIdiom == .pad {
            XCTAssertFalse(app.keyboards.firstMatch.exists)
            field.tap()
        }
        field.typeText("space")
        let first = app.buttons["favorites.save.search-0"]
        XCTAssertTrue(first.waitForExistence(timeout: 10))
        XCTAssertTrue(app.buttons["article.open.search-0"].exists)
        XCTAssertTrue(app.buttons["article.open.search-1"].exists)
        capture("search-cards")
        first.tap()
        XCTAssertTrue(app.buttons["favorites.login"].waitForExistence(timeout: 5))
        app.buttons["favorites.login"].tap()
        XCTAssertTrue(app.textFields["auth.email"].waitForExistence(timeout: 5))
        app.buttons["auth.close"].tap()
        XCTAssertTrue(first.waitForExistence(timeout: 5))
        XCTAssertEqual(field.value as? String, "space")
        close(app)
        XCTAssertTrue(app.buttons["profile.login"].waitForExistence(timeout: 5))
        XCTAssertFalse(field.exists)
        app.buttons["Search"].firstMatch.tap()
        XCTAssertTrue(field.waitForExistence(timeout: 5))
        XCTAssertEqual(field.value as? String, "space")
        field.buttons.firstMatch.tap()
        XCTAssertTrue(app.staticTexts["Enter a search query"].waitForExistence(timeout: 5))
        XCTAssertFalse(first.exists)
        field.tap()
        field.typeText("empty\n")
        XCTAssertTrue(app.staticTexts["No results"].waitForExistence(timeout: 5))
        XCUIDevice.shared.press(.home)
        app.activate()
        XCTAssertTrue(field.waitForExistence(timeout: 5))
        XCTAssertEqual(field.value as? String, "empty")
        capture("search-empty")
        field.tap()
        field.buttons.firstMatch.tap()
        field.tap()
        field.typeText("error\n")
        XCTAssertTrue(app.buttons["search.retry"].waitForExistence(timeout: 5))
        capture("search-error")
        close(app)
        for label in ["News", "Favorites", "Profile"] {
            app.buttons[label].firstMatch.tap()
            XCTAssertFalse(field.exists)
        }
    }

    @MainActor
    func testPaginationRetainsCardsAndScrollAcrossReturn() {
        let app = launch()
        app.buttons["Search"].firstMatch.tap()
        let field = app.searchFields.firstMatch
        XCTAssertTrue(field.waitForExistence(timeout: 5))
        field.tap()
        field.typeText("append\n")
        XCTAssertTrue(app.buttons["favorites.save.search-0"].waitForExistence(timeout: 5))
        let list = app.scrollViews["search.results"]
        let retry = app.buttons["search.retry"]
        for _ in 0..<14 {
            if retry.exists && retry.isHittable { break }
            list.swipeUp()
        }
        XCTAssertTrue(retry.waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["favorites.save.search-9"].exists)
        capture("search-pagination-error")
        retry.tap()
        let appendedCards = app.buttons.matching(
            NSPredicate(format: "identifier MATCHES 'favorites.save.search-[12][0-9]'"))
        for _ in 0..<6 {
            if appendedCards.allElementsBoundByIndex.contains(where: { $0.isHittable }) { break }
            list.swipeUp()
        }
        XCTAssertTrue(appendedCards.allElementsBoundByIndex.contains(where: { $0.isHittable }))
        XCTAssertFalse(retry.exists, "Successful append removes the previous failure")
        let visible = app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH 'favorites.save.search-'"))
            .allElementsBoundByIndex
            .first { $0.isHittable }
        let identifier = visible?.identifier
        XCTAssertNotNil(identifier, "Scroll restoration requires a visible card before leaving Search")
        close(app)
        app.buttons["Search"].firstMatch.tap()
        XCTAssertEqual(field.value as? String, "append")
        if let identifier { XCTAssertTrue(app.buttons[identifier].isHittable) }
        capture("search-retained-scroll")
    }

    @MainActor
    func testIPadSidebarAndRotationPreserveSearch() throws {
        guard UIDevice.current.userInterfaceIdiom == .pad else { throw XCTSkip("iPad navigation") }
        let app = launch()
        app.buttons["Search"].firstMatch.tap()
        let field = app.searchFields.firstMatch
        XCTAssertTrue(field.waitForExistence(timeout: 5))
        XCTAssertFalse(app.keyboards.firstMatch.exists)
        field.tap()
        field.typeText("science\n")
        XCTAssertTrue(app.buttons["favorites.save.search-0"].waitForExistence(timeout: 5))
        app.buttons["ToggleSideBar"].firstMatch.tap()
        XCTAssertEqual(field.value as? String, "science")
        capture("search-sidebar")
        XCUIDevice.shared.orientation = .landscapeLeft
        let landscape = NSPredicate { _, _ in app.frame.width > app.frame.height }
        expectation(for: landscape, evaluatedWith: app)
        waitForExpectations(timeout: 5)
        XCTAssertEqual(field.value as? String, "science")
        XCTAssertFalse(app.buttons["search.close"].exists)
        capture("search-ipad-landscape")
        XCUIDevice.shared.orientation = .portrait
    }

    @MainActor
    func testLargeTextCards() {
        let app = launch(extra: ["-UIPreferredContentSizeCategoryName", "UICTContentSizeCategoryAccessibilityXXXL"])
        app.buttons["Search"].firstMatch.tap()
        let field = app.searchFields.firstMatch
        XCTAssertTrue(field.waitForExistence(timeout: 5))
        field.tap()
        field.typeText("space\n")
        let star = app.buttons["favorites.save.search-0"]
        XCTAssertTrue(star.waitForExistence(timeout: 5))
        XCTAssertGreaterThanOrEqual(star.frame.width, 44)
        XCTAssertGreaterThanOrEqual(star.frame.height, 44)
        capture("search-large-text")
    }

    @MainActor private func launch(extra: [String] = []) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments =
            ["--search-ui-fixture", "--feed-ui-fixture", "--auth-ui-fixture", "-AppleLanguages", "(en)"] + extra
        app.launch()
        XCTAssertTrue(app.buttons["favorites.save.fixture-0"].waitForExistence(timeout: 10))
        return app
    }

    @MainActor private func close(_ app: XCUIApplication) {
        if UIDevice.current.userInterfaceIdiom == .pad {
            XCTAssertFalse(app.buttons["search.close"].exists)
            app.buttons["Profile"].firstMatch.tap()
        } else if app.buttons["Cancel"].exists {
            app.buttons["Cancel"].tap()
        } else {
            app.buttons["Close"].tap()
        }
    }
}
