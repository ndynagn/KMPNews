import XCTest

final class ProfileLoadingUITests: XCTestCase {
    @MainActor
    func testLogoutDuringProfileLoading() {
        let app = launchProfile(arguments: ["--profile-ui-slow-fetch"])

        XCTAssertTrue(app.otherElements["profile.loading"].waitForExistence(timeout: 5))
        XCTAssertFalse(app.buttons["profile.edit"].exists)
        capture("profile-skeleton-light")

        confirmLogout(in: app)
    }

    @MainActor
    func testLogoutDuringProfileLoadingWithLargeText() {
        let app = launchProfile(arguments: [
            "--profile-ui-slow-fetch", "--profile-ui-dark", "-AppleInterfaceStyle", "Dark",
            "-UIPreferredContentSizeCategoryName", "UICTContentSizeCategoryAccessibilityXXXL",
        ])

        XCTAssertTrue(app.otherElements["profile.loading"].waitForExistence(timeout: 5))
        capture("profile-skeleton-dark-large-text")

        confirmLogout(in: app)
    }

    @MainActor
    func testProfileErrorAllowsLogoutCancellationAndRetry() {
        let app = launchProfile(arguments: ["--profile-ui-fetch-error-once"])
        let retry = app.buttons["profile.retry"]

        XCTAssertTrue(retry.waitForExistence(timeout: 5))
        XCTAssertFalse(app.otherElements["profile.loading"].exists)
        capture("profile-fetch-error")

        app.buttons["profile.logout"].tap()
        XCTAssertTrue(app.alerts["Выйти из аккаунта?"].waitForExistence(timeout: 3))
        app.alerts.buttons["Отмена"].tap()

        XCTAssertTrue(retry.exists)

        retry.tap()

        XCTAssertTrue(app.buttons["profile.edit"].waitForExistence(timeout: 5))
        XCTAssertFalse(retry.exists)
    }

    @MainActor
    func testLogoutAfterProfileFetchError() {
        let app = launchProfile(arguments: ["--profile-ui-fetch-error-once"])

        XCTAssertTrue(app.buttons["profile.retry"].waitForExistence(timeout: 5))

        confirmLogout(in: app)
    }

    @MainActor
    func testLogoutWhenSessionIsUnavailable() {
        let app = launchProfile(arguments: ["--auth-ui-profile-error"])

        XCTAssertTrue(app.buttons["profile.retry"].waitForExistence(timeout: 5))
        capture("profile-session-error")

        confirmLogout(in: app)
    }

    @MainActor
    private func launchProfile(arguments: [String]) -> XCUIApplication {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchArguments =
            [
                "--auth-ui-fixture", "--auth-ui-signed-in", "--feed-ui-fixture",
                "-AppleLanguages", "(ru)", "-AppleLocale", "ru_RU",
            ] + arguments
        app.launch()
        selectSection("Профиль", in: app)

        return app
    }

    @MainActor
    private func confirmLogout(in app: XCUIApplication) {
        let logout = app.buttons["profile.logout"]
        for _ in 0..<5 {
            if logout.exists && logout.isHittable { break }
            app.swipeUp()
        }

        XCTAssertTrue(logout.exists)
        XCTAssertTrue(logout.isEnabled)
        XCTAssertTrue(logout.isHittable)

        logout.tap()
        XCTAssertTrue(app.alerts["Выйти из аккаунта?"].waitForExistence(timeout: 3))
        app.alerts.buttons["Выйти"].tap()

        XCTAssertTrue(app.buttons["profile.login"].waitForExistence(timeout: 5))
    }
}
