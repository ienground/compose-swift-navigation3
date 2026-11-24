# 빌드와 검증

기존 장 번호를 유지한다. 5장에는 빌드·실행·XCUITest 명령과 검증 이력이, 8장에는 작업 흐름과 검증 체크리스트가 있다. 날짜가 붙은 결과는 당시 예제와 환경의 기록이다.

[작업 지침](../AGENTS.md) · [아키텍처와 결정 기록](architecture.md)

## 5. 빌드와 테스트 명령

```bash
# 테스트 (CI와 같다. 이름만 쓴 태스크는 library와 example/shared에서 모두 실행된다)
./gradlew jvmTest
./gradlew testAndroidHostTest
./gradlew iosSimulatorArm64Test          # macOS 필요
./gradlew jsTest wasmJsTest              # 헤드리스 브라우저에서 실행

# 예제 앱 컴파일 확인 (CI와 같다)
./gradlew :example:androidApp:assembleDebug :example:desktopApp:jar

# 예제 앱 실행
./gradlew :example:androidApp:installDebug
./gradlew :example:desktopApp:run

# iOS 예제 앱 빌드 (시뮬레이터)
xcodebuild -project example/iosApp/iosApp.xcodeproj -scheme example.iosApp \
  -configuration Debug -destination 'platform=iOS Simulator,name=iPhone 17,OS=27.2' \
  -derivedDataPath build/xcode build
```

- 스킴 `example.iosApp`은 gitignore 대상인 `xcuserdata`에만 있다. 새로 클론한 환경이나 CI에서는 없을 수 있다. iOS 빌드를 CI에 넣을 때는 공유 스킴(`xcshareddata`)을 커밋한다.
- 시뮬레이터 이름과 OS는 `xcrun simctl list devices available`로 확인해 환경에 맞게 바꾼다.
- iOS 26 미만 동작은 18.2 이상의 iOS 18.x 시뮬레이터로 검증한다. 18.2 미만 런타임(예: 18.1)은 지원 범위 밖이다. 해당 런타임에 기기가 없으면 `xcrun simctl create`로 만든다.
- 현재 CI는 iOS 앱(Xcode)을 빌드하지 않는다. iOS 쪽 변경은 위 `xcodebuild` 명령으로 직접 확인한다.

**샘플 앱 띄우기 (사람이 직접 확인)**

```bash
example/run-sample.sh            # iOS + Android
example/run-sample.sh ios        # iOS만 (기본: 가장 최신 "iPhone 18 Pro" 시뮬레이터, IOS_SIM_UDID로 변경)
example/run-sample.sh android    # Android만 (연결된 모든 기기, ANDROID_SERIAL이면 그 기기만. 없으면 첫 AVD를 켠다. ANDROID_AVD로 변경)
```

- 기능을 하나 마칠 때마다 이 스크립트로 최신 예제를 띄워 사용자가 볼 수 있게 한다.
- UI 테스트와 같은 시뮬레이터를 쓰지 않는다. UI 테스트는 앱을 종료하고 다시 실행하기 때문이다.
- Xcode 27에는 Simulator.app이 없고 `Xcode.app/Contents/Applications/DeviceHub.app`이 시뮬레이터 화면을 보여준다. 스크립트가 있는 쪽을 연다.

**iOS UI 테스트 (실제 터치)**

```bash
example/iosUITests/run.sh <시뮬레이터 UDID>                                   # 전체
example/iosUITests/run.sh <UDID> -only-testing:UITests/NavigationUITests       # 일부
python3 -I example/iosUITests/timeline.py build/ui-tests/<UDID>               # 결과 타임라인
```

- `run.sh`가 하는 일
  - 예제 앱을 빌드해 설치하고, 별도 프로젝트 `example/iosUITests/iosUITests.xcodeproj`의 UI 테스트를 실행한다. 예제 앱 Xcode 프로젝트는 건드리지 않는다.
- 테스트는 설치된 `NaviteExample.app`을 번들 ID `zone.ien.navite.example`로 띄운다.
- 결과는 `build/ui-tests/<UDID>/`에 남는다.
  - `xcodebuild.log`: 테스트 결과와 `[HARNESS]` 단계 표시
  - `app.log`: 앱의 시스템 로그
  - `shots/`: 약 1초 간격 스크린샷
