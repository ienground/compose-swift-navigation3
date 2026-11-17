import XCTest

final class NavigationUITests: ExampleAppTestCase {
    func testPushAndNativeBack() {
        launch()
        tap("Row 3")
        expectText("Selected item: 3", "상세 화면")
        XCTAssertTrue(app.navigationBars["Detail 3"].exists)
        tapBack()
        expectText("Row 3", "뒤로가기")
        XCTAssertTrue(app.navigationBars["Home"].exists)
    }

    func testCancelledAndCompletedSwipe() {
        launch()
        tap("Row 3"); pause(1)
        edgeSwipe(distance: 50, velocity: 80, hold: 0.3); pause(1)
        expectText("Selected item: 3", "스와이프 취소")
        edgeSwipe(distance: 320, velocity: 1200, hold: 0); pause(1)
        expectText("Row 3", "스와이프 완료")
    }

    func testTabsKeepTheirBackStacks() {
        launch()
        tap("Row 3")
        app.tabBars.buttons["Settings"].tap()
        tap("Open detail")
        expectText("Selected item: 42", "설정 상세")
        app.tabBars.buttons["Home"].tap()
        expectText("Selected item: 3", "홈 스택 유지")
        tapBack()
        expectText("Row 3", "홈 루트")
        app.tabBars.buttons["Settings"].tap()
        expectText("Selected item: 42", "설정 스택 유지")
        tapBack()
        expectText("Open detail", "설정 루트")
    }
}
