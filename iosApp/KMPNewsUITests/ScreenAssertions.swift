import XCTest

extension XCTestCase {
    @MainActor
    func enterRegistrationNames(firstName: String, lastName: String, in app: XCUIApplication) {
        for (identifier, value) in [("profile.lastName", lastName), ("profile.firstName", firstName)] {
            let field = app.textFields[identifier]
            for _ in 0..<5 {
                if field.isHittable { break }
                app.swipeDown()
            }

            field.tap()
            field.typeText(value)
        }
    }

    @MainActor
    func hideKeyboard(_ app: XCUIApplication) {
        let popover = app.otherElements["PopoverDismissRegion"]
        if popover.exists { popover.tap() }

        let keyboard = app.keyboards.firstMatch

        guard keyboard.exists else { return }

        let done = keyboard.descendants(matching: .any).matching(
            NSPredicate(format: "label IN %@", ["Done", "Готово"])
        ).firstMatch
        let systemDismiss = keyboard.descendants(matching: .any).matching(
            NSPredicate(format: "label IN %@", ["Hide keyboard", "Скрыть клавиатуру"])
        ).firstMatch
        if done.exists, done.isHittable {
            done.tap()
        } else if systemDismiss.exists, systemDismiss.isHittable {
            systemDismiss.tap()
        } else {
            let form = app.collectionViews.firstMatch
            for _ in 0..<3 where keyboard.exists {
                form.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.3))
                    .press(
                        forDuration: 0.1,
                        thenDragTo: form.coordinate(
                            withNormalizedOffset: CGVector(dx: 0.5, dy: 0.85)))
            }
        }

        XCTAssertTrue(keyboard.waitForNonExistence(timeout: 3))
    }

    @MainActor
    func enterNewPassword(_ value: String, into field: XCUIElement, in app: XCUIApplication) {
        field.tap()
        field.typeText(String(value.prefix(1)))

        // iOS may present a system strong-password sheet after the first character.
        if app.buttons["GenerateStrongPasswordButton"].waitForExistence(timeout: 2) {
            app.buttons["xmark"].firstMatch.tap()
            field.tap()
            field.typeText(value)
            return
        }

        field.typeText(String(value.dropFirst()))
    }

    @MainActor
    func selectSection(_ title: String, in app: XCUIApplication) {
        func tapItem() {
            if app.tabBars.buttons[title].exists {
                app.tabBars.buttons[title].tap()
            } else if app.cells[title].firstMatch.exists {
                app.cells[title].firstMatch.tap()
            } else {
                let items = app.buttons.matching(identifier: title).allElementsBoundByIndex
                (items.last(where: { $0.isHittable }) ?? app.buttons[title].firstMatch).tap()
            }
        }

        tapItem()
        // At accessibility sizes the first tap can reveal an overflowing iPad tab.
        if !app.navigationBars[title].waitForExistence(timeout: 3) { tapItem() }
        XCTAssertTrue(app.navigationBars[title].waitForExistence(timeout: 3))
    }

    @MainActor
    func assertCentered(
        _ app: XCUIApplication, includesSearchField: Bool = false,
        file: StaticString = #filePath, line: UInt = #line
    ) {
        let content = app.otherElements["screenState.content"].firstMatch
        let viewport = app.scrollViews["screenState.viewport"].firstMatch

        XCTAssertTrue(content.waitForExistence(timeout: 5), file: file, line: line)
        XCTAssertTrue(viewport.exists, file: file, line: line)
        guard content.exists, viewport.exists, content.frame.height <= viewport.frame.height else { return }

        let navigation = app.navigationBars.firstMatch
        let top = navigation.exists ? max(viewport.frame.minY, navigation.frame.maxY) : viewport.frame.minY
        var bottom = viewport.frame.maxY
        var occluders = [app.keyboards.firstMatch, app.tabBars.firstMatch]
        if includesSearchField { occluders.append(app.searchFields.firstMatch) }

        for element in occluders {
            if element.exists, element.frame.minY > top { bottom = min(bottom, element.frame.minY) }
        }

        // AX scroll frames extend under system bars; allow the home-indicator safe inset.
        XCTAssertEqual(content.frame.midY, (top + bottom) / 2, accuracy: 32, file: file, line: line)
    }

    @MainActor
    func capture(_ name: String) {
        // Full-screen capture avoids application-frame cropping after device rotation.
        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
