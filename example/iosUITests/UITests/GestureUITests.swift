import XCTest

/// Back handlers in screen content, and vertical-drag areas excluded from the native scroll.
final class GestureUITests: ExampleAppTestCase {

    func testBackHandler() throws {
        launch()
        tap("Row 3"); pause(1.5); expectText("Detail 3", "G1")
        tap("Guard back: off"); pause(0.5); expectText("Guard back: on", "G2")

        log("PHASE G3 native back button -> handler, no pop")
        tapBack(); pause(1.2)
        expectText("Back intercepted: 1", "G3a"); expectText("Guard back: on", "G3b")

        log("PHASE G4 edge swipe -> handler (Compose edge gesture), no pop")
        edgeSwipe(distance: 300, velocity: 1000, hold: 0); pause(1.5)
        expectText("Back intercepted: 2", "G4")

        log("PHASE G5 a screen above the guarded one still swipes back natively")
        tap("Open Detail 4"); pause(1.5); expectText("Detail 4", "G5a")
        edgeSwipe(distance: 320, velocity: 1200, hold: 0); pause(1.5)
        expectText("Guard back: on", "G5b")

        log("PHASE G6 swipe on the guarded screen is intercepted again")
        edgeSwipe(distance: 300, velocity: 1000, hold: 0); pause(1.5)
        expectText("Back intercepted: 3", "G6")

        log("PHASE G7 guard off -> native back pops")
        tap("Guard back: on"); pause(0.5)
        tapBack(); pause(1.5); expectText("Row 3", "G7")
        log("DONE")
    }

    func testScrollExclusion() throws {
        launch()
        tap("Row 4"); pause(1.5); expectText("Detail 4", "X1")
        let pad = app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH 'Drag pad'")).firstMatch
        XCTAssertTrue(pad.waitForExistence(timeout: 5))
        let headline = element("Detail 4")
        let before = headline.frame

        log("PHASE X2 vertical drag inside the pad -> Compose, page does not move")
        let center = pad.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5))
        center.press(forDuration: 0.1, thenDragTo: center.withOffset(CGVector(dx: 0, dy: -80)), withVelocity: 200, thenHoldForDuration: 0.2)
        pause(1)
        log("CHECK X2 pad label '\(pad.label)'")
        XCTAssertNotEqual(pad.label, "Drag pad: 0", "the pad must receive the vertical drag")
        XCTAssertEqual(before.minY, headline.frame.minY, accuracy: 1, "the page must not scroll")

        log("PHASE X3 vertical drag outside the pad -> native page scroll (ScrollState)")
        at(200, 760).press(forDuration: 0.05, thenDragTo: at(200, 250), withVelocity: 1500, thenHoldForDuration: 0)
        pause(2)
        expectText("Line 20 of detail 4", "X3")
        log("DONE")
    }
}
