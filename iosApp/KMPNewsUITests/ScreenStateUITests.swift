import XCTest

final class ScreenStateUITests: XCTestCase {
    @MainActor
    func testGuestFavoritesUsesProfileActionsAtBottom() {
        let app = launch(extra: [])
        app.buttons["Profile"].firstMatch.tap()
        let profileRegister = app.buttons["profile.register"]
        XCTAssertTrue(profileRegister.waitForExistence(timeout: 5))
        let bottom = profileRegister.frame.maxY
        let label = profileRegister.label

        app.buttons["Favorites"].firstMatch.tap()
        let login = app.buttons["favorites.guest.login"]
        let register = app.buttons["favorites.guest.register"]
        XCTAssertTrue(register.waitForExistence(timeout: 5))
        XCTAssertEqual(register.label, label)
        XCTAssertEqual(register.frame.maxY, bottom, accuracy: 2)
        XCTAssertTrue(login.isHittable && register.isHittable)
        capture("favorites-guest-bottom-actions")
        login.tap()
        XCTAssertTrue(app.textFields["auth.email"].waitForExistence(timeout: 5))
        app.terminate()

        let large = launch(extra: [
            "-UIPreferredContentSizeCategoryName", "UICTContentSizeCategoryAccessibilityXXXL",
        ])
        large.buttons["Favorites"].firstMatch.tap()
        XCUIDevice.shared.orientation = .landscapeLeft
        defer { XCUIDevice.shared.orientation = .portrait }
        let registration = large.buttons["favorites.guest.register"]
        let viewport = large.scrollViews["favorites.guest"]
        XCTAssertTrue(viewport.waitForExistence(timeout: 5))
        for _ in 0..<6 where !registration.isHittable { viewport.swipeUp() }
        XCTAssertTrue(registration.isHittable)
        capture("favorites-guest-large-landscape")
        registration.tap()
        XCTAssertTrue(large.secureTextFields["auth.repeatPassword"].waitForExistence(timeout: 5))
    }

    @MainActor
    func testFeedStatesAreCentered() {
        for flag in ["--feed-ui-empty", "--feed-ui-error", "--feed-ui-loading"] {
            let app = launch(extra: [flag])
            assertCentered(app)
            capture(flag)
            app.terminate()
        }
    }

    @MainActor
    func testFavoritesEmptyAndAccountErrorsAreCentered() {
        let app = launch(extra: ["--auth-ui-signed-in"])
        app.buttons["Favorites"].firstMatch.tap()
        assertCentered(app)
        capture("favorites-empty-centered")
        app.terminate()
        let failed = launch(extra: ["--auth-ui-profile-error"])
        failed.buttons["Profile"].firstMatch.tap()
        assertCentered(failed)
        capture("profile-error-centered")
        failed.buttons["Favorites"].firstMatch.tap()
        assertCentered(failed)
        capture("favorites-account-error-centered")
    }

    @MainActor
    func testLargeTextStateActionsRemainReachableInLandscape() {
        let app = launch(extra: [
            "--auth-ui-profile-error", "-UIPreferredContentSizeCategoryName",
            "UICTContentSizeCategoryAccessibilityXXXL",
        ])
        app.buttons["Profile"].firstMatch.tap()
        XCUIDevice.shared.orientation = .landscapeLeft
        defer { XCUIDevice.shared.orientation = .portrait }
        let landscape = XCTNSPredicateExpectation(
            predicate: NSPredicate { _, _ in app.frame.width > app.frame.height }, object: nil)
        XCTAssertEqual(XCTWaiter.wait(for: [landscape], timeout: 5), .completed)
        let viewport = app.scrollViews["screenState.viewport"].firstMatch
        XCTAssertTrue(viewport.waitForExistence(timeout: 5))
        let logout = app.buttons["profile.logout"].firstMatch
        for _ in 0..<6 where !logout.isHittable { viewport.swipeUp() }
        XCTAssertTrue(logout.isHittable)
        capture("profile-large-landscape-actions")
    }

    @MainActor private func launch(extra: [String]) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["--feed-ui-fixture", "--auth-ui-fixture", "-AppleLanguages", "(en)"] + extra
        app.launch()
        XCTAssertTrue(app.buttons["Profile"].firstMatch.waitForExistence(timeout: 10))
        return app
    }
}
