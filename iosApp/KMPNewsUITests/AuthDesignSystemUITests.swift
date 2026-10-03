import XCTest

final class AuthDesignSystemUITests: XCTestCase {
    @MainActor
    func testManualPasswordDoesNotFillConfirmation() {
        let app = launchFixture()
        openLogin(app)
        hideKeyboard(app)
        app.buttons["auth.register"].tap()
        XCTAssertTrue(app.secureTextFields["auth.repeatPassword"].waitForExistence(timeout: 3))
        XCTAssertTrue(app.keyboards.firstMatch.waitForExistence(timeout: 3))
        app.typeText("reader@example.test")
        XCTAssertEqual(app.textFields["auth.email"].value as? String, "reader@example.test")
        app.secureTextFields["auth.password"].tap()
        app.secureTextFields["auth.password"].typeText("x")
        XCTAssertEqual(app.secureTextFields["auth.repeatPassword"].value as? String, "Повторите пароль")
        capture("auth-password-independent-fields")
    }

    @MainActor
    func testRecoveryAndRegistrationUseRealNavigation() {
        let app = launchFixture()
        openLogin(app)
        hideKeyboard(app)
        let submit = app.buttons["auth.submit"]
        let register = app.buttons["auth.register"]
        XCTAssertEqual(register.frame.minY - submit.frame.maxY, 12, accuracy: 1)
        XCTAssertGreaterThanOrEqual(app.buttons["auth.forgotPassword"].frame.height, 44)
        capture("auth-login-design-system")
        submit.tap()
        XCTAssertTrue(app.staticTexts["auth.error"].waitForExistence(timeout: 3))
        XCTAssertLessThan(app.staticTexts["auth.error"].frame.maxY, app.buttons["auth.forgotPassword"].frame.minY)
        capture("auth-login-validation")
        app.textFields["auth.email"].tap()
        app.textFields["auth.email"].typeText("reader@example.test")
        capture("auth-email-keyboard")
        hideKeyboard(app)
        app.buttons["auth.forgotPassword"].tap()
        XCTAssertTrue(app.navigationBars["Восстановление пароля"].waitForExistence(timeout: 3))
        XCTAssertEqual(app.textFields["auth.email"].value as? String, "reader@example.test")
        capture("auth-recovery-email")
        app.buttons["auth.submit"].tap()
        XCTAssertTrue(app.textFields["auth.code"].waitForExistence(timeout: 5))
        XCTAssertFalse(app.buttons["auth.resend"].isEnabled)
        capture("auth-recovery-code")
        XCTAssertTrue(app.keyboards.firstMatch.waitForExistence(timeout: 3))
        app.typeText("11111")
        XCTAssertEqual(app.textFields["auth.code"].value as? String, "11111")
        XCTAssertFalse(app.buttons["auth.retryCode"].exists)
        app.typeText("1")
        capture("auth-code-keyboard")
        XCTAssertFalse(app.buttons["auth.submit"].exists)
        XCTAssertTrue(app.staticTexts["auth.error"].waitForExistence(timeout: 3))
        app.textFields["auth.code"].tap()
        app.textFields["auth.code"].typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: 6) + "012345")
        XCTAssertTrue(app.navigationBars["Новый пароль"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.secureTextFields["auth.repeatPassword"].exists)
        XCTAssertTrue(app.keyboards.firstMatch.waitForExistence(timeout: 3))
        app.typeText("x")
        XCTAssertNotEqual(app.secureTextFields["auth.password"].value as? String, "Пароль")
        XCTAssertEqual(app.secureTextFields["auth.repeatPassword"].value as? String, "Повторите пароль")
        capture("auth-recovery-new-password")
        app.navigationBars["Новый пароль"].buttons["BackButton"].tap()
        XCTAssertTrue(app.textFields["auth.code"].exists)
        XCTAssertEqual(app.textFields["auth.code"].value as? String, "Код из письма")
        XCTAssertFalse(app.buttons["auth.resend"].isEnabled)
        app.textFields["auth.code"].tap()
        app.textFields["auth.code"].typeText("012345")
        XCTAssertTrue(app.navigationBars["Новый пароль"].waitForExistence(timeout: 5))
        fillPasswords(app)
        app.buttons["auth.submit"].tap()
        XCTAssertTrue(app.buttons["profile.logout"].waitForExistence(timeout: 5))
        XCTAssertFalse(app.buttons["auth.submit"].exists)
        capture("auth-recovery-profile")
        app.buttons["profile.logout"].tap()
        XCTAssertTrue(app.buttons["profile.register"].waitForExistence(timeout: 5))
        app.buttons["profile.register"].tap()
        app.textFields["auth.email"].tap()
        app.textFields["auth.email"].typeText("reader@example.test")
        hideKeyboard(app)
        fillPasswords(app)
        capture("auth-registration-design-system")
        app.buttons["auth.submit"].tap()
        XCTAssertTrue(app.textFields["auth.code"].waitForExistence(timeout: 5))
        XCTAssertFalse(app.buttons["auth.resend"].isEnabled)
        app.textFields["auth.code"].tap()
        app.textFields["auth.code"].typeText("012345")
        XCTAssertTrue(app.buttons["profile.logout"].waitForExistence(timeout: 5))
        app.buttons["profile.logout"].tap()
        XCTAssertTrue(app.buttons["profile.login"].waitForExistence(timeout: 5))
        openLogin(app)
        app.textFields["auth.email"].tap()
        app.textFields["auth.email"].typeText("reader@example.test")
        app.secureTextFields["auth.password"].tap()
        app.secureTextFields["auth.password"].typeText("fixture-password")
        hideKeyboard(app)
        app.buttons["auth.submit"].tap()
        XCTAssertTrue(app.buttons["profile.logout"].waitForExistence(timeout: 5))
        app.tabBars.buttons["Избранное"].tap()
        XCTAssertTrue(app.navigationBars["Избранное"].exists)
        app.tabBars.buttons["Новости"].tap()
        XCTAssertTrue(app.navigationBars["Новости"].exists)
    }

    @MainActor
    func testLoadingGeometryAndDismissal() {
        let app = launchFixture(extra: ["--auth-ui-slow"])
        openLogin(app)
        app.textFields["auth.email"].tap()
        app.textFields["auth.email"].typeText("reader@example.test")
        app.secureTextFields["auth.password"].tap()
        app.secureTextFields["auth.password"].typeText("fixture-password")
        hideKeyboard(app)
        let submit = app.buttons["auth.submit"]
        let frame = submit.frame
        submit.tap()
        XCTAssertFalse(submit.isEnabled)
        XCTAssertEqual(submit.value as? String, "Загрузка")
        XCTAssertEqual(submit.frame.height, frame.height, accuracy: 0.5)
        XCTAssertEqual(submit.frame.width, frame.width, accuracy: 0.5)
        capture("auth-login-loading")
        app.buttons["auth.close"].tap()
        XCTAssertTrue(app.buttons["profile.login"].waitForExistence(timeout: 3))
        openLogin(app)
        XCTAssertEqual(app.textFields["auth.email"].value as? String, "Email")
        XCTAssertEqual(app.secureTextFields["auth.password"].value as? String, "Пароль")
        app.navigationBars["Войти"].swipeDown()
        XCTAssertTrue(app.buttons["auth.close"].exists)
        app.buttons["auth.close"].tap()
        XCTAssertTrue(app.buttons["profile.login"].waitForExistence(timeout: 3))
        XCTAssertFalse(app.buttons["profile.logout"].waitForExistence(timeout: 9))
    }

    @MainActor
    func testUnconfirmedLoginAndStorageFailure() {
        let app = launchFixture(extra: ["--auth-ui-unconfirmed", "--auth-ui-storage-failure"])
        openLogin(app)
        app.textFields["auth.email"].tap()
        app.textFields["auth.email"].typeText("reader@example.test")
        app.secureTextFields["auth.password"].tap()
        app.secureTextFields["auth.password"].typeText("fixture-password")
        hideKeyboard(app)
        app.buttons["auth.submit"].tap()
        XCTAssertTrue(app.buttons["auth.openConfirmation"].waitForExistence(timeout: 3))
        app.buttons["auth.openConfirmation"].tap()
        XCTAssertTrue(app.buttons["auth.resend"].waitForExistence(timeout: 3))
        XCTAssertTrue(app.buttons["auth.resend"].isEnabled)
        app.buttons["auth.resend"].tap()
        let countdown = NSPredicate(format: "label BEGINSWITH %@", "Повторная отправка через")
        expectation(for: countdown, evaluatedWith: app.buttons["auth.resend"])
        waitForExpectations(timeout: 3)
        XCTAssertFalse(app.staticTexts["auth.countdown"].exists)
        capture("auth-resend-button-countdown")
        XCTAssertFalse(app.buttons["auth.resend"].isEnabled)
        app.navigationBars["Подтвердить email"].buttons["BackButton"].tap()
        app.buttons["auth.forgotPassword"].tap()
        app.buttons["auth.submit"].tap()
        XCTAssertTrue(app.textFields["auth.code"].waitForExistence(timeout: 3))
        app.textFields["auth.code"].tap()
        app.textFields["auth.code"].typeText("012345")
        XCTAssertTrue(app.secureTextFields["auth.repeatPassword"].waitForExistence(timeout: 3))
        fillPasswords(app)
        app.buttons["auth.submit"].tap()
        XCTAssertTrue(app.buttons["auth.returnToLogin"].waitForExistence(timeout: 3))
        XCTAssertTrue(app.staticTexts["auth.error"].exists)
        capture("auth-password-changed-storage-error")
        app.buttons["auth.returnToLogin"].tap()
        XCTAssertTrue(app.buttons["auth.close"].waitForExistence(timeout: 3))
        XCTAssertEqual(app.textFields["auth.email"].value as? String, "reader@example.test")
        XCTAssertEqual(app.secureTextFields["auth.password"].value as? String, "Пароль")
    }

    @MainActor
    func testLargeTextAndPasswordVisibility() {
        let app = launchFixture(extra: [
            "-UIPreferredContentSizeCategoryName", "UICTContentSizeCategoryAccessibilityXXXL",
        ])
        openLogin(app)
        let password = app.secureTextFields["auth.password"]
        for _ in 0..<6 where !password.isHittable { app.swipeUp() }
        password.tap()
        password.typeText("fixture-password")
        hideKeyboard(app)
        app.buttons["Показать"].tap()
        XCTAssertEqual(app.textFields["auth.password"].value as? String, "fixture-password")
        app.buttons["Скрыть"].tap()
        let register = app.buttons["auth.register"]
        for _ in 0..<6 where !register.isHittable { app.swipeUp() }
        XCTAssertTrue(register.isHittable)
        capture("auth-login-large-text")
        register.tap()
        XCTAssertTrue(app.navigationBars["Регистрация"].waitForExistence(timeout: 3))
        app.navigationBars["Регистрация"].buttons["BackButton"].tap()
        for _ in 0..<6 where !password.isHittable { app.swipeDown() }
        XCTAssertEqual(password.value as? String, "Пароль")
    }

    @MainActor private func launchFixture(extra: [String] = []) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["--auth-ui-fixture"] + extra
        app.launch()
        app.tabBars.buttons["Профиль"].tap()
        XCTAssertTrue(app.buttons["profile.login"].waitForExistence(timeout: 10))
        return app
    }

    @MainActor private func openLogin(_ app: XCUIApplication) {
        app.buttons["profile.login"].tap()
        XCTAssertTrue(app.textFields["auth.email"].waitForExistence(timeout: 3))
    }

    @MainActor private func fillPasswords(_ app: XCUIApplication) {
        for identifier in ["auth.password", "auth.repeatPassword"] {
            let field = app.secureTextFields[identifier]
            field.tap()
            field.typeText("fixture-password")
            if identifier == "auth.password" { capture("auth-password-keyboard") }
            hideKeyboard(app)
        }
    }

    @MainActor private func hideKeyboard(_ app: XCUIApplication) {
        let hide = app.buttons["Скрыть клавиатуру"].firstMatch
        if hide.waitForExistence(timeout: 2), hide.isHittable { hide.tap() }
    }
}
