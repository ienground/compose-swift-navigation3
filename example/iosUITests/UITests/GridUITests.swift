import XCTest

/// rememberNativeScroll with LazyVerticalGrid and LazyVerticalStaggeredGrid (Gallery tab).
/// Check the bar height per phase in the NATIVE log lines (timeline.py).
final class GridUITests: ExampleAppTestCase {

    private func dragAndHold() {
        at(200, 600).press(forDuration: 0.1, thenDragTo: at(200, 570), withVelocity: 60, thenHoldForDuration: 3)
    }

    private func fling() {
        at(200, 700).press(forDuration: 0.05, thenDragTo: at(200, 200), withVelocity: 3000, thenHoldForDuration: 0)
    }

    func testGrids() throws {
        launch()
        app.tabBars.buttons["Gallery"].tap(); pause(1.5)
        expectText("Cell 0", "GR0 grid")

        log("PHASE GR1 grid: drag up 30pt and hold (large title collapses with the finger)")
        dragAndHold()
        log("PHASE GR2 grid: released (snaps)"); pause(2)

        log("PHASE GR3 grid: flings to the end")
        for _ in 1...5 { fling(); pause(1.5) }
        expectText("Cell 89", "GR3 end of grid")
        log("PHASE GR4 grid: pull up at the end and hold (bounce)")
        at(200, 600).press(forDuration: 0.1, thenDragTo: at(200, 420), withVelocity: 300, thenHoldForDuration: 2)
        pause(2)
        expectText("Cell 89", "GR4 after bounce")

        log("PHASE GR5 grid: status bar tap -> top, large title back")
        at(200, 8).tap(); pause(3)
        expectText("Cell 0", "GR5")

        log("PHASE GR6 switch to the staggered grid")
        app.navigationBars.buttons["Staggered"].tap(); pause(1.5)
        expectText("Tile 0", "GR6")

        log("PHASE GR7 staggered: drag up 30pt and hold")
        dragAndHold()
        log("PHASE GR8 staggered: released"); pause(2)

        log("PHASE GR9 staggered: fling, then status bar tap")
        fling(); pause(2)
        at(200, 8).tap(); pause(3)
        expectText("Tile 0", "GR9")

        log("PHASE GR10 open a tile, come back")
        tap("Tile 1"); pause(1.5); expectText("Detail 1", "GR10a")
        tapBack(); pause(1.5); expectText("Tile 1", "GR10b")
        log("DONE")
    }
}