- 현재 테스트 목록(2026-11-17 최소 예제 기준, 총 5개)
  - `NavigationUITests`: push·뒤로 버튼, 스와이프 취소·완료, 탭별 백스택 유지
  - `ScrollUITests`: 탭 전환 후 스크롤 위치 유지, 가로 화면
- 아래 검증 기록은 당시의 기능 데모 예제를 기준으로 한다. 최소 예제에는 앱 내부 추적 로그를 넣지 않는다.
- 2026-11-13 루트 전환을 추가한 뒤 13개를 실행했다.
  - iOS 18.5: 13개 모두 통과했다.
  - iOS 27.2: 13개 모두 통과했다. 처음 실행에서는 `SearchUITests`가 S2 단계에서 실패했다. 하네스를 고친 뒤 13개를 한 번에 다시 실행해 모두 통과했다.
    - 원인은 시뮬레이터 설정이었다. Mac 키보드로 입력한 적이 있는 시뮬레이터는 하드웨어 키보드가 있다고 보고 소프트웨어 키보드를 숨긴다(`com.apple.keyboard.preferences`의 `AutomaticMinimizationEnabled`).
    - 키보드가 없으면 걸러진 리스트가 스크롤 없이 화면에 다 들어간다. 그래서 검색어를 지우면 맨 위(Row 0)가 보이는 것이 맞다. 테스트는 Row 40까지 스크롤돼 있다고 가정했다.
    - 그래서 `run.sh`가 이 값을 끄고, 테스트는 소프트웨어 키보드가 보이는지 먼저 확인한다.
- 2026-11-14 뒤로가기 처리를 바꾼 뒤(6.9장) 14개(`testRootHandlerHasNoBackButton` 추가)를 실행했다.
  - iOS 27.2: 14개 모두 통과했다.
  - iOS 18.5: 새로 만든 시뮬레이터에서 14개 모두 통과했다.
  - 오래 쓴 iOS 18.5 시뮬레이터에서는 복원 테스트가 재실행 때 저장 데이터를 받지 못했다(`RESTORE none`). 수정 전 라이브러리에서도 같은 일이 있었다.
    - 원인: iOS가 이전 장면 세션을 다시 연결하지 않고 새 세션을 만들었다. 시스템 로그에서 재실행 직후 "Persistent SceneSession Map Update"가 찍히는 쪽이 실패한 경우다.
    - 앱 문제가 아니라 시뮬레이터 상태 문제다. 복원 테스트가 `RESTORE none`으로 실패하면 새 시뮬레이터(`xcrun simctl create`)에서 다시 확인한다.
- 2026-11-14 Hotpot에서 나온 문제를 고친 뒤 실행했다. 고친 것은 같은 key 재진입 크래시, 제목 지연, 제목 반복 설정이다(6.6장, 6.7장).
  - iOS 18.5(새로 만든 iPhone 16): 14개 모두 통과했다.
  - iOS 27.2(새로 만든 iPhone 17): 13개 통과, `SearchUITests`만 S2에서 실패했다. 검색어를 지우면 목록이 Row 40 근처에 머물지 않고 맨 위로 돌아갔다.
    - 변경 전 코드(HEAD)로 같은 시뮬레이터에서 돌려도 같은 단계에서 실패했다. 이번 변경 때문이 아니다.
    - 이 시뮬레이터는 한국어 키보드(후보 막대 때문에 더 높음)를 보였다. 원인은 아직 확인하지 않았다.
  - Gradle: `jvmTest`, `testAndroidHostTest`, `iosSimulatorArm64Test`, 예제 Android·Desktop 빌드가 통과했다.
- 2026-11-14 `NativeStack`/`NativeTabs`를 commonMain으로 옮기고 예제를 `App()` 하나로 합친 뒤 실행했다(6.6장).
  - 첫 실행(iOS 27.2 iPhone 17)에서 두 개가 실패했다. 둘 다 고친 뒤 다시 돌렸다.
    - `testRestoreSignedOut`: 로그아웃 상태가 저장되지 않았다. 예제 `rememberAppNavigation()`의 `SideEffect` 복사 문제였다(6.6장 함정).
    - `testLandscape`: iOS 화면에 테마가 적용되며 행이 커져, 가로 화면에서 Row 2가 화면 아래로 밀렸다. Row 0을 누르도록 바꿨다.
  - 오래 쓴 iOS 18.5 iPhone 16에서는 `testRestoreSignedOut`이 `RESTORE none`으로 실패했다. 재실행 직후 "Persistent SceneSession Map Update"가 찍힌, 위에 적은 시뮬레이터 상태 문제다.
  - 최종 코드: iOS 27.2(iPhone 17)와 새로 만든 iOS 18.5(iPhone 16 `UI test 18.5`)에서 14개 모두 통과했다.
  - Gradle: `jvmTest`, `testAndroidHostTest`, `iosSimulatorArm64Test`, `jsTest`, `wasmJsTest`, 예제 Android·Desktop 빌드가 통과했다. 새 `NativeNavigationScopeTest`(제목 fallback, 탭 바 규칙, 바 아이템) 8개를 포함한다.
