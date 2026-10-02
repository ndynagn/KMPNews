import XCTest

final class FavoriteSignInUITests: XCTestCase {
    @MainActor
    func testInvitationDismissalAndAuthRoutes() {
        let app = launch()
        let save = app.buttons["favorites.save.fixture-0"]
        XCTAssertTrue(save.waitForExistence(timeout: 10))
        save.tap()
        let login = app.buttons["favorites.login"]
        XCTAssertTrue(login.waitForExistence(timeout: 5))
        capture(app, "favorite-invitation-light")
        app.swipeDown()
        XCTAssertTrue(login.exists, "The invitation must not dismiss with a swipe")
        app.buttons["favorites.close"].tap()
        XCTAssertTrue(save.waitForExistence(timeout: 5))
        save.tap()
        login.tap()
        XCTAssertTrue(app.textFields["auth.email"].waitForExistence(timeout: 5))
        verifyAuthCannotSwipeClosed(app)
        capture(app, "favorite-large-auth-sheet")
        app.buttons["auth.close"].tap()
        XCTAssertTrue(save.waitForExistence(timeout: 5))
        XCTAssertEqual(save.label, "Добавить в избранное")
        save.tap()
        app.buttons["favorites.register"].tap()
        XCTAssertTrue(app.secureTextFields["auth.repeatPassword"].waitForExistence(timeout: 5))
        app.buttons["auth.close"].tap()
    }

    @MainActor
    func testProfileAuthRequiresExplicitCloseAtEveryStep() {
        let app = launch()
        app.tabBars.buttons["Профиль"].tap()
        let login = app.buttons["profile.login"]
        XCTAssertTrue(login.waitForExistence(timeout: 5))
        capture(app, "profile-registration-prompt")
        login.tap()
        XCTAssertTrue(app.textFields["auth.email"].waitForExistence(timeout: 5))
        verifyAuthCannotSwipeClosed(app)
        capture(app, "profile-large-auth-sheet")
        app.buttons["auth.register"].tap()
        XCTAssertTrue(app.secureTextFields["auth.repeatPassword"].waitForExistence(timeout: 5))
        verifyAuthCannotSwipeClosed(app)
        app.buttons["auth.close"].tap()
        XCTAssertTrue(login.waitForExistence(timeout: 5))
        XCTAssertFalse(app.textFields["auth.email"].exists)
    }

