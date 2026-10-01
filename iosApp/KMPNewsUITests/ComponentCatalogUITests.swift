import XCTest

final class ComponentCatalogUITests: XCTestCase {
    @MainActor
    func testCatalogFormsAndNavigation() {
        let app = XCUIApplication()

        app.launch()
        app.tabBars.buttons["Профиль"].tap()
        XCTAssertTrue(app.buttons["kit.entry"].waitForExistence(timeout: 10))
        app.buttons["kit.entry"].tap()
        XCTAssertTrue(app.navigationBars["UI-Kit"].waitForExistence(timeout: 5))
        capture(app, "kit-index")

        let actions = app.buttons["kit.actionsLink"]

        for _ in 0..<6 where !actions.isHittable { app.swipeUp() }
        actions.tap()
        XCTAssertTrue(app.buttons["Войти"].waitForExistence(timeout: 3))
        capture(app, "kit-actions")

        assertLoadingGeometry(app)
        app.navigationBars["Действия"].buttons["BackButton"].tap()
        app.buttons["kit.openAuth"].tap()
        XCTAssertTrue(app.textFields["kit.email"].waitForExistence(timeout: 5))
        XCTAssertFalse(app.buttons["kit.submit"].isEnabled)

        let emailRow = app.cells.containing(.textField, identifier: "kit.email").firstMatch
        let passwordRow = app.cells.containing(.secureTextField, identifier: "kit.password").firstMatch

        XCTAssertEqual(emailRow.frame.height, passwordRow.frame.height, accuracy: 2)
        capture(app, "kit-login")

        app.buttons["kit.register"].tap()
        XCTAssertTrue(app.secureTextFields["kit.repeatPassword"].waitForExistence(timeout: 3))
        capture(app, "kit-registration")

        app.textFields["kit.email"].tap()
        app.textFields["kit.email"].typeText("reader@example.test")
        app.secureTextFields["kit.password"].tap()
        app.secureTextFields["kit.password"].typeText("fixture-password")
        app.buttons["Скрыть клавиатуру"].tap()
        app.secureTextFields["kit.repeatPassword"].tap()
        app.secureTextFields["kit.repeatPassword"].typeText("fixture-password")
        app.buttons["Скрыть клавиатуру"].tap()
        app.buttons["kit.submit"].tap()
        XCTAssertTrue(app.textFields["kit.code"].waitForExistence(timeout: 5))
        XCTAssertFalse(app.buttons["kit.resend"].isEnabled)
        capture(app, "kit-confirmation")

        app.textFields["kit.code"].tap()
        app.textFields["kit.code"].typeText("012345")
        XCTAssertFalse(app.buttons["kit.submit"].exists)
        XCTAssertTrue(app.buttons["kit.openAuth"].waitForExistence(timeout: 5))
        app.buttons["kit.openAuth"].tap()
        XCTAssertEqual(app.textFields["kit.email"].value as? String, "Email")
        app.buttons["kit.recovery"].tap()
        XCTAssertTrue(app.navigationBars["Восстановление пароля"].waitForExistence(timeout: 3))
        capture(app, "kit-recovery")

        app.navigationBars["Восстановление пароля"].buttons["BackButton"].tap()
        app.buttons["kit.authClose"].tap()
        app.buttons["kit.openAuth"].tap()
        app.navigationBars["Войти"].swipeDown()
        XCTAssertTrue(app.buttons["kit.openAuth"].waitForExistence(timeout: 5))
        app.buttons["kit.close"].tap()
        XCTAssertTrue(app.navigationBars["Профиль"].waitForExistence(timeout: 5))
        app.tabBars.buttons["Новости"].tap()
        XCTAssertTrue(app.navigationBars["Новости"].exists)
    }

    @MainActor
    func testCatalogLargeText() {
        let app = XCUIApplication()
        app.launchArguments += ["-UIPreferredContentSizeCategoryName", "UICTContentSizeCategoryAccessibilityXXXL"]

        app.launch()
        app.tabBars.buttons["Профиль"].tap()
        app.buttons["kit.entry"].tap()

        let actions = app.buttons["kit.actionsLink"]

        for _ in 0..<6 where !actions.isHittable { app.swipeUp() }
        actions.tap()
        assertLoadingGeometry(app)
        capture(app, "kit-actions-large-text")
        app.navigationBars["Действия"].buttons["BackButton"].tap()

        let open = app.buttons["kit.openAuth"]

        for _ in 0..<6 where !open.isHittable { app.swipeUp() }
        open.tap()

        let email = app.textFields["kit.email"]

        for _ in 0..<8 where !email.isHittable { app.swipeUp() }
        XCTAssertTrue(email.isHittable)
        capture(app, "kit-large-text")

        let register = app.buttons["kit.register"]

        for _ in 0..<6 where !register.isHittable { app.swipeUp() }
        XCTAssertTrue(register.isHittable)
        register.tap()
        XCTAssertTrue(app.navigationBars["Создать аккаунт"].exists)
    }

    @MainActor
    func testCatalogSpecimens() {
        let app = XCUIApplication()
        app.launch()
        app.tabBars.buttons["Профиль"].tap()
        app.buttons["kit.entry"].tap()
        for (title, name) in [
            ("Основы", "foundations"), ("Навигация", "navigation"),
            ("Новостной контент", "content"), ("Состояния", "states"),
            ("Пример профиля", "profile"),
        ] {
            let link = app.buttons[title]
            for _ in 0..<5 where !link.isHittable { app.swipeUp() }
            link.tap()
            XCTAssertTrue(app.navigationBars[title].waitForExistence(timeout: 3))
            capture(app, "kit-" + name)
            if name == "navigation" {
                app.buttons["Показать уведомление"].tap()
                XCTAssertTrue(app.alerts["Демонстрация"].waitForExistence(timeout: 3))
                app.alerts.buttons["ОК"].tap()
            }
            if name == "content" {
                app.switches["Изображение недоступно"].firstMatch.coordinate(
                    withNormalizedOffset: CGVector(dx: 0.9, dy: 0.5)
                ).tap()
                capture(app, "kit-image-unavailable")
            }
            app.navigationBars[title].buttons["BackButton"].tap()
        }
    }

    @MainActor
    private func assertLoadingGeometry(_ app: XCUIApplication) {
        let button = app.buttons["Войти"]
        let initialFrame = button.frame

        XCTAssertGreaterThan(initialFrame.height, 0)
        app.switches["kit.loadingToggle"].firstMatch.coordinate(withNormalizedOffset: CGVector(dx: 0.9, dy: 0.5)).tap()
        XCTAssertFalse(button.isEnabled)
        XCTAssertEqual(button.value as? String, "Загрузка")
        XCTAssertEqual(button.frame.width, initialFrame.width, accuracy: 0.5)
        XCTAssertEqual(button.frame.height, initialFrame.height, accuracy: 0.5)
        capture(app, "kit-actions-loading")
        app.switches["kit.loadingToggle"].firstMatch.coordinate(withNormalizedOffset: CGVector(dx: 0.9, dy: 0.5)).tap()
        XCTAssertTrue(button.isEnabled)
    }

    @MainActor
    private func capture(_ app: XCUIApplication, _ name: String) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
