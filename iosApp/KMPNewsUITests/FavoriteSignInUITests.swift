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
        capture("favorite-invitation-light")
        app.swipeDown()
        XCTAssertTrue(login.exists, "The invitation must not dismiss with a swipe")
        app.buttons["favorites.close"].tap()
        XCTAssertTrue(save.waitForExistence(timeout: 5))
        save.tap()
        login.tap()
        XCTAssertTrue(app.textFields["auth.email"].waitForExistence(timeout: 5))
        verifyAuthCannotSwipeClosed(app)
        capture("favorite-large-auth-sheet")
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
        capture("profile-registration-prompt")
        login.tap()
        XCTAssertTrue(app.textFields["auth.email"].waitForExistence(timeout: 5))
        verifyAuthCannotSwipeClosed(app)
        capture("profile-large-auth-sheet")
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
        XCTAssertTrue(app.staticTexts["Новость добавлена в избранное"].waitForExistence(timeout: 5))
        capture("favorite-filled-star")
        app.tabBars.buttons["Избранное"].tap()
        XCTAssertTrue(app.otherElements["feed.article.fixture-0"].waitForExistence(timeout: 5))
        capture("favorites-list-content")
        app.buttons["favorites.save.fixture-0"].tap()
        XCTAssertTrue(app.otherElements["feed.article.fixture-0"].exists)
        XCTAssertEqual(app.buttons["favorites.save.fixture-0"].label, "Добавить в избранное")
        capture("favorite-removed-retained")
        let list = app.scrollViews["favorites.list"]
        list.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.3)).press(
            forDuration: 0.1, thenDragTo: list.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.85)))
        XCTAssertTrue(app.staticTexts["Пока нет избранных новостей"].waitForExistence(timeout: 5))
        capture("favorites-list-empty")
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
    func testStarChangesBeforeSuccessNotice() {
        let app = launch(extra: ["--favorites-ui-slow"])
        app.tabBars.buttons["Профиль"].tap()
        app.buttons["profile.login"].tap()
        signIn(app)
        app.tabBars.buttons["Новости"].tap()
        let star = app.buttons["favorites.save.fixture-0"]
        XCTAssertTrue(star.waitForExistence(timeout: 5))

        star.tap()

        XCTAssertEqual(star.label, "Удалить из избранного")
        XCTAssertFalse(app.staticTexts["Новость добавлена в избранное"].exists)
        capture("favorite-optimistic-star")
        XCTAssertTrue(app.staticTexts["Новость добавлена в избранное"].waitForExistence(timeout: 10))
        capture("favorite-confirmed-notice")
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
        capture("favorites-retained-after-delete-error")
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
        capture("favorite-saved-after-login")
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
        capture("favorite-save-error")
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
        capture("favorite-saved-after-registration")
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
        capture("favorite-invitation-large-text")
    }

    @MainActor
    private func launch(extra: [String] = []) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["--feed-ui-fixture", "--auth-ui-fixture", "-AppleLanguages", "(ru)"] + extra
        app.launch()
        return app
    }
}
