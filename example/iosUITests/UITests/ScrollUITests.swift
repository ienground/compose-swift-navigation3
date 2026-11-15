import XCTest

/// Scroll bridge: the native navigation bar tracks the Compose list on Home.
/// Check the bar height per phase in the NATIVE log lines (timeline.py).
final class ScrollUITests: ExampleAppTestCase {

    func testGestures() throws {
        launch()
        log("PHASE 1 rest"); pause(2)

        log("PHASE 2 drag up 30pt and hold (large title collapses with the finger)")
        at(200, 600).press(forDuration: 0.1, thenDragTo: at(200, 570), withVelocity: 60, thenHoldForDuration: 3)
        log("PHASE 2 released (snaps)"); pause(2.5)

        log("PHASE 3 fling up")
        at(200, 700).press(forDuration: 0.05, thenDragTo: at(200, 300), withVelocity: 2500, thenHoldForDuration: 0)
        log("PHASE 3 released"); pause(3)

        log("PHASE 4 tap status bar (scroll to top)")
        at(200, 8).tap(); pause(3)

        log("PHASE 5 pull down and hold (overscroll)")
        at(200, 300).press(forDuration: 0.1, thenDragTo: at(200, 480), withVelocity: 300, thenHoldForDuration: 3)
        log("PHASE 5 released"); pause(2.5)

        let card = app.staticTexts["Card 1"]
        let before = card.frame
        log("PHASE 6 horizontal swipe on LazyRow")
        let start = card.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5))
        start.press(forDuration: 0.05, thenDragTo: start.withOffset(CGVector(dx: -250, dy: 4)), withVelocity: 800, thenHoldForDuration: 0.2)
        pause(1.5)
        XCTAssertNotEqual(before.minX, card.frame.minX, "LazyRow did not scroll horizontally")
        log("DONE")
    }

    func testBottomEdge() throws {
        launch()
        for i in 1...6 {
            log("PHASE C fling \(i)")
            at(200, 750).press(forDuration: 0.05, thenDragTo: at(200, 150), withVelocity: 4000, thenHoldForDuration: 0)
            pause(1.5)
        }
        expectText("Row 99", "end of list")
        log("PHASE C pull up at end and hold")
        at(200, 600).press(forDuration: 0.1, thenDragTo: at(200, 420), withVelocity: 300, thenHoldForDuration: 3)
        log("PHASE C released"); pause(2.5)
        expectText("Row 99", "after bounce")
    }

    func testTabSwitchKeepsScroll() throws {
        launch()
        at(200, 700).press(forDuration: 0.05, thenDragTo: at(200, 250), withVelocity: 600, thenHoldForDuration: 0.3)
        pause(1.5)
        let row = element("Row 12")
        XCTAssertTrue(row.waitForExistence(timeout: 3))
        let before = row.frame
        log("PHASE T1 switch tabs and back")
        app.tabBars.buttons["Settings"].tap(); pause(1)
        app.tabBars.buttons["Home"].tap(); pause(1.5)
        XCTAssertTrue(row.waitForExistence(timeout: 3))
        log("CHECK T1 Row 12 y before=\(before.minY) after=\(row.frame.minY)")
        XCTAssertEqual(before.minY, row.frame.minY, accuracy: 1, "scroll position kept across tabs")
    }

    func testLandscape() throws {
        launch()
        log("PHASE L1 rotate to landscape")
        XCUIDevice.shared.orientation = .landscapeLeft; pause(2)
        expectText("Row 0", "L1")
        // Row 0: lower rows sit under the tab bar or below the edge in landscape.
        tap("Row 0"); pause(1.5); expectText("Detail 0", "L2")
        tapBack(); pause(1.5); expectText("Row 0", "L3")
        log("PHASE L4 back to portrait")
        XCUIDevice.shared.orientation = .portrait; pause(2)
        expectText("Row 0", "L4")
    }
}
