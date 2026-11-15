import XCTest

/// Native navigation <-> Navigation 3 back stack sync. Compare the KOTLIN and NATIVE log lines.
final class NavigationUITests: ExampleAppTestCase {

    func testNavigation() throws {
        launch()
        log("PHASE N1 start"); expectText("Row 3", "N1")

        log("PHASE N2 tap Row 3 -> push Detail 3 (tab bar hidden)")
        tap("Row 3"); pause(1.2); expectText("Detail 3", "N2")

        log("PHASE N3 increment twice (ViewModel)")
        tap("Increment"); tap("Increment"); expectText("Count: 2", "N3")

        log("PHASE N4 push Detail 4")
        tap("Open Detail 4"); pause(1.2); expectText("Detail 4", "N4")

        log("PHASE N5 native back button -> Detail 3 keeps its ViewModel")
        tapBack(); pause(1.2); expectText("Count: 2", "N5")

        log("PHASE N6 cancelled edge swipe -> stays on Detail 3")
        edgeSwipe(distance: 50, velocity: 80, hold: 0.3); pause(1.5)
        expectText("Count: 2", "N6")

        log("PHASE N7 edge swipe back -> Home (Detail 3 ViewModel cleared)")
        edgeSwipe(distance: 320, velocity: 1200, hold: 0); pause(1.5)
        expectText("Row 3", "N7")

        log("PHASE N8 push Detail 5, then the same route again (duplicate content key)")
        tap("Row 5"); pause(1.2); expectText("Detail 5", "N8a")
        tap("Open same Detail"); pause(1.5); expectText("Detail 5", "N8b")

        log("PHASE N9 back -> first Detail 5")
        tapBack(); pause(1.5); expectText("Detail 5", "N9")

        log("PHASE N10 replace top with Detail 105")
        tap("Replace with Detail 105"); pause(1.5); expectText("Detail 105", "N10")

        log("PHASE N11 pop to root")
        tap("Pop to root"); pause(1.5); expectText("Row 3", "N11")

        log("PHASE N12 deep link Home > Detail 3 > Detail 7, then back")
        tap("Deep link 3 > 7"); pause(1.5); expectText("Detail 7", "N12a")
        tapBack(); pause(1.2); expectText("Detail 3", "N12b")
        tap("Pop to root"); pause(1.5)

        log("PHASE N13 Detail 4 keeps the tab bar; switching tabs keeps each stack")
        tap("Row 4"); pause(1.2); expectText("Detail 4", "N13a")
        app.tabBars.buttons["Settings"].tap(); pause(1.2)
        tap("Tap settings"); expectText("Settings taps: 1", "N13b")
        app.tabBars.buttons["Home"].tap(); pause(1.2); expectText("Detail 4", "N13c")
        app.tabBars.buttons["Settings"].tap(); pause(1.2); expectText("Settings taps: 1", "N13d")
        app.tabBars.buttons["Home"].tap(); pause(1.2)

        log("PHASE N14 re-tap the selected tab -> UIKit pops to root")
        app.tabBars.buttons["Home"].tap(); pause(1.5); expectText("Row 3", "N14")
        log("DONE")
    }

    /// NativeTabs' shared back stack: Profile opens over whichever tab is selected, hides the tab bar,
    /// and follows the selected tab when the app switches tabs.
    func testSharedStack() throws {
        launch()
        log("PHASE H1 Home: open the shared Profile -> pushed on Home, tab bar hidden")
        tap("Open profile"); pause(1.2); expectText("Profile edits: 0", "H1")
        XCTAssertTrue(app.navigationBars["Profile"].exists, "H1: Profile title missing")
        XCTAssertFalse(tabBarShown(), "H1: tab bar still shown")

        log("PHASE H2 saveable state, then Detail 50 on the shared stack (an even Detail, still no tab bar)")
        tap("Edit profile"); expectText("Profile edits: 1", "H2a")
        tap("Open Detail 50"); pause(1.2); expectText("Detail 50", "H2b")
        XCTAssertFalse(tabBarShown(), "H2: tab bar shown over a shared screen")

        log("PHASE H3 edge swipe back -> Profile keeps its state")
        edgeSwipe(distance: 320, velocity: 1200, hold: 0); pause(1.5)
        expectText("Profile edits: 1", "H3")

        log("PHASE H4 back button -> Home with the tab bar")
        tapBack(); pause(1.2); expectText("Row 3", "H4")
        XCTAssertTrue(tabBarShown(), "H4: tab bar not back")

        log("PHASE H5 Settings opens the same Profile (its popped state was cleared)")
        app.tabBars.buttons["Settings"].tap(); pause(1)
        tap("Open profile from settings"); pause(1.2); expectText("Profile edits: 0", "H5")
        XCTAssertFalse(tabBarShown(), "H5: tab bar still shown")

        log("PHASE H6 the app selects Gallery -> Profile moves to Gallery with its state")
        tap("Edit profile"); expectText("Profile edits: 1", "H6a")
        tap("Switch to Gallery"); pause(2); expectText("Profile edits: 1", "H6b")
        tapBack(); pause(1.2)
        XCTAssertTrue(app.navigationBars["Gallery"].waitForExistence(timeout: 5), "H6c: not back on Gallery")
        XCTAssertTrue(tabBarShown(), "H6c: tab bar not back")

        log("PHASE H7 Settings no longer shows Profile")
        app.tabBars.buttons["Settings"].tap(); pause(1); expectText("Open profile from settings", "H7")
        log("DONE")
    }

    private func tabBarShown() -> Bool {
        let bar = app.tabBars.firstMatch
        return bar.exists && bar.isHittable
    }

    /// Switching the root declaration: NativeTabs -> NativeStack (sign-in flow) -> NativeTabs.
    func testRootSwitch() throws {
        launch()
        log("PHASE S1 Home > Detail 4, Settings counter 1")
        tap("Row 4"); pause(1.2); expectText("Detail 4", "S1a")
        app.tabBars.buttons["Settings"].tap(); pause(1)
        tap("Tap settings"); expectText("Settings taps: 1", "S1b")

        log("PHASE S2 sign out -> sign-in stack, no tab bar")
        tap("Sign out"); pause(1.5)
        expectText("You are signed out.", "S2")
        XCTAssertFalse(app.tabBars.firstMatch.exists, "S2: tab bar still shown")

        log("PHASE S3 push Help, edge swipe back (native pop in the new root)")
        tap("Sign-in help"); pause(1.2); expectText("Any account works in this example.", "S3a")
        edgeSwipe(distance: 320, velocity: 1200, hold: 0); pause(1.5)
        expectText("You are signed out.", "S3b")

        log("PHASE S4 sign in from Help -> tabs at their roots")
        tap("Sign-in help"); pause(1.2)
        tap("Sign in from help"); pause(1.5)
        expectText("Row 3", "S4")
        XCTAssertTrue(app.tabBars.buttons["Home"].exists, "S4: tab bar missing")
        XCTAssertFalse(element("Detail 4").exists, "S4: Home stack not reset")

        log("PHASE S5 the tabs' screen state started over")
        app.tabBars.buttons["Settings"].tap(); pause(1)
        expectText("Settings taps: 0", "S5")
        log("DONE")
    }
}