- 2026-11-15 `NativeNavigationFrame`, `LocalNativeScreen`, 호스트 기본 locals를 추가하고 예제 테마를 frame으로 옮긴 뒤 실행했다(6.6장).
  - iOS 27.2(iPhone 18 Pro): 14개 모두 통과했다. iOS 18.x는 이번에 실행하지 않았다(사용자 요청으로 이미 켜진 시뮬레이터만 썼다).
  - Gradle: `jvmTest`, `testAndroidHostTest`, `iosSimulatorArm64Test`, `jsTest`, `wasmJsTest`, 예제 Android·Desktop 빌드가 통과했다.
  - Android 예제(에뮬레이터)에서 frame으로 준 테마가 Material 바와 화면에 적용되는 것을 눈으로 확인했다.
  - Hotpot: iOS 빌드와 투어(탭, 상세 push와 뒤로, 테마, Compose 탑바 숨김)를 iPhone 18 Pro에서, Android 실행을 에뮬레이터에서 확인했다. host 테스트 289개가 통과했다.
- 2026-11-15 탭 위의 공유 화면(`NativeTabs(sharedBackStack)`)을 추가한 뒤 실행했다(6.6장). 15개(`testSharedStack` 추가)다.
  - iOS 27.2(iPhone 18 Pro): 14개 통과, `SearchUITests`만 S2에서 실패했다. 검색어를 지우면 목록이 Row 40 근처가 아니라 맨 위로 돌아갔다(2026-11-14 iPhone 17과 같은 증상).
    - 이번 변경 전 상태(스테이징된 코드)로 같은 시뮬레이터에서 돌려도 같은 단계에서 실패했다. 이번 변경 때문이 아니다. 같은 날 앞선 실행에서는 통과했으므로 시뮬레이터 상태(키보드)와 관련된 것으로 보이지만 원인은 아직 확인하지 않았다.
  - iOS 18.x는 실행하지 않았다(사용자 요청으로 이미 켜진 시뮬레이터만 썼다).
  - Gradle: `jvmTest`, `testAndroidHostTest`, `iosSimulatorArm64Test`(라이브러리 82개, 새 `StackCoordinatorTest` 4개 포함), `jsTest`, `wasmJsTest`, 예제 Android·Desktop 빌드가 통과했다.
  - Android 예제(에뮬레이터): Home에서 Profile 열기(하단 탭 숨김), 시스템 뒤로가기, Settings에서 연 Profile을 Gallery로 옮긴 뒤 뒤로가기를 화면으로 확인했다.
    - Profile은 바 아이템을 선언하지 않는다. 그래서 push 직후 Home이 composition에 남아 있는 동안 Home의 "Open 42"와 검색 필드가 Material 바에 잠깐 보였다(가장 최근에 등록한 화면이 이기는 규칙, 6.7장). 몇 초 뒤에는 사라졌다.
  - Hotpot: 상세 화면과 게스트 로그인 화면을 공유 스택으로 옮겼다. iOS 투어(설정 → 상세 push와 뒤로 버튼, 다시 연 상세의 가장자리 스와이프, 탭 전환)를 iPhone 18 Pro에서 통과했고 host 테스트 289개가 통과했다. 시뮬레이터가 실제 계정으로 로그인돼 있어 게스트 로그인 화면은 확인하지 않았다.
- 2026-11-15 제목 이미지(시험용, 6.7장)를 추가한 뒤 실행했다. 예제 Home(PNG 로고, 큰 제목)과 Profile(벡터 로고)에 `titleImage`를 넣은 상태다.
  - iOS 27.2(iPhone 18 Pro): 15개 모두 통과했다(`SearchUITests` 포함). iOS 18.x는 실행하지 않았다.
  - Gradle: `jvmTest`, `testAndroidHostTest`, `iosSimulatorArm64Test`(라이브러리 83개, 새 `titleImageComesFromTheTopEntryMetadata` 포함), 예제 Android·Desktop 빌드가 통과했다. `jsTest`, `wasmJsTest`는 실행하지 않았다.
  - Android 예제(Pixel 7a): Home의 Material 탑바 제목 자리에 로고가 나오는 것을 화면으로 확인했다.
