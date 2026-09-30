import XCTest

final class KMPNewsUITests: XCTestCase {
    @MainActor
    func testTabTitlesAndPlaceholders() {
        let app = XCUIApplication()
        app.launch()
        XCTAssertTrue(app.navigationBars["Новости"].waitForExistence(timeout: 10))
        app.tabBars.buttons["Избранное"].tap()
        XCTAssertTrue(app.navigationBars["Избранное"].exists)
        XCTAssertTrue(app.staticTexts["Раздел в разработке"].exists)
        app.tabBars.buttons["Профиль"].tap()
        XCTAssertTrue(app.navigationBars["Профиль"].exists)
        XCTAssertTrue(app.staticTexts["Раздел в разработке"].exists)
        attachScreenshot(app, "ios-profile")
        app.tabBars.buttons["Новости"].tap()
        XCTAssertTrue(app.navigationBars["Новости"].exists)
    }

    @MainActor
    func testCachedCardExpansionAndScrollSurviveTabSwitch() throws {
        let app = XCUIApplication()
        app.launch()
        let title = "Test news 0: A headline that can expand to show the article summary"
        let headline = app.buttons[title]
        guard headline.waitForExistence(timeout: 10) else {
            throw XCTSkip("Requires the documented deterministic simulator cache fixture")
        }
        headline.tap()
        let summary = app.staticTexts[
            "Deterministic UI fixture. The description appears when the headline is expanded."]
        XCTAssertTrue(summary.waitForExistence(timeout: 3))
        attachScreenshot(app, "ios-expanded")
        app.tabBars.buttons["Избранное"].tap()
        app.tabBars.buttons["Новости"].tap()
        XCTAssertTrue(summary.isHittable)
        app.scrollViews.firstMatch.swipeUp()
        let before = app.scrollViews.firstMatch.staticTexts.allElementsBoundByIndex
            .filter(\.isHittable).map(\.label)
        app.tabBars.buttons["Профиль"].tap()
        app.tabBars.buttons["Новости"].tap()
        let after = app.scrollViews.firstMatch.staticTexts.allElementsBoundByIndex
            .filter(\.isHittable).map(\.label)
        XCTAssertEqual(before, after)
        attachScreenshot(app, "ios-restored-scroll")
    }

    @MainActor
    private func attachScreenshot(_ app: XCUIApplication, _ name: String) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
