import UIKit
import XCTest

final class AuthCodeFocusUITests: XCTestCase {
    @MainActor
    func testConfirmationFocusAndResend() {
        verifyFocusAndResend(recovery: false)
    }

    @MainActor
    func testRecoveryFocusAndResend() {
        verifyFocusAndResend(recovery: true)
    }

    @MainActor
    func testLeavingPendingConfirmationDoesNotRestoreKeyboard() {
        let app = openCode(recovery: false, extra: ["--auth-ui-slow-code"])
        app.textFields["auth.code"].typeText("111111")
        assertKeyboard(false, in: app)
        app.navigationBars.buttons["BackButton"].tap()
        app.buttons["auth.close"].tap()
        XCTAssertTrue(app.buttons["profile.login"].waitForExistence(timeout: 5))

        let keyboardReturns = XCTNSPredicateExpectation(
            predicate: NSPredicate { _, _ in self.keyboardVisible(in: app) }, object: nil)
        keyboardReturns.isInverted = true
        wait(for: [keyboardReturns], timeout: 9)
        capture("otp-dismissed-during-request")
    }

    @MainActor
    func testPastedRecoveryCodeSubmits() {
        verifyPastedCode(recovery: true)
    }

    @MainActor
    func testPastedConfirmationCodeSubmits() {
        verifyPastedCode(recovery: false)
    }

    @MainActor
    func testRecoveryErrorInLandscapeWithLargeText() {
        let app = openCode(
            recovery: true,
            extra: [
                "--profile-ui-dark", "-UIPreferredContentSizeCategoryName", "UICTContentSizeCategoryAccessibilityXXXL",
            ])
        XCUIDevice.shared.orientation = .landscapeLeft
        defer { XCUIDevice.shared.orientation = .portrait }

        app.textFields["auth.code"].typeText("333333")
        assertKeyboard(false, in: app)
        waitUntilEnabled(app.textFields["auth.code"])
        let retry = app.buttons["auth.retryCode"]
        for _ in 0..<5 where !retry.isHittable { app.collectionViews.firstMatch.swipeUp() }
        XCTAssertTrue(retry.isHittable)
        capture("otp-network-landscape-large-text")
    }

    @MainActor
    private func verifyPastedCode(recovery: Bool) {
        let app = openCode(recovery: recovery)
        UIPasteboard.general.string = "012345"
        let keyboardPopover = app.otherElements["PopoverDismissRegion"]
        if keyboardPopover.exists { keyboardPopover.tap() }

        app.textFields["auth.code"].press(forDuration: 1.2)
        let paste = app.menuItems.matching(NSPredicate(format: "label IN %@", ["Paste", "Вставить"])).firstMatch
        XCTAssertTrue(paste.waitForExistence(timeout: 5))
        paste.tap()

        assertKeyboard(false, in: app)
        if recovery {
            XCTAssertTrue(app.navigationBars["Новый пароль"].waitForExistence(timeout: 8))
        } else {
            XCTAssertTrue(app.buttons["profile.logout"].waitForExistence(timeout: 8))
        }
    }