- 2026-11-16 탭 바 숨김을 `UITabBarController.setTabBarHidden`으로 옮기고 push의 전환 준비 뒤에 적용했다. `hidesBottomBarWhenPushed`가 이전 화면의 큰 제목을 먼저 접던 문제를 피한다.
  - iOS 27.2(iPhone 17): Navigation UI 테스트 4개와 탭 전환 후 스크롤 유지 테스트 1개가 통과했다. 새 `testTabBarVisibilityDuringCancelledPop`은 스와이프 취소 후 숨김 유지와 완료 후 탭 바 복원을 확인한다.
  - iOS 18.5(iPhone 16): Navigation UI 테스트 4개가 모두 통과했다.
  - Gradle: `jvmTest`, `testAndroidHostTest`, 라이브러리 `iosSimulatorArm64Test`(99개, `StackCoordinatorTest` 13개 포함), 예제 Android·Desktop 빌드가 통과했다.
  - Hotpot: iOS 27.2(iPhone 18 Pro)에서 설정 → 타이머 알림 → 뒤로가기 UI 테스트가 통과했다. 녹화에서 이전 설정 largeTitle이 크기를 유지한 채 왼쪽으로 이동하는 것을 확인했다. 실기기 실행은 하지 않았다.
- 2026-11-16 바 버튼·메뉴에 `Painter` 이미지를 추가했다.
  - 라이브러리: JVM·Android host·JS·Wasm 테스트 각각 65개와 iOS 시뮬레이터 테스트 105개가 통과했다. 기존 생성자 호환성, 이미지 교체·재사용, 비율과 크기, 메뉴 이미지, 컨트롤러 해제를 확인한다.
  - iOS 27.2(iPhone 17)와 iOS 18.5(iPhone 16): `BarItemsUITests`가 통과했다. PNG 버튼, `ImageVector` 버튼·툴바, 벡터 리소스 메뉴의 실제 터치를 확인한다.
  - 예제 Android·Desktop 빌드가 통과했다. iPhone 18 Pro 시뮬레이터와 Pixel 7a에서 최신 예제를 실행해 이미지 표시를 확인했다. PNG 검증용 투명 배경 리소스 `example_bar_icon.png`를 예제에 추가했다.
- 위의 과거 기능 데모 테스트는 `-freshStart`로 시작했다. 현재 최소 예제는 새 프로세스에서 Home으로 시작한다.

## 8. 작업 흐름과 검증

### 작업 순서

1. 기존 Navigation 3 구조와 플랫폼별 진입점을 분석한다.
2. SwiftUI와 UIKit 방식 중 하나를 정하고 이유를 7장에 기록한다. → 완료: UIKit(6.1장, 7장)
3. 변경할 파일과 구현 구조를 정리한다.
4. Android 동작을 유지하면서 iOS 네이티브 내비게이션을 구현한다.
5. 화면 이동, 뒤로가기, 탭 전환, 딥링크, 상태 복원을 검증한다.
6. 가능한 빌드와 테스트를 실행하고 남은 제약을 보고한다.

### 검증 체크리스트

iOS 26 이상 시뮬레이터와 iOS 18.x(18.2 이상) 시뮬레이터에서 각각 확인한다. 두 환경 모두 네이티브 컨트롤러로 동작해야 한다.

결과는 2026-11-13 iOS 27.2와 iOS 18.5 시뮬레이터에서 XCUITest(실제 터치)로 확인한 것이다. 확인 방법은 앱 로그의 Kotlin 백스택과 네이티브 스택, 그리고 스크린샷이다.

