import XCTest

extension XCTestCase {
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
