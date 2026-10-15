import XCTest

final class ProfileEditingUITests: XCTestCase {
    @MainActor
    func testDarkLargeTextAndSaveError() {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchArguments = [
            "--auth-ui-fixture", "--auth-ui-signed-in", "--feed-ui-fixture",
            "--profile-ui-long-name", "--profile-ui-save-error", "--profile-ui-dark",
            "-AppleLanguages", "(ru)", "-AppleLocale", "ru_RU",
            "-AppleInterfaceStyle", "Dark", "-UIPreferredContentSizeCategoryName",
            "UICTContentSizeCategoryAccessibilityXXXL",
        ]
        app.launch()
        selectSection("Профиль", in: app)
        let edit = app.buttons["profile.edit"]

        XCTAssertTrue(edit.waitForExistence(timeout: 10))
        XCTAssertTrue(edit.isHittable)
        capture("profile-dark-accessibility-long-name")

        edit.tap()
        let firstName = app.textFields["profile.firstName"]
        XCTAssertTrue(firstName.waitForExistence(timeout: 5))

        firstName.tap()
        firstName.typeText("-Тест")
        app.buttons["profile.edit.save"].tap()

        XCTAssertTrue(app.buttons["profile.edit.save"].waitForExistence(timeout: 5))
        XCTAssertTrue((firstName.value as? String)?.contains("-Тест") == true)

        for _ in 0..<6 {
            if app.staticTexts["profile.edit.error"].isHittable { break }
            app.swipeUp()
        }

        XCTAssertTrue(app.staticTexts["profile.edit.error"].isHittable)
        capture("profile-dark-editor-error")
    }

    @MainActor
    func testProfileEditingAndLogout() {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchArguments = [
            "--auth-ui-fixture", "--auth-ui-signed-in", "--feed-ui-fixture", "--profile-ui-light",
            "-AppleLanguages", "(ru)", "-AppleLocale", "ru_RU",
            "-AppleInterfaceStyle", "Light", "-UIPreferredContentSizeCategoryName", "UICTContentSizeCategoryL",
        ]
        app.launch()
        selectSection("Профиль", in: app)
        let edit = app.buttons["profile.edit"]

        XCTAssertTrue(edit.waitForExistence(timeout: 10))
        XCTAssertEqual(app.buttons.matching(identifier: "profile.edit").count, 1)
        capture("profile-selected-layout")

        edit.tap()
        let firstName = app.textFields["profile.firstName"]
        XCTAssertTrue(firstName.waitForExistence(timeout: 5))

        firstName.tap()
        firstName.typeText("-Тест")
        capture("profile-editor-keyboard")
        app.buttons["profile.edit.cancel"].tap()
        app.buttons["profile.discard"].firstMatch.tap()

        XCTAssertTrue(edit.waitForExistence(timeout: 5))
        XCTAssertFalse(app.staticTexts["Александр-Тест"].exists)

        edit.tap()
        firstName.tap()
        firstName.typeText("-Тест")
        app.buttons["profile.edit.save"].tap()

        XCTAssertTrue(edit.waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["Александр-Тест"].exists)

        let logout = app.buttons["profile.logout"]
        if !logout.isHittable { app.swipeUp() }
        logout.tap()
        XCTAssertTrue(app.alerts["Выйти из аккаунта?"].waitForExistence(timeout: 3))
        capture("profile-selected-logout")
        app.alerts.buttons["Отмена"].tap()

        XCTAssertTrue(edit.exists)

        logout.tap()
        app.alerts.buttons["Выйти"].tap()

        XCTAssertTrue(app.buttons["profile.login"].waitForExistence(timeout: 5))
    }
}