- [x] push, pop, replace, popTo 후 네이티브 스택과 Kotlin 백스택이 일치한다.
- [x] 네이티브 뒤로가기 버튼과 가장자리 스와이프 모두 Kotlin 상태에 반영된다. 스와이프를 취소하면 상태가 바뀌지 않는다.
- [x] 같은 Route를 연속으로 push해도 스택이 중복되거나 엉키지 않는다. 같은 `contentKey`는 Navigation 3 의미대로 ViewModel을 공유한다.
- [x] 탭을 바꿨다가 돌아오면 각 탭의 스택과 화면 상태(스크롤, ViewModel)가 유지된다. 스택, `rememberSaveable`, ViewModel, 스크롤 위치(`ScrollUITests.testTabSwitchKeepsScroll`, 전환 전후 행 위치가 같음)를 확인했다.
- [x] 상세 화면에서 탭 바를 숨기거나 보이는 설정이 의도대로 동작한다(`hidesTabBar`).
- [x] 내비게이션 바 타이틀과 toolbar가 엔트리 메타데이터와 `NativeBarItems`대로 표시된다. 동적 타이틀, 버튼, 메뉴(destructive 포함), 툴바 표시와 숨김, 비활성 상태를 `BarItemsUITests`로 확인했다.
- [x] iOS 26 이상에서 탭 바, 내비게이션 바, toolbar에 Liquid Glass가 적용된다. 라이브러리 설정이 이를 덮어쓰지 않는다. iOS 27.2 스크린샷에서 탭 바와 뒤로 버튼이 glass로 보인다.
- [x] iOS 18.x에서 미지원 API 호출(`unrecognized selector` 등) 없이 동작한다(iOS 18.5).
- [x] 딥링크로 여러 단계의 스택을 한 번에 구성할 수 있다. 중간 화면은 보일 때 처음 만든다.
- [ ] 상태 복원 후 스택이 재구성된다.
  - iOS: 확인함(6.8장, `RestorationUITests`). 백스택, 탭, `rememberSaveable`, 스크롤 위치가 복원된다.
  - Android: 확인함(2026-11-13, 에뮬레이터). Detail 3에서 홈으로 나간 뒤 `adb shell am kill`로 프로세스를 종료하고 다시 실행하자 Detail 3이 복원됐다. Navigation 3의 `rememberNavBackStack`이 처리하며, 라이브러리는 관여하지 않는다.
- [ ] Android의 기존 동작(뒤로가기, 전환, Material 3 내비게이션)이 바뀌지 않았다.
  - 라이브러리는 Android UI를 그리지 않는다.
  - 예제를 에뮬레이터(Pixel_10_Pro AVD)에서 실행해 다음을 확인했다(2026-11-13, adb와 uiautomator).
    - Home과 Detail push
    - `NativeBarState`로 그린 Material 바 아이템, 툴바, 검색 필드
    - 시스템 뒤로가기
    - `NavigationBackHandler` 가드: 켜면 뒤로가기를 가로채고, 끄면 pop된다
  - 공통 `App()`으로 바꾼 뒤 다시 확인했다(2026-11-14, Pixel_10_Pro AVD, adb와 uiautomator). Material 바와 탭 바는 모두 `NativeTabs` 블록의 `NativeNavigationScope` 값으로 그린다.
    - 탑바 제목(metadata, 동적 제목 "Detail 3 (2)"), 뒤로 버튼, `trailing`과 `leading` 버튼, 툴바(비활성 포함), 검색 필드
    - 홀수 Detail에서 탭 바가 숨는다. 그 위로 push한 Detail 4에서도 숨겨진 채로 있다(iOS `hidesBottomBarWhenPushed`와 같은 규칙. 이전 예제는 맨 위 화면만 봤다).
    - 탭 전환: Gallery의 staggered 모드(`rememberSaveable`)와 Home의 Detail 4(ViewModel 카운트 2)가 다른 탭에 다녀와도 그대로다. pop한 Detail의 ViewModel은 정리된다.
    - 로그아웃 → 로그인 스택(탭 바 없음) → Help → 시스템 뒤로가기 → 로그인 → 탭
    - 로그아웃 상태로 Help까지 연 뒤 홈으로 나가 `am kill`로 프로세스를 종료했다. 다시 실행하자 Help 화면이 복원됐다.
  - 자동 테스트는 없다.

### 테스트 작성

- 백스택 diff, 동기화, 중복 방지 로직은 UIKit에 의존하지 않는 순수 Kotlin으로 분리하고 `commonTest`에서 테스트한다.
- UIKit에 의존하는 부분은 `iosTest`(`iosSimulatorArm64Test`)에서 가능한 범위만 테스트하고, 나머지는 예제 앱으로 수동 검증한다.
- 결과를 보고할 때 실행한 명령과 실패 출력을 그대로 남긴다. 실행하지 못한 검증은 실행하지 못했다고 쓴다.