    @MainActor
    private func verifyFocusAndResend(recovery: Bool) {
        let app = openCode(recovery: recovery, extra: ["--auth-ui-resend-error-once"])
        let code = app.textFields["auth.code"]

        for (value, restoresFocus) in [
            ("111111", true), ("222222", true), ("333333", false), ("444444", false), ("555555", false),
        ] {
            if !keyboardVisible(in: app) { code.tap() }
            code.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: 6) + String(value.prefix(5)))
            XCTAssertTrue(code.isEnabled)
            assertKeyboard(true, in: app)
            code.typeText(String(value.suffix(1)))
            assertKeyboard(false, in: app)
            waitUntilEnabled(code)
            XCTAssertTrue(app.staticTexts["auth.error"].exists)
            XCTAssertEqual(code.value as? String, value)
            XCTAssertEqual(app.buttons["auth.retryCode"].exists, !restoresFocus)
            if !restoresFocus {
                XCTAssertEqual(app.buttons["auth.retryCode"].label, "Проверить код ещё раз")
            }
            assertKeyboard(restoresFocus, in: app)
            capture("otp-\(recovery ? "recovery" : "confirmation")-\(value)")
        }

        app.buttons["auth.retryCode"].tap()
        assertKeyboard(false, in: app)
        waitUntilEnabled(code)
        XCTAssertEqual(code.value as? String, "555555")

        let resend = app.buttons["auth.resend"]
        waitUntilEnabled(resend, timeout: 65)
        resend.tap()
        waitUntilEnabled(code)
        XCTAssertTrue(app.staticTexts["auth.error"].exists)
        XCTAssertEqual(code.value as? String, "555555")
        assertKeyboard(false, in: app)
        XCTAssertFalse(app.buttons["auth.retryCode"].exists)

        resend.tap()
        assertKeyboard(true, in: app)
        XCTAssertFalse(app.staticTexts["auth.error"].exists)
        XCTAssertNotEqual(code.value as? String, "555555")
        code.typeText("012345")
        assertKeyboard(false, in: app)
        if recovery {
            XCTAssertTrue(app.navigationBars["Новый пароль"].waitForExistence(timeout: 8))
        } else {
            XCTAssertTrue(app.buttons["profile.logout"].waitForExistence(timeout: 8))
            assertKeyboard(false, in: app)
        }
    }

    @MainActor
    private func openCode(recovery: Bool, extra: [String] = []) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments =
            [
                "--auth-ui-fixture", "--feed-ui-fixture", "--auth-ui-unconfirmed", "--auth-ui-code-errors",
                "-AppleLanguages", "(ru)", "-AppleLocale", "ru_RU",
            ] + extra
        app.launch()
        selectSection("Профиль", in: app)
        app.buttons["profile.login"].tap()
        let email = app.textFields["auth.email"]
        XCTAssertTrue(email.waitForExistence(timeout: 5))
        email.tap()
        email.typeText("reader@example.test")
        if recovery {
            if !app.buttons["auth.forgotPassword"].isHittable { hideKeyboard(app) }
            app.buttons["auth.forgotPassword"].tap()
            app.buttons["auth.submit"].tap()
        } else {
            hideKeyboard(app)
            let password = app.secureTextFields["auth.password"]
            password.tap()
            password.typeText("fixture-password")
            app.buttons["auth.submit"].tap()
            XCTAssertTrue(app.buttons["auth.openConfirmation"].waitForExistence(timeout: 5))
            app.buttons["auth.openConfirmation"].tap()
        }
        XCTAssertTrue(app.textFields["auth.code"].waitForExistence(timeout: 5))
        assertKeyboard(true, in: app)
        // iPad retains its own system keyboard-dismiss key.
        if UIDevice.current.userInterfaceIdiom == .phone {
            XCTAssertFalse(app.buttons["Скрыть клавиатуру"].exists)
        }
        return app
    }

    @MainActor
    private func keyboardVisible(in app: XCUIApplication) -> Bool {
        app.keyboards.firstMatch.exists || app.otherElements["PopoverDismissRegion"].exists
    }

    @MainActor
    private func assertKeyboard(_ visible: Bool, in app: XCUIApplication) {
        let expectation = XCTNSPredicateExpectation(
            predicate: NSPredicate { _, _ in self.keyboardVisible(in: app) == visible }, object: nil)
        XCTAssertEqual(XCTWaiter.wait(for: [expectation], timeout: visible ? 5 : 2), .completed)
    }

    @MainActor
    private func waitUntilEnabled(_ element: XCUIElement, timeout: TimeInterval = 8) {
        let expectation = XCTNSPredicateExpectation(predicate: NSPredicate(format: "enabled == true"), object: element)
        XCTAssertEqual(XCTWaiter.wait(for: [expectation], timeout: timeout), .completed)
    }
}
