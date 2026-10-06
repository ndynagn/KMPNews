import XCTest

final class SearchNavigationUITests: XCTestCase {
    @MainActor
    func testNativeNavigationPrototype() { verifyPrototype(legacy: false) }

    @MainActor
    func testLegacyNavigationPrototype() { verifyPrototype(legacy: true) }

    @MainActor
    private func verifyPrototype(legacy: Bool) {
        let app = XCUIApplication()
        app.launchArguments = ["--search-navigation-prototype", "-AppleLanguages", "(en)"]
        if legacy { app.launchArguments.append("--search-legacy-navigation") }
        app.launch()
        XCTAssertTrue(app.staticTexts["prototype.news"].waitForExistence(timeout: 10))
        XCTAssertFalse(app.searchFields.firstMatch.exists)
        select("Profile", in: app)
        XCTAssertTrue(app.staticTexts["prototype.profile"].waitForExistence(timeout: 5))
        select("Search", in: app)
        let field = app.searchFields.firstMatch
        XCTAssertTrue(field.waitForExistence(timeout: 5))
        if UIDevice.current.userInterfaceIdiom == .pad {
            XCTAssertFalse(app.keyboards.firstMatch.exists)
            field.tap()
        } else {
            XCTAssertTrue(app.keyboards.firstMatch.waitForExistence(timeout: 5))
        }
        field.typeText("space")
        capture(legacy ? "search-legacy-input" : "search-native-input")
        field.buttons.firstMatch.tap()
        XCTAssertEqual(field.value as? String, "Search news")
        XCTAssertEqual(app.staticTexts["prototype.query"].label, "Query: []")
        field.tap()
        field.typeText("science")
        close(app)
        XCTAssertTrue(app.staticTexts["prototype.profile"].waitForExistence(timeout: 5))
        XCTAssertFalse(app.searchFields.firstMatch.exists)
        select("Search", in: app)
        XCTAssertTrue(field.waitForExistence(timeout: 5))
        XCTAssertEqual(field.value as? String, "science")
        if UIDevice.current.userInterfaceIdiom == .pad { XCTAssertFalse(app.keyboards.firstMatch.exists) }
        XCUIDevice.shared.orientation = .landscapeLeft
        XCTAssertTrue(field.waitForExistence(timeout: 5))
        XCTAssertEqual(field.value as? String, "science")
        capture("search-landscape")
        XCUIDevice.shared.orientation = .portrait
        close(app)
        for (label, marker) in [("News", "prototype.news"), ("Favorites", "prototype.favorites")] {
            select(label, in: app)
            XCTAssertTrue(app.staticTexts[marker].waitForExistence(timeout: 5))
            XCTAssertFalse(app.searchFields.firstMatch.exists)
        }
    }

    @MainActor
    private func select(_ label: String, in app: XCUIApplication) {
        let button = app.buttons[label].firstMatch
        if button.exists, button.isHittable { button.tap(); return }
        let cell = app.cells.containing(.staticText, identifier: label).firstMatch
        if cell.exists, cell.isHittable { cell.tap(); return }
        XCTFail("Missing section: \(label). \(app.debugDescription)")
    }

    @MainActor
    private func close(_ app: XCUIApplication) {
        if UIDevice.current.userInterfaceIdiom == .pad {
            XCTAssertFalse(app.buttons["search.close"].exists)
            select("Profile", in: app)
        } else if app.buttons["Cancel"].exists {
            app.buttons["Cancel"].tap()
        } else {
            app.buttons["Close"].tap()
        }
    }
}
