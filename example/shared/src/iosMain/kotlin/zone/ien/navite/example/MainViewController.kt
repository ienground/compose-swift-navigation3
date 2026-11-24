package zone.ien.navite.example

import zone.ien.navite.NativeNavigationViewController

/** iOS 진입점만 네이티브 호스트로 감싼다. 화면과 내비게이션 상태는 공통 App이 소유한다. */
fun MainViewController() = NativeNavigationViewController { App() }
