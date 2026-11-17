import XCTest

final class ScrollUITests: ExampleAppTestCase {
    func testTabSwitchKeepsScroll() {
        launch()
        at(200, 700).press(forDuration: 0.05, thenDragTo: at(200, 250), withVelocity: 600, thenHoldForDuration: 0.3)
        pause(1)
        let row = element("Row 12")
        XCTAssertTrue(row.waitForExistence(timeout: 3))
        let before = row.frame
        app.tabBars.buttons["Settings"].tap()
        app.tabBars.buttons["Home"].tap(); pause(1)
        XCTAssertTrue(row.exists)
        XCTAssertEqual(before.minY, row.frame.minY, accuracy: 1)
    }

    func testLandscape() {
        launch()
        XCUIDevice.shared.orientation = .landscapeLeft; pause(1)
        tap("Row 2")
        expectText("Selected item: 2", "가로 화면 상세")
        tapBack()
        expectText("Row 2", "가로 화면 루트")
    }
}
