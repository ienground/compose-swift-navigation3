import XCTest

/// NativeBarItems: navigation bar buttons, menus, toolbar and title follow Compose state.
final class BarItemsUITests: ExampleAppTestCase {

    private var toolbar: XCUIElement { app.toolbars.firstMatch }

    /// The Detail toolbar's button; `app.toolbars` can match an invisible element on iOS 26,
    /// so presence of the toolbar is judged by its items (the app log has the native state).
    private var toolbarItemShown: Bool { app.buttons["Increase"].exists }

    func testBarItems() throws {
        launch()

        log("PHASE E1 Home: trailing button, no toolbar")
        XCTAssertTrue(app.navigationBars.buttons["Open 42"].waitForExistence(timeout: 5))
        XCTAssertFalse(toolbarItemShown, "Home must not show a toolbar")

        log("PHASE E2 Detail 3: toolbar shown, Decrease disabled at 0")
        tap("Row 3"); pause(1.5); expectText("Detail 3", "E2")
        XCTAssertTrue(app.buttons["Increase"].waitForExistence(timeout: 5), "Detail must show its toolbar")
        XCTAssertFalse(toolbar.buttons["Decrease"].isEnabled, "Decrease must be disabled at 0")

        log("PHASE E3 navigation bar button -> ViewModel, title follows")
        app.navigationBars.buttons["Add one"].tap(); pause(0.8)
        expectText("Count: 1", "E3")
        XCTAssertTrue(app.navigationBars.staticTexts["Detail 3 (1)"].waitForExistence(timeout: 3), "title override")

        log("PHASE E4 toolbar buttons")
        toolbar.buttons["Increase"].tap(); pause(0.8); expectText("Count: 2", "E4a")
        XCTAssertTrue(toolbar.buttons["Decrease"].isEnabled, "Decrease enabled above 0")
        toolbar.buttons["Decrease"].tap(); pause(0.8); expectText("Count: 1", "E4b")

        log("PHASE E5 menu -> destructive action")
        app.navigationBars.buttons["More"].tap(); pause(1)
        tap("Reset count"); pause(0.8); expectText("Count: 0", "E5")
        XCTAssertTrue(app.navigationBars.staticTexts["Detail 3"].waitForExistence(timeout: 3), "title back to metadata")

        // Detail 4 is pushed above Detail 3, which hides the tab bar: UIKit keeps it hidden for
        // every screen above one with hidesBottomBarWhenPushed (see NavigationUITests N13 for
        // Detail 4 pushed from Home, where it stays visible).
        log("PHASE E6 menu -> navigation (Detail 4 has a toolbar)")
        app.navigationBars.buttons["More"].tap(); pause(1)
        tap("Open Detail 4"); pause(1.5); expectText("Detail 4", "E6")
        XCTAssertTrue(toolbarItemShown, "Detail 4 toolbar")

        log("PHASE E7 back to Home -> toolbar hidden")
        tapBack(); pause(1.2); tapBack(); pause(1.5)
        expectText("Row 3", "E7")
        XCTAssertFalse(toolbarItemShown, "toolbar must hide on Home")

        log("PHASE E8 Home trailing button")
        app.navigationBars.buttons["Open 42"].tap(); pause(1.5); expectText("Detail 42", "E8")
        log("DONE")
    }
}
