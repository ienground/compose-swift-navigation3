import XCTest

/// State restoration: the system terminates the app in the background; on relaunch the back
/// stacks, the selected tab and the entries' rememberSaveable state (scroll included) come back.
final class RestorationUITests: ExampleAppTestCase {

    func testRestoreAfterBackgroundTermination() throws {
        launch(["-traceNav", "-freshStart"])

        log("PHASE R1 Settings counter (rememberSaveable)")
        app.tabBars.buttons["Settings"].tap(); pause(1)
        tap("Tap settings"); tap("Tap settings"); expectText("Settings taps: 2", "R1")
        app.tabBars.buttons["Home"].tap(); pause(1)

        log("PHASE R2 scroll Home (LazyListState)")
        at(200, 700).press(forDuration: 0.05, thenDragTo: at(200, 250), withVelocity: 600, thenHoldForDuration: 0.3)
        pause(1.5)

        log("PHASE R3 Home > Detail 42 > Detail 43")
        app.navigationBars.buttons["Open 42"].tap(); pause(1.5); expectText("Detail 42", "R3a")
        tap("Open Detail 43"); pause(1.5); expectText("Detail 43", "R3b")

        log("PHASE R4 background, then terminate (as the system does)")
        XCUIDevice.shared.press(.home); pause(2)
        app.terminate(); pause(1)

        log("PHASE R5 relaunch without -freshStart")
        launch(["-traceNav"])
        expectText("Detail 43", "R5 top screen restored")

        log("PHASE R6 back through the restored stack")
        tapBack(); pause(1.2); expectText("Detail 42", "R6a")
        tapBack(); pause(1.5)
        expectText("Open 42", "R6b Home")
        log("PHASE R6c Home restored (check scroll in NATIVE/screenshot)"); pause(1)

        log("PHASE R7 Settings counter restored")
        app.tabBars.buttons["Settings"].tap(); pause(1)
        expectText("Settings taps: 2", "R7")
        log("DONE")
    }

    /// The root declaration is app state too: a signed-out app comes back to its sign-in stack.
    func testRestoreSignedOut() throws {
        launch(["-traceNav", "-freshStart"])

        log("PHASE RS1 sign out, push Help")
        app.tabBars.buttons["Settings"].tap(); pause(1)
        tap("Sign out"); pause(1.5)
        tap("Sign-in help"); pause(1.5); expectText("Any account works in this example.", "RS1")

        log("PHASE RS2 background, then terminate (as the system does)")
        XCUIDevice.shared.press(.home); pause(2)
        app.terminate(); pause(1)

        log("PHASE RS3 relaunch without -freshStart -> sign-in stack, no tab bar")
        launch(["-traceNav"])
        expectText("Any account works in this example.", "RS3")
        XCTAssertFalse(app.tabBars.firstMatch.exists, "RS3: tab bar shown")
        tapBack(); pause(1.2); expectText("You are signed out.", "RS4")

        log("PHASE RS5 sign in -> tabs")
        tap("Sign in"); pause(1.5); expectText("Row 3", "RS5")
        log("DONE")
    }
}
