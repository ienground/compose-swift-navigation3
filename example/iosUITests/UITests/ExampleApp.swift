import XCTest

/// Shared helpers: launch the installed example app and drive it with real touches.
class ExampleAppTestCase: XCTestCase {
    var app: XCUIApplication!

    /// Phase marker, merged with the app's system log by run.sh (timeline.py).
    func log(_ message: String) {
        print("[HARNESS \(Int64(Date().timeIntervalSince1970 * 1000))] \(message)")
    }

    func pause(_ seconds: Double) { Thread.sleep(forTimeInterval: seconds) }

    override func tearDown() {
        XCUIDevice.shared.orientation = .portrait
        super.tearDown()
    }

    /// 각 테스트는 새 앱 프로세스의 Home에서 시작한다.
    func launch(_ arguments: [String] = []) {
        app = XCUIApplication(bundleIdentifier: "zone.ien.utils.example.Example")
        app.launchArguments = arguments
        app.launch()
        pause(3)
    }

    func at(_ x: CGFloat, _ y: CGFloat) -> XCUICoordinate {
        app.coordinate(withNormalizedOffset: CGVector(dx: 0, dy: 0)).withOffset(CGVector(dx: x, dy: y))
    }

    func element(_ label: String) -> XCUIElement {
        app.descendants(matching: .any).matching(identifier: label).firstMatch
    }

    func expectText(_ label: String, _ phase: String, file: StaticString = #filePath, line: UInt = #line) {
        let found = element(label).waitForExistence(timeout: 5)
        log("CHECK \(phase): '\(label)' \(found ? "OK" : "MISSING")")
        XCTAssertTrue(found, "\(phase): '\(label)' not shown", file: file, line: line)
    }

    func tap(_ label: String, file: StaticString = #filePath, line: UInt = #line) {
        let target = element(label)
        XCTAssertTrue(target.waitForExistence(timeout: 5), "'\(label)' not found", file: file, line: line)
        target.tap()
    }

    func tapBack() {
        app.navigationBars.buttons.element(boundBy: 0).tap()
    }

    func edgeSwipe(distance: CGFloat, velocity: CGFloat, hold: Double) {
        let start = app.coordinate(withNormalizedOffset: CGVector(dx: 0, dy: 0.5)).withOffset(CGVector(dx: 2, dy: 0))
        start.press(forDuration: 0.05, thenDragTo: start.withOffset(CGVector(dx: distance, dy: 0)),
                    withVelocity: XCUIGestureVelocity(velocity), thenHoldForDuration: hold)
    }
}
