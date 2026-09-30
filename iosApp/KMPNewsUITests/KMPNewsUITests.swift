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
    func testCardsOmitDescriptionAndTitleCollapses() throws {
        let app = XCUIApplication()
        app.launch()
        let card = app.otherElements.matching(
            NSPredicate(format: "identifier BEGINSWITH %@", "feed.article.")
        ).firstMatch
        guard card.waitForExistence(timeout: 15) else {
            throw XCTSkip("Requires cached articles in the simulator")
        }
        let navigation = app.navigationBars["Новости"]
        let largeHeight = navigation.frame.height
        XCTAssertFalse(card.staticTexts["Описание отсутствует"].exists)
        XCTAssertFalse(
            card.staticTexts[
                "Deterministic UI fixture. The description appears when the headline is expanded."
            ].exists)
        attachScreenshot(app, "ios-card-no-description")
        app.tabBars.buttons["Избранное"].tap()
        app.tabBars.buttons["Новости"].tap()
        XCTAssertTrue(card.isHittable)
        app.scrollViews.firstMatch.swipeUp()
        XCTAssertLessThan(navigation.frame.height, largeHeight)
        attachScreenshot(app, "ios-collapsed-title")
    }

    /// Requires explicit opt-in because it uses the real mediator and its shared quota.
    @MainActor
    func testLiveMediatorRefreshAndScroll() throws {
        guard ProcessInfo.processInfo.environment["LIVE_FEED_SMOKE"] == "1" else {
            throw XCTSkip("Set TEST_RUNNER_LIVE_FEED_SMOKE=1 for the live mediator smoke")
        }
        let app = XCUIApplication()
        app.launch()
        let feed = app.scrollViews["feed.list"]
        XCTAssertTrue(feed.waitForExistence(timeout: 15))
        let article = app.descendants(matching: .any)
            .matching(NSPredicate(format: "identifier BEGINSWITH %@", "feed.article.")).firstMatch
        XCTAssertTrue(article.waitForExistence(timeout: 35))
        feed.swipeDown()
        XCTAssertTrue(article.waitForExistence(timeout: 35))
        attachScreenshot(app, "ios-live-mediator")
        feed.swipeUp()
        XCTAssertTrue(feed.exists)
        attachScreenshot(app, "ios-live-mediator-scroll")
    }

    @MainActor
    private func attachScreenshot(_ app: XCUIApplication, _ name: String) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
