import XCTest

/// NativeSearchBar: the screen's native search field (UISearchController) drives Compose state.
final class SearchUITests: ExampleAppTestCase {

    func testSearch() throws {
        launch()
        let field = app.searchFields.firstMatch
        if !field.waitForExistence(timeout: 3) {
            log("PHASE S0 pull down to reveal the search field")
            at(200, 300).press(forDuration: 0.05, thenDragTo: at(200, 450), withVelocity: 400, thenHoldForDuration: 0.2)
            pause(1)
        }
        XCTAssertTrue(field.waitForExistence(timeout: 3), "native search field")

        log("PHASE S1 type 'Row 4' -> rows filtered in Compose")
        field.tap(); pause(0.5)
        field.typeText("Row 4"); pause(1.5)
        // Without the software keyboard nothing scrolls and S1b/S2 test nothing (run.sh keeps it shown).
        XCTAssertTrue(app.keyboards.firstMatch.exists, "software keyboard must be shown (hardware keyboard minimization?)")
        expectText("Row 40", "S1")
        XCTAssertFalse(element("Row 3").exists, "Row 3 must be filtered out")

        log("PHASE S1b the last result can be scrolled above the keyboard (keyboard inset)")
        at(200, 330).press(forDuration: 0.05, thenDragTo: at(200, 110), withVelocity: 600, thenHoldForDuration: 0.3)
        pause(1.5)
        let last = element("Row 49")
        XCTAssertTrue(last.waitForExistence(timeout: 3), "Row 49 exists")
        log("CHECK S1b Row 49 hittable=\(last.isHittable) frame=\(last.frame)")
        XCTAssertTrue(last.isHittable, "Row 49 must not stay behind the keyboard")

        log("PHASE S2 clear the field -> rows the filter hid are back")
        field.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: 5)); pause(1.5)
        // LazyColumn keeps its position by item key (it stays at Row 40), so check the
        // neighbours of Row 40...49 that the filter had hidden.
        let hidden = ["Row 38", "Row 39", "Row 50", "Row 51"]
        let back = app.descendants(matching: .any).matching(NSPredicate(format: "identifier IN %@ OR label IN %@", hidden, hidden))
        log("CHECK S2 previously hidden rows shown: \(back.count)")
        XCTAssertGreaterThan(back.count, 0, "clearing the query shows the other rows again")
        log("DONE")
    }
}
