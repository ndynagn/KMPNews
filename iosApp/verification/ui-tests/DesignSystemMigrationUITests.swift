import XCTest

final class DesignSystemMigrationUITests: XCTestCase {
    @MainActor
    func testLoadingState() {
        let app = launch(extra: ["--feed-ui-loading"])

        XCTAssertTrue(app.staticTexts["Загрузка новостей"].waitForExistence(timeout: 5))
        XCTAssertFalse(app.otherElements["feed.article.fixture-0"].exists)
        capture("ds-feed-loading")
        XCTAssertTrue(app.otherElements["feed.article.fixture-0"].waitForExistence(timeout: 12))
    }

    @MainActor
    func testCatalogContentAndStates() {
        let app = launch()

        app.tabBars.buttons["Профиль"].tap()
        app.buttons["kit.entry"].tap()
        app.buttons["kit.contentLink"].tap()

        XCTAssertTrue(app.navigationBars["Новостной контент"].waitForExistence(timeout: 5))
        capture("ds-catalog-content")

        app.navigationBars["Новостной контент"].buttons["BackButton"].tap()
        app.buttons["kit.statesLink"].tap()

        XCTAssertTrue(app.navigationBars["Состояния"].waitForExistence(timeout: 5))
        capture("ds-catalog-states")
    }

    @MainActor
    func testProfileButtonsAndFeedCards() {
        let app = launch()

        XCTAssertTrue(app.otherElements["feed.article.fixture-0"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["Тестовая новость 1: город и технологии"].exists)
        XCTAssertFalse(app.staticTexts["Fixture description must not appear on a card."].exists)
        capture("ds-feed-cards")

        app.tabBars.buttons["Профиль"].tap()

        let login = app.buttons["profile.login"]
        let registration = app.buttons["profile.register"]

        XCTAssertTrue(login.waitForExistence(timeout: 5))
        XCTAssertEqual(login.frame.height, registration.frame.height, accuracy: 1)
        XCTAssertGreaterThanOrEqual(login.frame.height, 44)
        XCTAssertTrue(registration.isHittable)
        capture("ds-profile-buttons")

        login.tap()

        XCTAssertTrue(app.textFields["auth.email"].waitForExistence(timeout: 5))
        app.buttons["auth.close"].tap()

        registration.tap()

        XCTAssertTrue(app.secureTextFields["auth.repeatPassword"].waitForExistence(timeout: 5))
        app.buttons["auth.close"].tap()
    }

    @MainActor
    func testEmptyAndRetryStates() {
        let empty = launch(extra: ["--feed-ui-empty"])

        XCTAssertTrue(empty.staticTexts["Новостей пока нет"].waitForExistence(timeout: 5))
        capture("ds-feed-empty")
        empty.terminate()

        let failed = launch(extra: ["--feed-ui-error"])

        XCTAssertTrue(failed.buttons["feed.retryButton"].waitForExistence(timeout: 5))
        capture("ds-feed-error")

        failed.buttons["feed.retryButton"].tap()

        XCTAssertTrue(failed.otherElements["feed.article.fixture-0"].waitForExistence(timeout: 5))
        XCTAssertFalse(failed.buttons["feed.retryButton"].exists)
    }

    @MainActor
    func testUpdateFailureRetainsCards() {
        let app = launch(extra: ["--feed-ui-cached-error"])
        let first = app.otherElements["feed.article.fixture-0"]

        XCTAssertTrue(first.waitForExistence(timeout: 5))

        let retry = app.buttons["feed.retryButton"]

        for _ in 0..<8 where !retry.isHittable { app.scrollViews["feed.list"].swipeUp() }

        XCTAssertTrue(retry.isHittable)
        capture("ds-feed-cached-error")

        retry.tap()

        let recovered = XCTNSPredicateExpectation(predicate: NSPredicate(format: "exists == false"), object: retry)

        XCTAssertEqual(XCTWaiter.wait(for: [recovered], timeout: 5), .completed)
    }

    @MainActor
    func testProfileAndCardsAtLargeText() {
        let app = launch(extra: ["-UIPreferredContentSizeCategoryName", "UICTContentSizeCategoryAccessibilityXXXL"])

        XCTAssertTrue(app.otherElements["feed.article.fixture-0"].waitForExistence(timeout: 5))
        capture("ds-feed-large-text")

        app.tabBars.buttons["Профиль"].tap()
        XCTAssertTrue(app.buttons["profile.login"].waitForExistence(timeout: 5))
        app.swipeUp()
        XCTAssertTrue(app.buttons["profile.register"].isHittable)
        capture("ds-profile-large-text")
    }

    @MainActor
    private func launch(extra: [String] = []) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["--feed-ui-fixture", "--auth-ui-fixture"] + extra

        app.launch()

        return app
    }
}
