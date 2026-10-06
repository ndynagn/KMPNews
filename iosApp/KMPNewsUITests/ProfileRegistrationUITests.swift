import XCTest

final class ProfileRegistrationUITests: XCTestCase {
    @MainActor
    func testNamesRequiredRegistrationAndLogoutConfirmation() {
        let app = XCUIApplication()
        app.launchArguments = [
            "--auth-ui-fixture", "--feed-ui-fixture", "-AppleLanguages", "(ru)", "-AppleLocale", "ru_RU",
        ]
        app.launch()
        selectSection("Профиль", in: app)
        app.buttons["profile.register"].tap()

        XCTAssertTrue(app.textFields["profile.lastName"].waitForExistence(timeout: 5))
        capture("profile-registration-names")

        app.buttons["auth.submit"].tap()

        // Validation remains in the current form and never navigates to confirmation.
        XCTAssertFalse(app.textFields["auth.code"].exists)

        for (id, value) in [("profile.lastName", "Иванова"), ("profile.firstName", "Анна-Мария")] {
            let field = app.textFields[id]
            field.tap()
            field.typeText(value)
            hideKeyboard(app)
        }

        for (id, value) in [
            ("auth.email", "reader@example.test"), ("auth.password", "fixture-password"),
            ("auth.repeatPassword", "fixture-password"),
        ] {
            let field = id == "auth.email" ? app.textFields[id] : app.secureTextFields[id]
            reveal(field, in: app)
            if id == "auth.email" {
                field.tap()
                field.typeText(value)
            } else {
                enterNewPassword(value, into: field, in: app)
            }
            if id != "auth.email" {
                XCTAssertEqual((field.value as? String)?.count, value.count, "Password entry lost characters")
                capture(id + "-after-entry")
            }
            hideKeyboard(app)
        }

        capture("profile-registration-filled")
        app.buttons["auth.submit"].tap()
        guard app.textFields["auth.code"].waitForExistence(timeout: 5) else {
            capture("profile-registration-error")
            let hierarchy = XCTAttachment(string: app.debugDescription)
            hierarchy.lifetime = .keepAlways
            add(hierarchy)
            XCTFail("Registration did not reach confirmation")
            return
        }

        app.textFields["auth.code"].tap()
        app.typeText("012345")

        XCTAssertTrue(app.buttons["profile.logout"].waitForExistence(timeout: 8))

        app.buttons["profile.logout"].tap()
        XCTAssertTrue(app.alerts["Выйти из аккаунта?"].waitForExistence(timeout: 3))
        capture("profile-logout-confirmation")
        app.alerts.buttons["Отмена"].tap()

        XCTAssertTrue(app.buttons["profile.logout"].exists)

        app.buttons["profile.logout"].tap()
        app.alerts.buttons["Выйти"].tap()

        XCTAssertTrue(app.buttons["profile.login"].waitForExistence(timeout: 5))
    }

    @MainActor
    private func reveal(_ field: XCUIElement, in app: XCUIApplication) {
        for _ in 0..<5 {
            if field.isHittable { return }
            app.swipeUp()
        }
    }
}
