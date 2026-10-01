import XCTest

final class KMPNewsUITests: XCTestCase {
    @MainActor
    func testTabTitlesAndPlaceholders() {
        let app = XCUIApplication()
        app.launch()
        XCTAssertTrue(app.navigationBars["Новости"].waitForExistence(timeout: 10))
        tapTab("Избранное", in: app)
        XCTAssertTrue(app.navigationBars["Избранное"].exists)
        XCTAssertTrue(app.staticTexts["Раздел в разработке"].exists)
        tapTab("Профиль", in: app)
        XCTAssertTrue(app.navigationBars["Профиль"].exists)
        XCTAssertTrue(app.staticTexts["Добро пожаловать"].waitForExistence(timeout: 10))
        attachScreenshot(app, "ios-profile")
        tapTab("Новости", in: app)
        XCTAssertTrue(app.navigationBars["Новости"].exists)
    }

    @MainActor
    func testGuestProfileAndFormsDoNotRequireNetwork() {
        let app = XCUIApplication()
        app.launchArguments = ["--auth-ui-fixture"]
        app.launch()
        tapTab("Профиль", in: app)
        let login = app.buttons["profile.login"]
        let register = app.buttons["profile.register"]
        XCTAssertTrue(login.waitForExistence(timeout: 10))
        XCTAssertTrue(register.isHittable)
        XCTAssertEqual(login.frame.height, register.frame.height, accuracy: 1)
        XCTAssertGreaterThanOrEqual(login.frame.height, 50)
        XCTAssertLessThan(login.frame.maxY, register.frame.minY)
        if app.frame.width < 600 {
            XCTAssertLessThan(register.frame.maxY, app.tabBars.firstMatch.frame.maxY)
        }
        attachScreenshot(app, "ios-profile-guest")
        login.tap()
        XCTAssertTrue(app.buttons["auth.close"].exists)
        XCTAssertEqual(app.buttons["auth.submit"].label, "Войти")
        XCTAssertFalse(app.navigationBars.buttons["auth.submit"].exists)
        XCTAssertTrue(app.buttons["auth.forgotPassword"].exists)
        let fieldRows = app.cells.containing(.textField, identifier: "auth.email")
        let passwordRows = app.cells.containing(.secureTextField, identifier: "auth.password")
        XCTAssertEqual(fieldRows.firstMatch.frame.height, passwordRows.firstMatch.frame.height, accuracy: 2)
        XCTAssertLessThanOrEqual(app.buttons["auth.forgotPassword"].frame.maxY, app.buttons["auth.submit"].frame.minY)
        XCTAssertLessThanOrEqual(app.buttons["auth.submit"].frame.maxY, app.buttons["auth.register"].frame.minY)
        app.buttons["auth.forgotPassword"].tap()
        XCTAssertTrue(app.navigationBars["Восстановление пароля"].waitForExistence(timeout: 3))
        XCTAssertTrue(app.textFields["auth.email"].exists)
        app.navigationBars["Восстановление пароля"].buttons["BackButton"].tap()
        attachScreenshot(app, "ios-login-sheet")
        app.buttons["auth.submit"].tap()
        XCTAssertTrue(app.staticTexts["Введите корректный email"].exists)
        let email = app.textFields["auth.email"]
        email.tap()
        email.typeText("reader@example.test")
        app.secureTextFields["auth.password"].tap()
        app.secureTextFields["auth.password"].typeText("fixture-password")
        XCTAssertEqual((app.secureTextFields["auth.password"].value as? String)?.count, 16)
        attachScreenshot(app, "ios-password-input")
        app.buttons["Показать"].tap()
        XCTAssertTrue(app.textFields["auth.password"].exists)
        XCTAssertEqual(app.textFields["auth.password"].value as? String, "fixture-password")
        app.buttons["Скрыть"].tap()
        XCTAssertTrue(app.secureTextFields["auth.password"].exists)
        XCTAssertEqual((app.secureTextFields["auth.password"].value as? String)?.count, 16)
        attachScreenshot(app, "ios-login-keyboard")
        // Kill the process while credentials are present: they must not enter restoration state.
        // Native Password AutoFill saving is separate from this application's session storage.
        app.terminate()
        app.launch()
        tapTab("Профиль", in: app)
        XCTAssertTrue(login.waitForExistence(timeout: 10))
        login.tap()
        XCTAssertEqual(app.textFields["auth.email"].value as? String, "Email")
        XCTAssertEqual(app.secureTextFields["auth.password"].value as? String, "Пароль")
        attachScreenshot(app, "ios-login-restarted")
        app.buttons["auth.register"].tap()
        XCTAssertTrue(app.secureTextFields["auth.repeatPassword"].exists)
        XCTAssertFalse(app.buttons["auth.close"].exists)
        app.navigationBars["Регистрация"].buttons.firstMatch.tap()
        XCTAssertTrue(app.buttons["auth.close"].exists)
        app.buttons["auth.register"].tap()
        XCTAssertTrue(app.navigationBars["Регистрация"].waitForExistence(timeout: 3))
        app.coordinate(withNormalizedOffset: CGVector(dx: 0.01, dy: 0.4))
            .press(forDuration: 0.05, thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.9, dy: 0.4)))
        XCTAssertTrue(app.buttons["auth.close"].waitForExistence(timeout: 3))
        app.buttons["auth.close"].tap()
        XCTAssertTrue(register.waitForExistence(timeout: 5))
        register.tap()
        XCTAssertTrue(app.secureTextFields["auth.repeatPassword"].waitForExistence(timeout: 5))
        attachScreenshot(app, "ios-register-sheet")
        app.buttons["auth.close"].tap()
        XCTAssertTrue(login.waitForExistence(timeout: 5))
        login.tap()
        let close = app.buttons["auth.close"]
        close.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5))
            .press(forDuration: 0.1, thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.95)))
        XCTAssertTrue(login.waitForExistence(timeout: 5))
        XCTAssertTrue(login.isHittable)
    }

    @MainActor
    func testGuestProfileLargeText() {
        let app = XCUIApplication()
        app.launchArguments += ["-UIPreferredContentSizeCategoryName", "UICTContentSizeCategoryAccessibilityXXXL"]
        app.launch()
        tapTab("Профиль", in: app)
        XCTAssertTrue(app.buttons["profile.login"].waitForExistence(timeout: 10))
        app.swipeUp()
        XCTAssertTrue(app.buttons["profile.register"].isHittable)
        attachScreenshot(app, "ios-profile-large-text")
        app.buttons["profile.login"].tap()
        if app.frame.width < 600 {
            let expanded = XCTNSPredicateExpectation(
                predicate: NSPredicate { _, _ in
                    app.buttons["auth.close"].frame.minY < app.frame.height * 0.2
                }, object: nil)
            XCTAssertEqual(XCTWaiter.wait(for: [expanded], timeout: 5), .completed)
        }
        app.swipeUp()
        XCTAssertTrue(app.buttons["auth.register"].isHittable)
        attachScreenshot(app, "ios-auth-large-text")
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
        tapTab("Избранное", in: app)
        tapTab("Новости", in: app)
        XCTAssertTrue(card.isHittable)
        app.scrollViews.firstMatch.swipeUp()
        XCTAssertLessThan(navigation.frame.height, largeHeight)
        attachScreenshot(app, "ios-collapsed-title")
        let visibleCard = try XCTUnwrap(
            app.otherElements.matching(
                NSPredicate(format: "identifier BEGINSWITH %@", "feed.article.")
            ).allElementsBoundByIndex.first { $0.isHittable })
        let identifier = visibleCard.identifier
        let verticalPosition = visibleCard.frame.minY
        tapTab("Профиль", in: app)
        XCTAssertTrue(app.buttons["profile.login"].waitForExistence(timeout: 10))
        app.buttons["profile.login"].tap()
        app.buttons["auth.close"].tap()
        tapTab("Новости", in: app)
        let restoredCard = app.otherElements[identifier]
        XCTAssertTrue(restoredCard.isHittable)
        XCTAssertEqual(restoredCard.frame.minY, verticalPosition, accuracy: 2)
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
    private func tapTab(_ title: String, in app: XCUIApplication) {
        if app.tabBars.buttons[title].exists {
            app.tabBars.buttons[title].tap()
        } else if app.buttons[title].firstMatch.exists {
            app.buttons[title].firstMatch.tap()
        } else {
            // iPad floating tabs expose their items as cells in the modern accessibility tree.
            app.cells[title].firstMatch.tap()
        }
    }

    @MainActor
    private func attachScreenshot(_ app: XCUIApplication, _ name: String) {
        // Let system sheet and navigation animations settle before capturing visual evidence.
        Thread.sleep(forTimeInterval: 0.5)
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
