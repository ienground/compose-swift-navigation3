import XCTest

final class NavigationUITests: ExampleAppTestCase {
    private let hiddenTabBarRow = "Row 4 · iOS 하단 탭 바 숨김"

    func testPushAndNativeBack() {
        launch()
        let homeTab = app.tabBars.buttons["Home"]
        XCTAssertTrue(homeTab.isHittable)
        tap(hiddenTabBarRow)
        expectText("Selected item: 4", "상세 화면")
        XCTAssertTrue(app.navigationBars["Detail 4"].exists)
        XCTAssertFalse(homeTab.isHittable)
        tapBack()
        expectText(hiddenTabBarRow, "뒤로가기")
        XCTAssertTrue(app.navigationBars["Home"].exists)
        expectTabBarToReturn(homeTab)
    }

    func testCancelledAndCompletedSwipe() {
        launch()
        let homeTab = app.tabBars.buttons["Home"]
        tap(hiddenTabBarRow); pause(1)
        XCTAssertFalse(homeTab.isHittable)
        edgeSwipe(distance: 50, velocity: 80, hold: 0.3); pause(1)
        expectText("Selected item: 4", "스와이프 취소")
        XCTAssertFalse(homeTab.isHittable)
        edgeSwipe(distance: 320, velocity: 1200, hold: 0); pause(1)
        expectText(hiddenTabBarRow, "스와이프 완료")
        expectTabBarToReturn(homeTab)
    }

    private func expectTabBarToReturn(_ tab: XCUIElement, file: StaticString = #filePath, line: UInt = #line) {
        let visible = XCTNSPredicateExpectation(predicate: NSPredicate(format: "hittable == true"), object: tab)
        XCTAssertEqual(XCTWaiter.wait(for: [visible], timeout: 5), .completed, file: file, line: line)
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

    func testDetailBarAndFloatingAction() {
        launch()
        tap("Row 3")
        let favorite = app.navigationBars.buttons["즐겨찾기 전환"]
        XCTAssertTrue(favorite.waitForExistence(timeout: 5))
        XCTAssertTrue(favorite.isEnabled)
        favorite.tap()
        expectText("즐겨찾기에 추가됨", "전환 후 바 버튼 실행")
        tap("메모 추가")
        expectText("메모 수 1개", "네이티브 FAB 실행")
        expectText("메모 1개", "네이티브 바 라벨 갱신")
    }

    func testPreviewHidesAndRestoresNavigationBar() {
        launch()
        app.tabBars.buttons["Settings"].tap()
        tap("미리보기 열기")
        expectText("항목 42 미리보기", "미리보기 화면")
        let bar = app.navigationBars["미리보기"]
        XCTAssertFalse(bar.exists && bar.isHittable)
        tap("미리보기 닫기")
        XCTAssertTrue(app.navigationBars["Settings"].waitForExistence(timeout: 5))
    }
}
