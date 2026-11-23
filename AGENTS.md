# AGENTS.md

`compose-swift-navigation3`는 Compose Multiplatform의 Navigation 3 백스택을 iOS 네이티브 내비게이션에 연결하는 라이브러리다.

## 작업별 참고 문서

- UIKit 컨트롤러, 전환, 스크롤, 바 아이템, 상태 복원, 뒤로가기, 검색을 변경할 때는 [아키텍처와 결정 기록](docs/architecture.md)의 관련 6.x절과 7장을 먼저 읽는다. 설계 결정이 바뀌면 해당 문서도 갱신한다.
- Xcode 빌드, 시뮬레이터, XCUITest, 회귀 검증이 필요할 때는 [빌드와 검증](docs/verification.md)의 5장과 8장을 읽는다. 이 문서에 이전 검증 결과와 시뮬레이터 상태에 따른 실패 기록도 있다.
- 앱에 적용하는 방법과 공개 API는 [한국어 README](README_ko.md)와 [영어 README](README.md)를 참고한다. 플랫폼별 의존성과 버전은 [버전 카탈로그](gradle/libs.versions.toml)를 확인한다.

## 플랫폼과 모듈 경계

- `library/`는 배포 대상 KMP 라이브러리다. 예제와 검증 전용 코드는 `example/`에 둔다. Maven 좌표는 `zone.ien.navigation:navigation`이다.
- `commonMain`에는 플랫폼 중립 API와 순수 Kotlin 로직을 둔다. 앱의 Route, 백스택, 화면 Composable, ViewModel, 루트 `App()`은 공통으로 유지한다.
- iOS는 `iosMain`의 Kotlin/Native에서 실제 `UITabBarController`와 탭별 `UINavigationController`를 제어한다. 네이티브 전환, 뒤로 버튼과 스와이프, 내비게이션 바와 툴바를 유지한다. 라이브러리에 Swift 코드나 Compose 내비게이션 대체 경로를 추가하지 않는다.
- Android·Desktop·Web은 `otherPlatforms`에서 앱의 기존 `Scaffold`와 `NavDisplay(entries, onBack = onBack)`를 실행한다. 라이브러리는 이 플랫폼의 UI를 그리지 않으며 `navigation3-ui`와 Material 3 의존성을 추가하지 않는다.
- iOS 최소 지원 버전은 **18.2**다. UIKit API의 도입 버전을 확인하고, 런타임 OS 버전 분기를 두지 않는다. 지원 SDK와 OS 조합에서 시스템이 적용하는 Liquid Glass를 커스텀 appearance로 덮지 않는다.
- 버전은 [버전 카탈로그](gradle/libs.versions.toml)에서 관리한다. AGP와 Gradle은 Kotlin Gradle plugin 호환 범위에 맞춘다. 웹 타깃은 브라우저 전용이며 의존성을 변경하면 `./gradlew kotlinUpgradeYarnLock kotlinWasmUpgradeYarnLock`으로 잠금 파일을 갱신한다.

## 내비게이션과 UIKit 구현 규칙

- Navigation 3의 **Kotlin 백스택이 상태를 소유한다**. UIKit과의 diff는 멱등이어야 한다. Kotlin 변경을 적용하면서 발생한 delegate 이벤트는 Kotlin에 다시 전달하지 않는다.
- 엔트리와 컨트롤러를 매핑할 때 기존 키와 lease 규칙을 지킨다. 같은 Route의 연속 push를 지원하며, 같은 `contentKey`를 쓰는 화면은 한 번에 하나만 composition에 올린다.
- UIKit에서 시작한 pop은 전환 완료 후 제거된 엔트리 수만큼 올바른 콜백에 전달한다. 스와이프 취소 시 Kotlin 백스택을 유지한다. 전환 중 받은 최신 Kotlin 상태가 이전 UIKit pop으로 다시 잘리지 않도록 한다.
- 각 탭의 백스택과 화면 상태를 유지한다. ViewModel과 저장 가능한 상태는 엔트리 제거 시점에 정리한다. `NativeStack`과 `NativeTabs`는 iOS 루트에 한 번에 하나만 선언하고 탭을 스택 화면 안에 중첩하지 않는다.
- UIKit 호출은 메인 스레드에서 한다. weak delegate의 수명을 Kotlin에서 유지하고, 클로저와 delegate가 화면 또는 컨트롤러를 순환 참조하지 않도록 한다.
- 고정 제목은 `nativeScreen`에, 화면 상태를 따라 바뀌는 제목과 액션은 `NativeBarItems`에 선언한다. 탭 바 숨김은 UIKit `setTabBarHidden(_:animated:)`과 전환 완료 상태를 기준으로 처리한다. `hidesBottomBarWhenPushed`는 이전 화면의 큰 제목을 먼저 접으므로 쓰지 않는다.
- iOS 루트 호스트의 `App()`과 `NativeNavigationFrame.providers`는 UI 노드를 내보내지 않는다. 화면 UI와 테마는 엔트리 콘텐츠 또는 `frame`에 둔다. 화면 안에서 Compose 상단 바를 조건부로 그릴 때는 `LocalNativeScreen`을 사용한다.
- 네이티브 세로 스크롤을 연결할 때는 `rememberNativeScroll`의 스크롤 활성 여부와 여백을 함께 연결한다. 스크롤이 없는 화면은 `nativeContentPadding()`을 사용한다. 시스템 바와 키보드 여백을 중복 적용하지 않는다.

## 빌드와 검증

저장소 루트에서 작업에 해당하는 명령을 실행한다.

```bash
./gradlew jvmTest
./gradlew testAndroidHostTest
./gradlew iosSimulatorArm64Test
./gradlew jsTest wasmJsTest
./gradlew :example:androidApp:assembleDebug :example:desktopApp:jar
```

- 백스택 diff·동기화는 순수 Kotlin의 `commonTest`에서, UIKit 연동은 `iosTest`에서 검증한다. 터치·동작 검증은 [예제 XCUITest](example/iosUITests/run.sh)를 사용한다.
- iOS 동작을 변경하면 관련 흐름을 iOS 18.2 이상의 18.x와 iOS 26 이상 시뮬레이터에서 확인한다. CI는 iOS 앱 빌드와 XCUITest를 실행하지 않으므로 필요한 경우 [로컬 검증 명령](docs/verification.md)을 사용한다.
- 기능 변경 후에는 [예제 실행 스크립트](example/run-sample.sh)로 최신 앱을 띄운다. UI 테스트와 수동 확인은 서로 다른 시뮬레이터를 사용한다.
- 실행한 명령과 결과를 보고한다. 실행하지 못한 검증이 있으면 명시한다.

## Git과 배포

- 기본 브랜치는 `main`, 작업 브랜치는 `dev`다. CI는 `main` 대상 push와 PR에서 실행된다.
- 변경 범위를 확인하고 작업한 파일을 스테이징한다. 커밋·푸시·PR 생성은 사용자 요청이 있을 때만 한다.
- `local.properties`, `xcuserdata`, `.kotlin`, `build`는 Git에 추가하지 않는다. 배포 Secret 값은 변경하지 않는다.
- 배포 태그는 `v<lib-version-name>`과 일치해야 한다. GitHub Release 공개 시 [배포 워크플로](.github/workflows/publish.yml)가 같은 커밋의 CI를 통과한 뒤 Maven Central에 배포한다.