    @MainActor
    private func verifyAuthCannotSwipeClosed(_ app: XCUIApplication) {
        let bar = app.navigationBars.firstMatch
        let start = bar.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.1))
        let end = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.85))
        start.press(forDuration: 0.1, thenDragTo: end)
        XCTAssertTrue(app.buttons["auth.close"].exists)
        XCTAssertTrue(app.textFields["auth.email"].exists)
    }

    @MainActor
    func testToggleAndFavoritesListStayInSync() {
        let app = launch()
        let star = app.buttons["favorites.save.fixture-0"]
        XCTAssertTrue(star.waitForExistence(timeout: 10))
        star.tap()
        app.buttons["favorites.login"].tap()
        signIn(app)
        let remove = app.buttons.matching(identifier: "favorites.save.fixture-0").matching(
            NSPredicate(format: "label == %@", "Удалить из избранного")
        ).firstMatch
        XCTAssertTrue(remove.waitForExistence(timeout: 10))
        capture(app, "favorite-filled-star")
        app.tabBars.buttons["Избранное"].tap()
        XCTAssertTrue(app.otherElements["feed.article.fixture-0"].waitForExistence(timeout: 5))
        capture(app, "favorites-list-content")
        app.buttons["favorites.save.fixture-0"].tap()
        XCTAssertTrue(app.staticTexts["Пока нет избранных новостей"].waitForExistence(timeout: 5))
        capture(app, "favorites-list-empty")
        app.tabBars.buttons["Новости"].tap()
        XCTAssertEqual(star.label, "Добавить в избранное")
        star.tap()
        XCTAssertTrue(remove.waitForExistence(timeout: 5))
        remove.tap()
        let add = app.buttons.matching(identifier: "favorites.save.fixture-0").matching(
            NSPredicate(format: "label == %@", "Добавить в избранное")
        ).firstMatch
        XCTAssertTrue(add.waitForExistence(timeout: 5))
    }

    @MainActor
    func testFailedRemovalPreservesSavedCard() {
        let app = launch(extra: ["--favorites-ui-remove-error"])
        let star = app.buttons["favorites.save.fixture-0"]
        XCTAssertTrue(star.waitForExistence(timeout: 10))
        star.tap()
        app.buttons["favorites.login"].tap()
        signIn(app)
        let remove = app.buttons.matching(identifier: "favorites.save.fixture-0").matching(
            NSPredicate(format: "label == %@", "Удалить из избранного")
        ).firstMatch
        XCTAssertTrue(remove.waitForExistence(timeout: 10))
        remove.tap()
        XCTAssertTrue(app.alerts.firstMatch.waitForExistence(timeout: 5))
        app.alerts.buttons["Закрыть"].tap()
        XCTAssertTrue(remove.exists)
        app.tabBars.buttons["Избранное"].tap()
        XCTAssertTrue(app.otherElements["feed.article.fixture-0"].waitForExistence(timeout: 5))
        capture(app, "favorites-retained-after-delete-error")
    }

    @MainActor
    func testSuccessfulLoginSavesOriginalArticle() {
        let app = launch()
        let save = app.buttons["favorites.save.fixture-0"]
        XCTAssertTrue(save.waitForExistence(timeout: 10))
        save.tap()
        app.buttons["favorites.login"].tap()
        signIn(app)
        let saved = app.buttons.matching(identifier: "favorites.save.fixture-0").matching(
            NSPredicate(format: "label == %@", "Удалить из избранного")
        ).firstMatch
        XCTAssertTrue(saved.waitForExistence(timeout: 10))
        capture(app, "favorite-saved-after-login")
    }

    @MainActor
    func testFailedSaveCanRetry() {
        let app = launch(extra: ["--favorites-ui-error"])
        let save = app.buttons["favorites.save.fixture-0"]
        XCTAssertTrue(save.waitForExistence(timeout: 10))
        save.tap()
        app.buttons["favorites.login"].tap()
        signIn(app)
        let retry = app.alerts.buttons["Повторить"]
        XCTAssertTrue(retry.waitForExistence(timeout: 10))
        capture(app, "favorite-save-error")
        retry.tap()
        let saved = app.buttons.matching(identifier: "favorites.save.fixture-0").matching(
            NSPredicate(format: "label == %@", "Удалить из избранного")
        ).firstMatch
        XCTAssertTrue(saved.waitForExistence(timeout: 10))
    }

    @MainActor
    func testConfirmedRegistrationSavesOriginalArticle() {
        let app = launch()
        let save = app.buttons["favorites.save.fixture-0"]
        XCTAssertTrue(save.waitForExistence(timeout: 10))
        save.tap()
        app.buttons["favorites.register"].tap()
        let email = app.textFields["auth.email"]
        XCTAssertTrue(email.waitForExistence(timeout: 5))
        email.tap()
        email.typeText("reader@example.test")
        app.secureTextFields["auth.password"].tap()
        app.secureTextFields["auth.password"].typeText("Fixture123!")
        app.secureTextFields["auth.repeatPassword"].tap()
        app.secureTextFields["auth.repeatPassword"].typeText("Fixture123!")
        app.buttons["auth.submit"].tap()
        let code = app.textFields["auth.code"]
        XCTAssertTrue(code.waitForExistence(timeout: 5))
        code.tap()
        code.typeText("012345")
        let saved = app.buttons.matching(identifier: "favorites.save.fixture-0").matching(
            NSPredicate(format: "label == %@", "Удалить из избранного")
        ).firstMatch
        XCTAssertTrue(saved.waitForExistence(timeout: 10))
        capture(app, "favorite-saved-after-registration")
    }

    @MainActor
    private func signIn(_ app: XCUIApplication) {
        let email = app.textFields["auth.email"]
        XCTAssertTrue(email.waitForExistence(timeout: 5))
        email.tap()
        email.typeText("reader@example.test")
        let password = app.secureTextFields["auth.password"]
        password.tap()
        password.typeText("Fixture123!")
        app.buttons["auth.submit"].tap()
    }

    @MainActor
    func testLargeTextInvitation() {
        let app = launch(extra: ["-UIPreferredContentSizeCategoryName", "UICTContentSizeCategoryAccessibilityXXXL"])
        let save = app.buttons["favorites.save.fixture-0"]
        XCTAssertTrue(save.waitForExistence(timeout: 10))
        save.tap()
        XCTAssertTrue(app.buttons["favorites.close"].waitForExistence(timeout: 5))
        app.swipeUp()
        XCTAssertTrue(app.buttons["favorites.register"].isHittable)
        capture(app, "favorite-invitation-large-text")
    }

    @MainActor
    private func launch(extra: [String] = []) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["--feed-ui-fixture", "--auth-ui-fixture", "-AppleLanguages", "(ru)"] + extra
        app.launch()
        return app
    }

    @MainActor
    private func capture(_ app: XCUIApplication, _ name: String) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
