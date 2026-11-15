# AGENTS.md

이 저장소에서 작업하는 AI 에이전트와 기여자를 위한 안내서다. 작업을 시작하기 전에 끝까지 읽는다. 설계 결정이 바뀌면 코드와 함께 이 문서도 갱신한다.

## 1. 프로젝트 목표

**compose-swift-navigation3**는 Kotlin Multiplatform 라이브러리다. Compose Multiplatform과 Navigation 3로 만든 앱이 플랫폼마다 다음처럼 동작하도록 연결한다.

| 플랫폼 | 동작 |
| --- | --- |
| Android | 기존 Compose Navigation 3(`NavDisplay`)와 Material 3 내비게이션을 **그대로** 사용한다. 동작을 바꾸지 않는다. |
| iOS | 실제 Apple 시스템 컴포넌트인 UIKit `UITabBarController`/`UINavigationController`를 Kotlin/Native에서 직접 제어한다(6.1장). 최소 지원 버전인 iOS 18.2부터 모든 버전에서 네이티브 컨트롤러를 쓰고, iOS 26 이상에서는 Liquid Glass가 적용된다. |
| 공통 | 화면 Composable, ViewModel, 비즈니스 로직, Route 정의를 공유한다. |

이 라이브러리의 핵심은 **실제 네이티브 내비게이션 컨트롤러를 Navigation 3 아키텍처와 통합하는 것**이다. iOS처럼 보이는 Compose 컴포넌트로 바꾸거나 UI 스타일만 바꾸는 것은 목표가 아니며, 금지한다.

### iOS 요구사항

1. **하단 내비게이션**: `UITabBarController`를 사용한다. iOS 26 이상에서는 시스템 Liquid Glass 디자인을 지원한다. 탭을 바꿔도 각 탭의 내비게이션 상태를 유지한다. 상세 화면에서는 탭 바 표시 여부를 제어할 수 있어야 한다.
2. **화면 내비게이션**: `UINavigationController`를 사용한다. 다음을 모두 네이티브로 제공한다.
   - 뒤로가기 버튼과 화면 전환 애니메이션
   - 가장자리 스와이프 뒤로가기 제스처
   - 내비게이션 바와 타이틀
   - 필요한 경우 Toolbar
3. **Navigation 3 연동**
   - 기존 Route와 내비게이션 인터페이스를 최대한 재사용한다.
   - push, pop, replace, popTo 요청을 처리한다.
   - 시스템 뒤로가기(버튼, 스와이프)와 Kotlin 내비게이션 상태를 동기화한다.
   - 중복 백스택과 순환 이벤트를 막는다.
   - 딥링크와 상태 복원을 고려한다.

## 2. 현재 상태 (2026-11-13 기준)

다음을 구현했다. 남은 일은 각 장 끝에 정리했다.
- iOS 스크롤 연동(6.5장)
- Navigation 3 연동(6.6장)
- 탑바 액션과 toolbar(6.7장)
- 상태 복원(6.8장)
- 화면 안 뒤로가기 핸들러 연동(6.9장)
- 검색(6.10장)
- 루트 전환: `NativeStack`과 `NativeTabs` 사이 전환(6.6장)
- 공통 선언: `NativeStack`/`NativeTabs`를 commonMain에 두고, 앱의 루트 `App()` 하나를 모든 플랫폼이 쓴다(6.6장)
- 탭 위의 공유 화면: 어느 탭에서든 여는 화면을 `NativeTabs(sharedBackStack)` 하나로 둔다(6.6장)

- `:library` (패키지 `zone.ien.navigation`, Maven 좌표 `zone.ien.navigation:navigation:<version>`)
  - commonMain 공개 API
    - 스크롤 연동: `rememberNativeScroll(state, background)`, `NativeScroll`, `nativeContentPadding`, `Modifier.nativeScrollExclusion()`
    - 화면 메타데이터: `nativeScreen(...)`(제목 이미지 `titleImage` 포함), 그리고 메타데이터를 읽는 `NavEntry.nativeTitle` 등
    - 바 아이템과 검색: `NativeBarItems`, `NativeBarButton`, `NativeMenuAction`, `NativeSearchBar`, `NativeBarState`, `ProvideNativeBarState`
    - 내비게이션 선언: `NativeStack`, `NativeTabs`(탭 위의 공유 화면 `sharedBackStack` 포함), `NativeTab`, 그리고 iOS가 아닌 플랫폼의 블록이 받는 `NativeNavigationScope`
    - 앱 틀: `NativeNavigationFrame(providers, frame)`, 네이티브 화면 여부 `LocalNativeScreen`
  - iosMain 공개 API
    - 화면 하나: `ComposeScreenViewController`
    - Navigation 3 앱 전체: `NativeNavigationViewController(restoredState, stateVersion) { ... }`
    - 상태 복원: `saveNativeNavigationState(controller)`
- `:example:shared`
  - 라우트는 `HomeRoute`, `GalleryRoute`, `DetailRoute(id)`, `SettingsRoute`이고, 탭 3개(Home, Gallery, Settings)가 각자 백스택을 가진다.
  - Gallery 탭은 `LazyVerticalGrid`와 `LazyVerticalStaggeredGrid`를 바 버튼으로 바꿔 가며 보여준다.
  - 모든 플랫폼이 `App()` 하나를 쓴다. iOS는 `NativeTabs`를 네이티브 컨트롤러로 그리고, Android와 Desktop은 `NativeTabs`의 블록(`MaterialChrome()`)에서 `NavDisplay`와 Material 3로 그린다.
  - 테마(`AppTheme`)는 `NativeNavigationFrame(frame = { AppTheme(it) })`로 준다. Android는 앱 전체를 한 번, iOS는 각 화면을 감싼다.
  - 홀수 Detail은 탭 바를 숨기고, 짝수 Detail은 탭 바를 그대로 둔다. 두 동작을 함께 보여주기 위해서다.
  - 공유 화면: Home과 Settings에서 여는 `ProfileRoute`는 공유 백스택(`AppNavigation.shared`)에 쌓인다. Profile에서 연 Detail 50도 같은 스택이다. "Switch to Gallery"는 공유 화면이 보이는 동안 앱이 탭을 바꾸는 경우다.
  - 제목 이미지(시험용, 6.7장): Home은 512px PNG 로고(`example_logo.png`, 큰 제목), Profile은 벡터 로고(`compose-multiplatform.xml`, 인라인 제목)를 제목 자리에 보인다.
  - 로그인 흐름: Settings의 "Sign out"을 누르면 탭 대신 로그인 스택(`SignInRoute` → `SignInHelpRoute`)이 나오고, "Sign in"을 누르면 탭으로 돌아간다. `App()`이 `NativeTabs`와 `NativeStack`을 `if`로 바꿔 선언한다. 로그아웃하면 탭 스택을 루트로 되돌린다.
- `example/iosApp`: SwiftUI `App` → `UIViewControllerRepresentable` → `MainViewController()` 구조다. `MainViewController()`는 `NativeNavigationViewController { App(navigation) }`를 반환한다. 테스트용 추적 코드(`-traceNav`, `-demoDeepLink`)만 iOS 진입점에 있다.
- `example/iosUITests`: 예제 앱을 실제 터치로 검증하는 XCUITest 프로젝트다(5장).
- `example/run-sample.sh`: 예제 앱을 iOS 시뮬레이터와 Android 에뮬레이터에 설치하고 실행한다. 사람이 직접 만져 보는 용도다(5장).

템플릿 교체(2026-11-13 완료)
- 이름: `rootProject.name`, group, namespace, artifactId, 패키지, `pom`
- 소스: Fibonacci 소스와 테스트를 지웠다.
- `linuxX64` 타깃: 제거했다.
- 문서: README를 다시 썼다.
- 아직 남은 것: 템플릿 README가 쓰던 `images/` 폴더는 지우지 않았다.

## 3. 저장소 구조

```
.
├── library/                 # 배포 대상 KMP 라이브러리 (Maven Central, com.vanniktech.maven.publish)
├── example/
│   ├── shared/              # 공유 Compose UI. :library에 의존하고 iOS용 static framework `Shared`를 만든다
│   ├── androidApp/          # Android 진입점 (MainActivity → App())
│   ├── desktopApp/          # Desktop(JVM) 진입점
│   ├── iosApp/              # Xcode 프로젝트 (Gradle 모듈이 아니다)
│   ├── iosUITests/          # 예제 앱용 XCUITest 프로젝트와 실행 스크립트 (run.sh, timeline.py)
│   └── run-sample.sh        # 예제 앱을 시뮬레이터/에뮬레이터에 설치·실행 (수동 확인용)
├── gradle/libs.versions.toml
└── .github/workflows/       # gradle.yml(CI), publish.yml(릴리스 시 Maven Central 배포)
```

- iOS 앱은 Xcode 빌드 단계 `Compile Kotlin Framework`에서 `./gradlew :example:shared:embedAndSignAppleFrameworkForXcode`를 호출해 `Shared` 프레임워크를 만든다.
- 라이브러리 코드는 `library/`에, 데모·검증용 코드는 `example/`에 둔다. 예제에만 필요한 코드를 라이브러리에 넣지 않는다.

## 4. 버전과 툴체인

| 항목 | 버전 | 위치 |
| --- | --- | --- |
| Kotlin | 2.4.21 | `gradle/libs.versions.toml` |
| Compose Multiplatform | 1.12.2 | 〃 |
| Compose Material3 | 1.12.0-alpha03 | 〃 |
| AGP | 9.3.1 | 〃 (Kotlin 2.4.2x가 완전히 지원하는 범위: 8.5.2–9.3.1) |
| Gradle | 9.7.0 | `gradle/wrapper/gradle-wrapper.properties` |
| AndroidX Lifecycle (JetBrains) | 2.11.0 | `gradle/libs.versions.toml` |
| Android compileSdk / targetSdk / minSdk | 37 / 37 / 29 | 〃 |
| Gradle daemon JVM | JetBrains Runtime 25 | `gradle/gradle-daemon-jvm.properties` |
| 컴파일 툴체인 | JDK 17 (`jvmToolchain(17)`) | 각 `build.gradle.kts` |
| Kotlin/Native iOS SDK 바인딩 | iOS 26.4 SDK | Kotlin 2.4.21 배포본의 `konan.properties` |
| 최소 지원 iOS (라이브러리, 예제 앱 Deployment Target) | 18.2 | `example/iosApp/iosApp.xcodeproj` |

- 버전은 반드시 `gradle/libs.versions.toml`에 둔다. `build.gradle.kts`에 버전 문자열을 직접 쓰지 않는다.
- AGP나 Gradle을 올릴 때는 `libs.versions.toml` 상단 주석에 적힌 Kotlin Gradle plugin 호환 범위를 지킨다.

### Navigation 3 의존성

모두 `gradle/libs.versions.toml`에 있다.

- 라이브러리
  - commonMain: `androidx.navigation3:navigation3-runtime:1.2.0`를 `api`로 둔다. Google이 모든 KMP 타깃용으로 배포한다.
  - iosMain: 호스트 composition의 owner를 위해 lifecycle-runtime-compose, lifecycle-viewmodel-compose(2.11.0), savedstate-compose(1.4.0)를 쓴다.
- 예제
  - `org.jetbrains.androidx.navigation3:navigation3-ui:1.2.0`(`NavDisplay`)
  - `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-navigation3:2.11.0`
  - kotlinx-serialization 플러그인(`@Serializable` 라우트용)
- 라이브러리는 `navigation3-ui`에 의존하지 않는다. iOS는 `NavDisplay`를 쓰지 않고, Android UI는 앱이 직접 그린다. 앱은 `NativeStack`/`NativeTabs`의 블록에서 decorated 엔트리를 받아 `NavDisplay(entries, onBack = onBack)`를 부른다.

### 타깃 제약

- 라이브러리는 Compose(runtime, foundation, ui)에 의존한다. 타깃은 android, jvm, iosArm64, iosSimulatorArm64, js, wasmJs다.
- `linuxX64`는 Compose가 지원하지 않아 제거했다. CI 매트릭스와 README에서도 뺐다.
- 웹(js, wasmJs) 타깃은 브라우저 전용이다.
  - Compose UI의 Skiko 런타임은 Node.js에서 불러올 수 없다. 그래서 `nodejs()`를 쓰지 않는다.
  - Compose 플러그인 검사(CMP-4906) 때문에 `binaries.executable()`을 선언한다.
- 웹 의존성이 바뀌면 `kotlin-js-store/yarn.lock` 갱신이 필요하다. `./gradlew kotlinUpgradeYarnLock kotlinWasmUpgradeYarnLock`을 실행한다.

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
  - 테스트는 설치된 앱을 bundle id로 띄운다.
- 결과는 `build/ui-tests/<UDID>/`에 남는다.
  - `xcodebuild.log`: 테스트 결과와 `[HARNESS]` 단계 표시
  - `app.log`: 앱의 시스템 로그
  - `shots/`: 약 1초 간격 스크린샷
- 예제 앱은 실행 인자 `-traceNav`를 받으면 다음 상태를 시스템 로그에 남긴다.
  - Kotlin 백스택(`KOTLIN`)
  - 네이티브 스택, 바 높이, 툴바와 탭 바 표시 여부(`NATIVE`)
  - ViewModel 생성과 정리, 화면 composition
- 테스트 목록
  - `ScrollUITests`: 6.5장 스크롤 연동
  - `NavigationUITests`: 6.6장 동기화, 루트 전환(`testRootSwitch`), 탭 위의 공유 화면(`testSharedStack`)
  - `BarItemsUITests`: 6.7장 바 아이템
  - `RestorationUITests`: 6.8장 상태 복원, 로그아웃 상태(로그인 스택) 복원
  - `GestureUITests`: 6.9장 뒤로가기 핸들러(forward 전용 핸들러, 루트 뒤로 버튼 포함), 6.5장 세로 드래그 제외 영역과 `ScrollState`
  - `SearchUITests`: 6.10장 검색과 키보드 여백
  - `GridUITests`: 6.5장 그리드(`LazyGridState`, `LazyStaggeredGridState`) 스크롤 연동
  - `ScrollUITests`에는 탭 전환 후 스크롤 유지와 가로 화면 테스트도 있다.
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
- 모든 테스트는 `-freshStart`로 시작해 복원된 상태를 무시한다. 복원 테스트만 두 번째 실행에서 이 인자를 뺀다.

## 6. 아키텍처 방향

### 6.1 iOS 구현 방식: UIKit (결정)

iOS 내비게이션은 **UIKit `UITabBarController`와 탭마다 하나씩 둔 `UINavigationController`로 구현하고, 모두 `iosMain`의 Kotlin/Native 코드로 제어한다.** 라이브러리에는 Swift 코드를 두지 않는다(7장).

**앱과의 연결 방식**
- 라이브러리는 Kotlin에서 루트 `UIViewController`(탭 컨트롤러)를 만들어 반환한다.
- SwiftUI 앱은 지금 예제처럼 `UIViewControllerRepresentable` 하나로 이 컨트롤러를 붙인다. UIKit 앱은 `window.rootViewController`에 넣는다.
- 라이브러리 사용자가 내비게이션용 Swift 코드를 추가로 작성하거나 복사할 필요가 없어야 한다.

**SwiftUI 대신 UIKit을 고른 이유**
- **배포**: KMP 라이브러리는 klib으로 배포된다. SwiftUI 코드를 넣으려면 다음 중 하나가 필요한데, 모두 비용이 크다.
  - 사용자가 Swift 파일을 복사한다.
  - 별도 Swift Package로 배포한다. 이 경우 Swift 코드가 앱마다 이름이 다른 Kotlin 프레임워크(`Shared` 등)를 import해야 해서, 패키지가 혼자서는 컴파일되지 않는다.
  - Swift를 static library로 빌드해 cinterop으로 klib에 넣는다.
  
  UIKit 방식은 klib 하나로 끝난다.
- **백스택의 주인**: Kotlin 백스택(Navigation 3)을 주인으로 두는 구조(6.3장)를 `setViewControllers`로 바로 반영할 수 있다. SwiftUI는 Kotlin 상태를 Swift `@Observable` 모델로 옮기는 연결 코드가 더 필요하다.
- **네이티브 동작**: `TabView`와 `NavigationStack`도 같은 시스템 탭 바와 내비게이션 바를 쓴다. 뒤로 버튼, 전환 애니메이션, 가장자리 스와이프, Liquid Glass 자동 적용은 두 방식이 같다.
- **SwiftUI 전용 기능**: SwiftUI 쪽 이점으로 꼽히는 `.tabBarMinimizeBehavior` 같은 기능은 iOS 26 전용이라, OS 버전 분기를 두지 않는 방침(6.4장)상 어차피 쓰지 않는다.

**감수하는 비용과 대응**
- SwiftUI의 path 바인딩이 대신해 주던 일을 직접 구현해야 한다. 백스택 diff, 스와이프 도중 취소 처리, 전환 중 들어온 요청의 대기열이 여기에 해당한다. 이 로직은 UIKit에 의존하지 않는 순수 Kotlin으로 분리하고 `commonTest`로 검증한다(8장).
- Kotlin/Native로 UIKit 클래스를 상속하거나 delegate를 작성하는 코드는 Swift보다 장황하다. 상속은 꼭 필요한 곳에만 쓴다.

**UIKit 구현 지침**
- 화면은 엔트리마다 `ComposeUIViewController`를 하나씩 두고, 엔트리 고유 키와 컨트롤러를 매핑해 재사용한다.
- 상세 화면의 탭 바 숨김은 `hidesBottomBarWhenPushed`로 처리한다. 이 값은 push하기 전에 설정해야 한다.
- 타이틀은 `navigationItem.title`, toolbar는 `navigationItem`의 bar button item 또는 `toolbarItems`로 구성한다. 버튼 동작은 selector 대신 `UIAction` 클로저로 연결한다.
- 네이티브에서 시작된 pop(뒤로 버튼, 스와이프)은 `UINavigationControllerDelegate`의 `navigationController(_:didShow:animated:)`에서 감지한다. 인터랙티브 전환은 `transitionCoordinator`로 완료 여부를 확인한다.
- 최소 지원 버전이 18.2이므로 iOS 18에서 추가된 UIKit API(`UITab`, `UITabGroup`, `UITabBarController.tabs`)는 쓸 수 있다.

**구현에 필요한 확인된 사실**
- Kotlin 2.4.21의 `platform.UIKit` 바인딩은 iOS 26.4 SDK에서 생성됐다. 바인딩에는 `UITabBarController.tabBarMinimizeBehavior`, `bottomAccessory`, `UIGlassEffect` 같은 iOS 26 API도 들어 있다. 하지만 OS 버전 분기를 두지 않기로 했으므로(6.4장, 7장) 이 API들은 쓰지 않는다.
- Kotlin에는 Swift의 `#available`이 없다. 컴파일러는 18.2보다 나중에 나온 API 호출을 막아 주지 않으므로, UIKit API를 쓸 때마다 Apple 문서에서 도입 버전을 확인한다.

**참고한 공식 구현 (SwiftUI 기반)**
- JetBrains 공식 가이드와 KotlinConf `lg-nav` 브랜치(10장 참고)는 같은 구조의 SwiftUI 셸을 사용한다.
  - `TabView` 안에 탭마다 `NavigationStack(path:)`을 둔다. Swift 코디네이터(`@Observable`)가 상세 화면 경로를 소유한다.
  - 탭 루트마다 `ComposeUIViewController`를 하나 두고, 그 안에서 `NavDisplay`를 실행한다. `snapshotFlow`로 백스택을 관찰하다가 루트 너머의 엔트리를 `onNavigate` 콜백으로 Swift에 넘기고, 자기 백스택은 루트까지 잘라낸다.
  - 상세 화면마다 새 `ComposeUIViewController`를 만든다. Swift는 `RouteWrapper`(UUID로 식별)로 같은 Route의 중복 push를 구분한다.
  - 가이드는 Kotlin `expect`/`actual` 코디네이터가 `UITabBarController`와 `UINavigationController`를 제어하는 UIKit 대안도 언급하지만, 구현 예시는 없다.
- KotlinConf `lg-nav`의 다음 한계는 이 라이브러리에서 그대로 따라 하지 않는다.
  - 상세 화면이 `NavEntry`/`entryProvider`를 거치지 않는다. 별도의 `when(route)` 디스패처(`ScreenContent`)로 화면을 다시 매핑하고, NavEntry decorator(saveable state, ViewModel store)도 적용하지 않는다.
  - 상세 화면 스택이 Swift 메모리에만 있어 상태 복원과 딥링크로 스택을 재구성할 수 없다. 스와이프 뒤로가기도 Kotlin에 전달되지 않는다.
  - 상세 화면에서 탭 바를 숨기는 처리가 없다.
  - 타이틀을 Route 프로퍼티에 넣는 방식이라, `data object` Route를 `data class`로 바꿔야 한다.
  - Compose 화면마다 `LocalUseNativeNavigation`을 확인해 자체 헤더, 뒤로 버튼, 하단 바를 숨겨야 한다.
  - iOS 26 미만에서는 네이티브 내비게이션을 쓰지 않고 기존 Compose 내비게이션으로 대체한다. 이 라이브러리는 iOS 26 미만에서도 네이티브 컨트롤러를 쓴다(7장).

### 6.2 계층 분리 원칙

- `commonMain`: 플랫폼 중립 공개 API를 둔다. 예: Route(`NavKey`), 내비게이터 인터페이스(push, pop, replace, popTo, 탭 선택), 엔트리 메타데이터(타이틀, 탭 바 표시 여부, toolbar 항목), 내비게이션 선언(`NativeStack`, `NativeTabs`).
- `androidMain`(그리고 jvm, web): 기존 Navigation 3 `NavDisplay`와 Material 3 구성을 그대로 통과시킨다. 공통 `NativeStack`/`NativeTabs`는 앱이 넘긴 블록을 실행할 뿐이다. 라이브러리가 Android UI를 새로 정의하거나 바꾸지 않는다.
- `iosMain`: Navigation 3 상태와 `UITabBarController`/`UINavigationController`를 잇는 어댑터를 Kotlin으로 둔다. 라이브러리에는 Swift 코드를 두지 않는다.
- 화면 Composable과 앱의 루트 Composable(`App()`)은 한 번만 작성한다. 플랫폼별로 복제하지 않는다. 플랫폼마다 다른 것은 진입점 한 줄과, iOS가 아닌 플랫폼에서만 실행되는 블록(앱의 Material 구성)이다.
- 기존 Route, Navigator, NavEntry, 백스택 관리 방식은 최소한으로 바꾼다. 전면 교체하지 않는다.

### 6.3 상태 동기화 규칙

어떤 구현 방식을 고르더라도 지킨다.

1. **단일 진실 공급원**: 백스택의 주인을 Kotlin과 네이티브 중 하나로 정하고 문서화한다. 양쪽이 각자 독립된 상태를 갖지 않게 한다. 딥링크, 상태 복원, Android와의 공유를 고려하면 Kotlin 백스택(Navigation 3)을 주인으로 두는 쪽이 기본값이다. 다르게 정했다면 7장에 이유를 남긴다.
2. **순환 방지**: Kotlin에서 네이티브로 변경을 적용하는 동안 발생한 delegate 콜백을 Kotlin에 다시 보내지 않는다. 네이티브 이벤트로 Kotlin 상태를 맞춘 뒤에는 그 변경을 네이티브에 다시 명령하지 않는다. 상태를 맞추는 diff는 멱등이어야 한다. 이미 일치하면 아무 일도 하지 않는다.
3. **중복 방지**: 엔트리를 Route의 동등성이 아니라 엔트리 고유 키로 식별한다. 같은 Route를 두 번 push할 수 있어야 하고, 컨트롤러 매핑이 엉키면 안 된다.
4. **인터랙티브 pop**: 가장자리 스와이프는 취소될 수 있다. 전환이 끝나기 전에는 Kotlin 상태를 바꾸지 않는다. 완료가 확정된 시점(예: `navigationController(_:didShow:animated:)`)에만 반영한다.
5. **전환 중 요청**: 전환 애니메이션이 진행 중일 때 들어온 요청은 큐에 쌓거나 합친다. 여러 단계가 한꺼번에 바뀌면 `setViewControllers(_:animated:)`처럼 한 번에 적용한다.
6. **생명주기와 상태**: 엔트리마다 `ViewModelStoreOwner`와 saved state를 유지한다. 엔트리가 백스택에서 실제로 제거될 때만 정리한다. 탭 전환이나 일시적인 화면 가림에서는 정리하지 않는다.
7. **스레드와 메모리**: UIKit 호출은 메인 스레드에서만 한다. `UINavigationController.delegate`처럼 weak로 잡히는 객체는 Kotlin 쪽에서 강한 참조를 유지한다. Kotlin/Native와 Objective-C 사이의 참조 순환(클로저, delegate)을 만들지 않는다.

### 6.4 iOS 버전 처리

- **OS 버전 분기를 두지 않는다.** iOS 18.2에서 쓸 수 있는 API만 사용하고, 지원하는 모든 버전에서 같은 코드 경로로 동작한다. 런타임 OS 버전 검사(`isOperatingSystemAtLeastVersion`, `systemVersion` 비교 등)로 동작을 나누지 않는다.
- iOS 26 이상: 앱을 iOS 26 SDK 이상으로 빌드하면 시스템 바(`UITabBar`, `UINavigationBar`, `UIToolbar`)에 Liquid Glass가 자동 적용된다. 앱이 `UIDesignRequiresCompatibility`로 이를 끈 경우는 예외다. 라이브러리는 커스텀 배경이나 appearance 설정으로 이를 덮어쓰지 않는다.
- iOS 18.2 이상 26 미만: 같은 네이티브 컨트롤러가 기존 디자인으로 동작한다. Compose 내비게이션으로 대체하는 경로는 두지 않는다.
- iOS 26 전용 기능은 지원하지 않는다. 예: 탭 바 최소화(`tabBarMinimizeBehavior`), 탭 바 accessory(`bottomAccessory`), 직접 만드는 glass 효과(`UIGlassEffect`). 공개 API 문서에 미지원으로 명시한다. 필요해지면 7장에 새 결정을 기록한 뒤 도입한다.
- 최소 지원 버전은 iOS 18.2다. 라이브러리 klib에는 배포 타깃이 따로 없어 빌드가 새 API 사용을 막지 못한다. 예제 앱의 Deployment Target(18.2)과 iOS 18.x 시뮬레이터 실행으로 이를 검증한다.
- 특정 플랫폼이나 OS 버전에서 지원하지 않는 기능을 지원하는 것처럼 가정하지 않는다. 지원하지 않는 기능은 문서에 명시하거나 명확하게 동작을 생략한다.

### 6.5 탑바와 스크롤 연동

iOS에서는 탑바 전체가 시스템 `UINavigationBar`다. Compose는 바 아래 콘텐츠만 그린다.

- **탑바 구성**
  - 뒤로가기는 `UINavigationController`가 만든다.
  - 타이틀은 `navigationItem.title`, 액션은 SF Symbol을 쓴 `UIBarButtonItem`과 `UIMenu`로 구성한다.
  - 화면 Composable 안에 Material `TopAppBar`를 직접 그리지 않는다. 탑바 정보는 엔트리 메타데이터(`nativeScreen(...)`, 6.6장)로 선언한다.
    - iOS에서는 라이브러리가 `navigationItem`으로 그린다.
    - Android에서는 앱이 `nativeTitle` 등을 읽어 자기 Material 3 `TopAppBar`에 쓴다. 라이브러리는 Android UI를 그리지 않는다(6.2장).
  - 액션 아이콘은 플랫폼별로 지정한다. Android는 `ImageVector`, iOS는 SF Symbol 이름이다.

**확인된 UIKit 제약** (2026-11-13, iOS 27.2와 18.5 시뮬레이터)
- 큰 타이틀이 접히는 동작, 바 배경 전환(iOS 18), scroll edge effect(iOS 26 이상)는 모두 화면의 content scroll view를 보고 동작한다. Compose에는 UIKit이 추적할 `UIScrollView`가 없다.
- 프로그램으로 `contentOffset`만 바꾸면 큰 타이틀이 연속으로 접히지 않는다. 순수 UIKit 스크롤 뷰로 한 대조 실험에서도 같았다. 레이아웃 시점에 펼침과 접힘 둘 중 하나로만 바뀐다.
- `isDragging()`/`isTracking()`을 override해 드래그 상태를 위장해도 연속으로 접히지 않는다. UIKit은 스크롤 뷰의 실제 pan 제스처 경로를 쓴다.

**방식: proxy `UIScrollView`가 세로 스크롤을 소유한다**
- **구조**
  - 화면 컨트롤러의 루트 뷰를 proxy `UIScrollView`로 두고 `setContentScrollView`로 등록한다.
  - Compose 뷰(`ComposeUIViewController.view`)는 proxy의 subview로 두고 viewport에 고정한다. 고무줄 overscroll 중에만 가장자리에 붙여 함께 움직인다.
- **스크롤 처리**
  - 세로 스크롤 제스처와 물리 동작(감속, 바운스, 상태 바 탭)은 proxy가 처리한다. 화면의 주 리스트는 `userScrollEnabled = false`로 두고, proxy offset을 delta로 받아 `dispatchRawDelta`로 따라간다.
  - Compose 쪽 사용자 스크롤은 반드시 꺼야 한다(`NativeScroll.userScrollEnabled`).
    - 켜 두면 Compose의 드래그 감지가 터치를 가져가 proxy pan이 시작되지 않는다.
    - 그러면 바는 Compose 스크롤을 따라 접히지만(프로그램 이동), 맨 위로 돌아와도 큰 타이틀이 다시 펼쳐지지 않는다. 2026-11-14 Hotpot의 라이선스 목록과 설정 화면에서 발견했다.
  - `userScrollEnabled`를 받지 않는 컨테이너(서드파티 리스트, 스크롤을 내장한 설정 화면 등)는 그대로 쓸 수 없다.
    - 스크롤을 끌 수 있는 컨테이너로 직접 그린다. 예: 컨테이너에 scroll state를 넘기지 않고 바깥에 `verticalScroll(state, enabled = nativeScroll.userScrollEnabled)`를 단다.
    - 상위 `nestedScroll`로 사용자 스크롤만 소비해 막는 방법은 실험에서 실패했다. 드래그 감지가 여전히 터치를 가져가 리스트가 아예 움직이지 않았다.
  - Compose가 스스로 스크롤하면 같은 delta로 proxy를 옮긴다. 대표적인 경우가 `animateScrollToItem`과 `bringIntoView`이고, `isScrollInProgress`가 켜져 있거나 proxy가 보낸 delta가 없는데 위치가 바뀐 경우로 판별한다. proxy가 애니메이션 중일 때는 덮어쓰지 않는다.
- **여백과 끝 처리**
  - 콘텐츠 여백은 `WindowInsets`가 아니라 proxy의 `adjustedContentInset`을 쓴다. 큰 타이틀이 접히는 동안 이 값이 프레임마다 바뀐다.
  - 끝까지 남은 거리를 알면 `contentSize`를 정확히 둔다. 네이티브 최대 offset이 Compose의 끝과 같아져서, 감속이 끝에 닿으면 UIKit이 보통 스크롤 뷰처럼 바운스한다.
    - `ScrollState`는 처음부터 안다(`maxValue - value`).
    - 리스트와 그리드는 마지막 아이템이 배치되면 안다(마지막 아이템 끝 + `afterContentPadding` − `viewportEndOffset`). staggered 그리드는 아이템을 가장 짧은 줄에 넣으므로, 마지막 아이템이 배치되면 그보다 아래에서 시작하는 아이템이 없다.
    - 끝을 모르는 동안은 `contentSize`를 넉넉히(화면 3배 이상) 두고 줄이지 않는다.
    - 끝을 넘는 이동(바운스)은 Compose에 보내지 않는다. 바운스는 화면 뷰만 움직이고 Compose는 끝에 그대로 있다.
  - 이전 방식은 `canScrollForward == false`가 된 프레임에야 `contentSize`를 실제 끝으로 줄였다. 2026-11-14 Hotpot에서 "바닥에서 끊긴다"는 보고가 있어 프레임 추적 로그로 확인했다.
    - 바운스가 되돌아오는 동안 Compose가 끝에서 2pt쯤 떨어지면 `canScrollForward`가 다시 `true`가 되어 `contentSize`가 커졌다.
    - 다음 프레임에 다시 줄면서 UIKit이 offset을 한 번에 잘라 냈다(약 77pt). 바운스가 도중에 끊겼다.
    - 예제 `testBottomEdge` 한 번에 이런 왕복이 28번, offset 잘림이 27번 있었다. 고친 뒤에는 각각 2번(처음 표시와 끝 도달)과 0번이다.
    - 고친 뒤 화면 이동량은 끝에 닿을 때 프레임당 41 → 36 → 24 → 16 → 9 → 4pt로 줄고, 넘어간 만큼 부드럽게 되돌아온다. Hotpot의 설정 탭(`ScrollState`)과 라이선스 목록(`LazyColumn`)을 녹화로 분석한 결과도 같았다.
  - 가로 pan은 proxy가 시작하지 않고 Compose(LazyRow, pager, slider)에 넘긴다.
- **구현 위치** (`library/src`, 패키지 `zone.ien.navigation`)
  - `commonMain/NativeScroll.kt`: 공개 API `rememberNativeScroll(LazyListState | LazyGridState | LazyStaggeredGridState | ScrollState)`, `NativeScroll(userScrollEnabled, contentPadding)`, `nativeContentPadding()`가 있다. 플랫폼 호스트 인터페이스 `NativeScrollHost`는 `LocalNativeScrollHost`로 제공된다. 호스트가 없으면 아무 일도 하지 않으므로 Android 동작은 바뀌지 않는다.
  - `commonMain/ScrollSync.kt`: 네이티브 offset과 Compose 위치 사이의 판단 로직이다. UIKit에 의존하지 않으며 `commonTest`에서 테스트한다.
  - `commonMain/ScrollPosition.kt`: 레이아웃마다 위치를 기록한다. 두 레이아웃 사이의 실제 이동량은 양쪽에 모두 보이는 같은 key의 아이템으로 계산한다. `dispatchRawDelta`가 보고하는 소비량은 리스트 끝에서 실제와 다를 수 있어서 쓰지 않는다.
    - 리스트, 그리드, staggered 그리드는 모두 `LazyLayoutSnapshot`(보이는 아이템의 key와 세로 offset)으로 기록한다. 그래서 이동량 계산은 하나다.
    - 절대 위치는 0번 아이템이 보이는 동안만 정확하다(큰 타이틀이 접히는 범위). 그 밖에서는 추정값이다. 리스트는 평균 아이템 높이, 그리드는 평균 행 높이, staggered 그리드는 평균 아이템 높이 × (index / lane 수)로 추정한다.
  - `iosMain/ScrollProxy.kt`: proxy `UIScrollView`다. 연결된 리스트가 없으면 스크롤하지 않는다.
  - `iosMain/ComposeScreenViewController.kt`: 공개 팩토리다. proxy를 루트 뷰로 두고 Compose를 호스팅한다.

**실제 터치로 검증한 결과** (XCUITest, iOS 27.2와 18.5 모두 통과. 프로토타입과 라이브러리 버전 `ComposeScreenViewController` + `rememberNativeScroll(LazyListState)`를 각각 확인했다.)
- 드래그하는 동안 큰 타이틀이 연속으로 접힌다. 손을 떼면 네이티브처럼 펼침이나 접힘으로 스냅된다.
- 플링하면 네이티브 감속으로 스크롤되고 인라인 바로 바뀐다. 바 배경은 iOS 18에서 블러와 구분선으로 바뀌고, iOS 26 이상에서는 scroll edge effect가 적용된다.
- 상태 바를 탭하면 맨 위로 스크롤되고 큰 타이틀이 다시 펼쳐진다.
- 맨 위에서 당기면 큰 타이틀이 늘어나고 고무줄처럼 늘어난다. 리스트 끝에서도 바운스한다.
- `animateScrollToItem`으로 Compose가 스크롤하면 바도 함께 접히고 펼쳐진다.
- 가로 스와이프(LazyRow)와 행 탭이 정상 동작한다.
- 검증 하네스는 `example/iosUITests`(`ScrollUITests`)에 있다(5장). `animateScrollToItem` 검증에 쓴 `-autoScroll` 단계는 예제가 내비게이션 앱으로 바뀌면서 빠졌다.

**아직 확인하지 않은 것과 한계**
- 실기기, iPad, 성능(접히는 동안 매 프레임 바뀌는 padding으로 인한 재측정)
- 가로 화면은 회전, push, pop이 동작하는 것만 확인했다(`ScrollUITests.testLandscape`). 노치 쪽 좌우 여백이 맞는지는 눈으로만 보았다.
- VoiceOver 스크롤과 키보드 포커스 이동
- 세로 드래그를 쓰는 Compose 제스처(순서 바꾸기, 세로 slider, 그리기 등)는 proxy pan과 충돌한다.
  - 해당 요소에 `Modifier.nativeScrollExclusion()`을 붙이면, 그 안에서 시작한 pan은 proxy가 시작하지 않고 Compose에 넘긴다.
  - proxy의 `gestureRecognizerShouldBegin`에서 터치 시작 위치(현재 위치 − 이동량)를 제외 영역과 비교한다.
  - `GestureUITests`로 확인했다. 패드 안 드래그는 패드 값만 바꾸고 페이지는 그대로이며, 패드 밖 드래그는 페이지가 스크롤된다.
  - 바텀시트처럼 화면 전체를 덮는 제스처는 아직 확인하지 않았다.
- 화면당 주 세로 스크롤 하나만 연동한다. 그 안에 중첩된 세로 스크롤은 연동하지 않는다.
- 소프트웨어 키보드
  - 키보드가 화면을 가리는 만큼 proxy의 `contentInset.bottom`을 늘린다(`KeyboardInset.kt`). `UITableViewController`가 하는 일이며, 일반 스크롤 뷰는 하지 않는다.
  - 그러면 `adjustedContentInset`을 통해 Compose 여백도 늘어나, 가려진 내용을 키보드 위로 스크롤할 수 있다(`SearchUITests`).
  - 화면이 보이는 동안에만 키보드 알림을 받는다.
  - Compose `TextField`에 포커스가 갈 때 CMP가 하는 자체 이동(`onFocusBehavior`)과 함께 쓸 때의 동작은 아직 확인하지 않았다.
- 상태 연결은 `LazyListState`, `LazyGridState`, `LazyStaggeredGridState`, `ScrollState`를 지원한다.
  - `ScrollState`는 Detail 화면에서 실제 터치로 확인했다(`GestureUITests`).
  - 그리드 두 종류는 Gallery 탭에서 실제 터치로 확인했다(`GridUITests`). 드래그하는 동안 큰 타이틀이 연속으로 접히고(106 → 86pt), 플링, 끝 바운스, 상태 바 탭, 타일 탭 후 Detail에서 돌아오기가 동작한다.
  - 세로 방향만 지원한다. `LazyRow`, `LazyHorizontalGrid`, `LazyHorizontalStaggeredGrid`의 state를 넘기면 안 된다. 가로 스크롤은 연결하지 않아도 Compose가 그대로 처리한다.
- Lazy 레이아웃(리스트, 그리드)의 전체 길이를 몰라 네이티브 스크롤 인디케이터는 끄고 있다.
- overscroll로 드러나는 영역은 `rememberNativeScroll(state, background = ...)`로 지정한 색이다. 지정하지 않으면 `systemBackgroundColor`다.
- 탭 바가 아래쪽 끝에서 배경을 바꾸는 전환(iOS 18)은 탭 바를 붙인 뒤 확인한다.
- 리스트 끝에서 멈추는 위치가 플링할 때마다 1~2px(1pt 미만) 달라진다. 눈에 보이는 문제는 없지만 원인은 아직 확인하지 않았다.

### 6.6 Navigation 3 연동 (iOS)

**구조**
- **호스트 composition**: `NativeNavigationViewController { ... }`의 content는 UI가 없는 composition에서 실행된다(`HostComposition.kt`).
  - 자체 `Recomposer`와 frame clock으로 메인 스레드에서 돈다. Molecule과 같은 방식이다.
  - 여기서 앱의 공통 `App()`이 실행된다. 백스택을 만들고(`rememberNavBackStack`), `NativeStack`이나 `NativeTabs`를 호출한다. 그러면 탭마다 `rememberDecoratedNavEntries`가 엔트리를 만든다.
  - Lifecycle(RESUMED), ViewModelStoreOwner, SavedStateRegistryOwner, SaveableStateRegistry를 제공한다. 그래서 `rememberNavBackStack`, `rememberSaveableStateHolderNavEntryDecorator`, `rememberViewModelStoreNavEntryDecorator`가 그대로 동작한다.
  - 숨긴 Compose 뷰를 두는 방식은 쓰지 않았다. 크기가 0인 뷰가 렌더링과 재구성을 계속하는지 보장할 수 없기 때문이다.
- **엔트리 하나 = 네이티브 화면 하나**(`EntryScreen`): 엔트리마다 `ComposeScreenController`(6.5장 proxy 포함)를 둔다. 호스트에서 decorator가 적용된 엔트리의 `Content()`를 그 화면의 composition에서 그린다.
  - Navigation 3는 "백스택에서 빠졌고 `Content()`가 composition을 떠남"을 일반 Kotlin 집합과 `DisposableEffect`로 추적한다. 그래서 다른 composition에서 그려도 decorator의 `onPop`(saveable state 제거, ViewModelStore clear)이 올바르게 호출된다(소스 확인: `DecoratedNavEntries.kt`).
  - 화면이 스택에서 제거되면 내용을 composition에서 내린다.
    - 앱이 애니메이션으로 pop하면 전환이 끝난 뒤에 내린다(`StackCoordinator.retire`). `NavDisplay`가 나가는 엔트리를 애니메이션 동안 그리는 것과 같다.
    - window를 떠난 화면의 composition은 다시 그려지지 않는다. 그래서 내용을 내리라는 상태 변경은 CMP가 그 scene을 dispose할 때(아래 CMP 동작, 최대 약 0.5초) 실제로 반영된다.
- **화면 메타데이터**: `nativeScreen(title, largeTitle, hidesTabBar, titleImage)`를 엔트리 metadata에 넣는다. 그러면 `navigationItem.title`, `largeTitleDisplayMode`, `hidesBottomBarWhenPushed`(push 전에 설정), `titleView`(6.7장 제목 이미지)가 적용된다. 키별로 다른 값이 필요하면 `entry<K>(metadata = { key -> ... })`를 쓴다.
  - 제목은 push 전에 정해져야 전환과 함께 보인다. 화면 상태와 무관한 제목은 metadata로 선언한다(6.7장 제목의 적용 시점).

**공통 선언** (commonMain `NativeNavigation.kt`)
- `NativeStack`, `NativeTabs`, `NativeTab`은 commonMain에 있다. 그래서 앱은 루트 Composable(`App()`)을 하나만 쓴다. iOS 진입점은 `NativeNavigationViewController { App() }`, Android는 `setContent { App() }`이다.
- 두 함수는 먼저 `rememberDecoratedNavEntries`로 엔트리를 만든다. `NativeTabs`는 탭마다 따로 만든다(`key(index)`). 그다음 플랫폼에 따라 나뉜다.
  - **iOS**: 루트 컨트롤러가 호스트 composition에 `LocalNativeNavigationHost`를 제공한다. 엔트리를 그 호스트(`RootNavigationHost`)에 넘기면 아래 동기화가 시작된다. 마지막 블록(`otherPlatforms`)은 실행하지 않으므로 `App()`은 iOS에서 UI를 내보내지 않는다.
  - **그 밖의 플랫폼**: 호스트가 없으면 `NativeBarState`를 제공하고 블록을 실행한다. 블록의 receiver(`NativeNavigationScope`)는 보이는 백스택의 엔트리(`entries`, 나눠 쓰는 `tabEntries`·`sharedEntries`), `onBack`, `canGoBack`, 제목(`NativeBarItems` 제목, 없으면 metadata 제목), 바 아이템, 검색, 탭, `tabBarVisible`을 준다. 앱은 이것으로 Material 구성과 `NavDisplay(entries, onBack = onBack)`를 그린다.
  - iOS에서 호스트 없이 호출하면 예외를 던진다(`usesNativeNavigation`, 플랫폼별 `actual`). iOS에서 Compose로 대신 그리는 경로를 두지 않는다(6.4장).
- Android도 탭마다 `rememberDecoratedNavEntries`를 따로 두므로, 탭을 바꿔도 다른 탭 엔트리가 백스택에 남아 상태(`rememberSaveable`, ViewModel)가 유지된다. Navigation 3의 여러 백스택 샘플(`MultipleBackStackSample`)과 같은 방식이다.
- `tabBarVisible`은 UIKit `hidesBottomBarWhenPushed`처럼 선택된 탭에서 push된 화면(루트 제외) 중 하나라도 `hidesTabBar`면 `false`다. iOS에서 루트 화면의 `hidesTabBar`가 어떻게 동작하는지는 확인하지 않았다(예제에는 그런 루트가 없다).
- **앱 틀** (commonMain `NativeNavigationFrame.kt`): 앱이 지금 `NavDisplay`를 감싸는 것(테마, DI `CompositionLocalProvider`, 루트 Scaffold)을 `NativeNavigationFrame(providers, frame) { ... }`에 넘긴다.
  - iOS가 아닌 플랫폼: `providers { frame { content } }`. 앱 전체를 한 번 감싼다.
  - iOS 호스트: `providers`만 호스트에 씌우고(상태 홀더, 알림창이 본다), `providers { frame { ... } }`를 각 화면에 씌운다. 화면에는 가장 바깥 엔트리 decorator로 들어간다(`rememberFramedDecorators`). iOS 화면은 composition이 따로 있어 호스트에서 감싼 값이 닿지 않기 때문이다.
  - `providers`는 UI를 내보내면 안 된다(호스트에서도 실행된다). `frame`은 화면 안에서만 실행되므로 UI를 둬도 된다.
  - `frame` 등 화면 안의 코드는 `LocalNativeScreen.current`(iOS 네이티브 화면에서 `true`)로 플랫폼별 처리를 한다. 예: 화면별 Compose 탑바 숨김, Android에서만 `imePadding()`.
  - 호스트 composition은 `LocalUIViewController`(루트 컨트롤러)와 `LocalDensity`(`UIScreen.mainScreen.scale`)를 기본으로 제공한다. 그래서 공통 코드의 adaptive 알림창과 `stringResource`가 호스트에서 그대로 동작한다.
  - 엔트리 decorator로 화면마다 테마를 씌우는 방식도 그대로 쓸 수 있다.
  - 예제는 테마를 decorator로 옮기면서 iOS 화면에도 `MaterialTheme`이 적용됐다. 그 전에는 iOS 화면에 테마가 없어 기본 글자 스타일이 Android(`bodyLarge`, 16sp/24sp)와 달랐다(`TextStyle.Default`). 그래서 Home 행 높이가 52pt로 커졌고, `ScrollUITests.testLandscape`는 가로 화면에서 아래로 밀린 Row 2 대신 Row 0을 누르도록 바꿨다.
- **함정: 저장 값을 `SideEffect`로 복사하는 상태 홀더.** 예제 `rememberAppNavigation()`은 `signedIn`, `selectedTab`을 `SideEffect`로 `rememberSaveable` 값에 복사했다. `SideEffect`는 호출한 scope가 recompose될 때만 실행된다.
  - 이전 iOS 진입 람다는 `navigation.signedIn`을 직접 읽어 함께 recompose됐다. `App()`으로 옮긴 뒤에는 그 값을 `App()` 안에서만 읽어 진입 람다가 recompose되지 않았다. 그래서 로그아웃 상태가 저장되지 않았다(`RestorationUITests.testRestoreSignedOut` 실패, iOS 27.2).
  - 상태 홀더가 `rememberSaveable`의 `MutableState`를 직접 들게 고쳤다(`var signedIn by signedInState`). 앱에도 같은 방식을 권한다.

**동기화** (`StackCoordinator.kt`, 6.3장 규칙 적용)
- **Kotlin → UIKit**: 현재 화면과 목표 엔트리를 `contentKey` 기준으로 앞에서부터 비교한다(`StackUpdate.kt`, 순수 함수, `commonTest`).
  - 하나를 push하면 `pushViewController`, 뒤가 잘리면 `popToViewController`, 그 밖에는 `setViewControllers`를 쓴다. replace와 딥링크는 `setViewControllers`에 해당한다.
  - 입력이 같으면 아무 일도 하지 않는다.
  - 첫 적용은 애니메이션 없이 한다.
- **UIKit → Kotlin**: 뒤로 버튼, 가장자리 스와이프, 뒤로 버튼 길게 누르기 메뉴, 탭 재탭으로 UIKit이 pop하면 `didShow`에서 사라진 화면 수만큼 `onBack()`을 호출한다.
  - 인터랙티브 pop 중에는 UIKit의 `viewControllers`가 잠시 줄어들지만, `didShow` 전에는 반영하지 않는다. 취소된 스와이프가 상태를 바꾸지 않는 이유다(실측으로 확인).
- **전환 중 요청**: `transitionCoordinator`가 있으면 적용을 미루고, `didShow`에서 최신 목표를 적용한다. 전환 중 목표의 content key 목록이나 공유 화면 경계가 바뀌면 새 Kotlin 백스택을 우선하고, 이전 네이티브 pop의 콜백으로 새 스택을 다시 줄이지 않는다. 메타데이터만 바뀐 경우에는 네이티브 pop을 그대로 반영한다.
- **탭**: `UITabBarControllerDelegate`의 선택은 `onTabSelected`로, Kotlin의 `selectedTab`은 `selectedIndex`로 반영한다. 둘 다 멱등이다.

**탭 위의 공유 화면** (`NativeTabs(sharedBackStack, onSharedBack)`)
- 어느 탭에서든 여는 화면(프로필, 편집 화면 등)을 백스택 하나에 둔다. 탭 화면 전체를 바깥 `NavDisplay`로 덮던 앱(Hotpot)이 그 백스택을 그대로 쓰게 하려고 만들었다. 비어 있어도 된다.
- **의미**: 공유 화면은 항상 선택된 탭 위에 있고, 항상 탭 바를 숨긴다(metadata와 무관). 그래서 공유 화면이 보이는 동안 사용자는 탭을 바꿀 수 없다.
  - 앱이 그동안 `selectedTab`을 바꾸면 공유 화면이 새 탭으로 옮겨 간다. 탭 재탭은 탭 바가 숨겨져 있어 일어나지 않는다.
  - 공유 화면이 늘 선택된 탭에 붙으므로 상태 복원에 따로 저장할 값이 없다. 백스택은 앱의 `rememberNavBackStack`이 복원한다.
- **iOS** (`TabsLayout`, `StackCoordinator`)
  - 선택된 탭의 목표 스택은 그 탭 엔트리 뒤에 공유 엔트리를 붙인 것이다. 나머지 탭에는 붙이지 않는다. diff와 전환은 기존 규칙(Kotlin → UIKit)을 그대로 쓴다.
  - 공유 화면은 `hidesBottomBarWhenPushed = true`로 만든다(`EntryScreen.shared`).
  - UIKit이 pop하면 사라진 화면마다 공유 화면은 `onSharedBack`, 탭 화면은 그 탭의 `onBack`을 위에서부터 부른다.
  - 화면에 보이는 탭만 애니메이션한다. 선택되지 않은 탭이나 이번에 선택이 바뀌는 탭의 스택은 애니메이션 없이 바꾼다. 그래서 앱이 탭을 바꾸면 공유 화면은 이전 탭에서 빠지고 새 탭에 바로 붙는다.
    - 이전에는 선택되지 않은 탭도 애니메이션으로 바꿨다. 화면 밖이라 보이지 않았고, 진행 중인 전환이 남아 다음 적용이 `didShow`까지 미뤄질 수 있었다.
  - 옮겨 간 공유 화면은 새 컨트롤러다. 같은 content key를 쓰므로(6.6장 같은 `contentKey`) 이전 탭의 화면이 composition을 떠난 뒤(최대 약 0.5초) 내용이 그려진다. `rememberSaveable`과 ViewModel은 같은 엔트리라 유지된다.
- **그 밖의 플랫폼**: 블록의 `entries`는 선택된 탭 엔트리 뒤에 공유 엔트리를 붙인 목록이고 `onBack`은 공유 화면이 있으면 `onSharedBack`이다. 그래서 탭용 Material 구성(`NavDisplay(entries)` + 하단 탭)을 그대로 쓸 수 있다. 상세 화면이 탭 바까지 덮으며 들어오게 하려면 `tabEntries`와 `sharedEntries`로 바깥 `NavDisplay`를 따로 그린다.
- 검증
  - `StackCoordinatorTest`(iosTest, 실제 `UINavigationController`): 공유 화면이 탭 화면 위에 쌓이고 탭 바를 숨기는지, UIKit pop이 맞는 콜백으로 가는지(탭 재탭 포함), 앱이 공유 화면을 뺄 때 콜백이 없는지 확인한다.
  - `NativeNavigationScopeTest`: `entries`, `tabEntries`, `sharedEntries`, 제목, `canGoBack`, `tabBarVisible`.
  - `NavigationUITests.testSharedStack`(실제 터치, iOS 27.2): Home에서 연 Profile이 탭 바를 숨기고, 그 위 Detail 50(짝수지만 탭 바 숨김), 스와이프와 뒤로 버튼으로 돌아온다. Settings에서 다시 연 Profile은 pop 때 정리된 상태(0)로 시작하고, 앱이 Gallery를 고르면 Profile이 `rememberSaveable` 상태(1)를 그대로 가진 채 Gallery로 옮겨 간다.

**루트 전환** (`NativeNavigationViewController.kt`)
- content는 `NativeStack`이나 `NativeTabs`를 한 번에 하나만 선언한다. 어느 것을 선언할지는 바뀔 수 있다. 예: `if (signedIn) NativeTabs(...) else NativeStack(signInStack, ...)`
- **선언 식별**: 각 호출은 `remember`한 객체로 식별한다. `DisposableEffect`에서 루트에 등록(`declare`)하고 해제(`retract`)한다.
  - Compose는 한 번의 apply에서 떠나는 쪽의 `onDispose`를 먼저, 들어오는 쪽의 effect를 그다음에, `SideEffect`를 마지막에 실행한다. 그래서 `if`/`else` 전환은 해제 → 등록 → 갱신 순서가 된다.
  - 두 선언이 동시에 composition에 있으면 예외를 던진다.
- **교체**: 다른 선언이 갱신을 보내면 새 계층(`StackLayout`, `TabsLayout`)을 만들어 루트의 child로 바꿔 끼운다.
  - 윈도우의 루트 뷰 컨트롤러를 바꿀 때처럼 교차 페이드(0.3초)한다. 이전 계층의 스냅샷(`snapshotViewAfterScreenUpdates`)을 위에 올려 흐리게 지우고, 새 계층은 처음부터 살아 있고 터치를 받는다.
  - 이전 계층의 화면은 즉시 모두 내린다(`StackCoordinator.dispose`). 내비게이션 delegate도 끊어서, 진행 중이던 전환의 콜백이 앱 백스택에 `onBack`을 보내지 않는다.
  - 새 선언 없이 해제만 되면 다음 메인 큐 차례에 이전 계층을 내리고 빈 화면을 둔다.
- **Navigation 3 의미**: 떠나는 선언의 `rememberDecoratedNavEntries`는 `onPop`을 호출하지 않는다. `NavDisplay`가 composition을 떠날 때와 같다(소스 확인: `DecoratedNavEntries.kt`).
  - decorator를 분기 안에서 만들면, decorator가 떠날 때 상태가 정리된다. `rememberViewModelStoreNavEntryDecorator`는 `clearAllKeys()`로 ViewModel을 정리하고(소스 확인: `RememberViewModelStoreProvider.kt`), saveable state holder는 저장한 상태를 함께 버린다.
  - decorator를 분기 밖에 두면 상태가 남는다. 이건 앱이 고른다.
- 선언 자체(예: `signedIn`)는 앱 상태다. `rememberSaveable`에 두면 상태 복원으로 돌아온다(`RestorationUITests.testRestoreSignedOut`).

**같은 `contentKey`를 가진 화면** (`ContentLeases.kt`)
- 같은 라우트를 두 번 push하면 Navigation 3에서는 같은 내용으로 취급하고 decorator 상태(ViewModel 포함)를 공유한다.
- UIKit은 가려진 화면도 살려 두므로 두 composition이 같은 key를 동시에 쓸 수 있다. 그러면 `SaveableStateHolder`가 예외를 던진다.
- 그래서 한 key는 한 번에 한 화면만 composition에 올린다.
  - 가장 최근에 나타난(`viewWillAppear`) 화면이 key를 가져간다.
  - 새 화면은 이전 화면이 내용을 내린 뒤에 그린다.
  - 스택에서 제거된 화면도 내용이 실제로 composition을 떠날 때까지 key를 쥔다. `forget`은 lease만 끝낸다.
    - 이전에는 제거 즉시 key를 놓았다. 그래서 닫은 화면의 composition이 아직 남아 있는 동안 같은 라우트를 다시 열면 `SaveableStateHolder`가 "Key ... was used multiple times" 예외를 던졌다(Hotpot `CustomIngredient(null)`, 2026-11-14).
    - 지금은 닫은 직후 같은 라우트를 다시 열면, CMP가 이전 화면의 scene을 정리한 뒤(최대 약 0.5초) 새 화면의 내용이 그려진다. metadata 제목은 그 사이에도 보인다.
- 한 번도 나타나지 않은 화면(딥링크의 중간 화면)은 내용을 만들지 않는다. 보일 때 처음 만든다.

**확인한 Compose Multiplatform 동작** (1.12.2 소스)
- `ComposeUIViewController`는 자신이나 상위 컨트롤러가 window hierarchy에 있으면 composition을 유지한다(`CMPViewController.m`의 `cmp_isInWindowHierarchy`). 그래서 스택에서 가려진 화면은 composition이 살아 있다.
- 컨트롤러가 hierarchy를 떠나면 0.5초 주기 검사나 dealloc 시점에 scene을 dispose한다. dispose는 composition을 닫으므로 Popup과 Dialog도 이때 닫힌다(`ComposeSceneMediator.dispose` → `scene.close()`).
- 첫 composition은 Compose 뷰가 window에 들어가 크기가 정해진 뒤에 한다(`runOnceOnAppeared`). push 중에는 UIKit이 내비게이션 바 전환을 정한 다음이다.
  - Scaffold 내용처럼 레이아웃 단계에서 subcompose되는 부분은 첫 프레임 측정 때 composition된다. Hotpot 실측으로 push 시작 후 30~110ms였다.
- Popup과 Dialog는 window 바로 아래 뷰에 전체 화면 레이어로 붙는다(`ComposeLayersViewController.layersContainerView`). 화면과 함께 움직이지 않는다.
- 화면이 UIKit 전환으로 움직인 뒤에도 접근성 요소의 좌표는 갱신되지 않는다.
  - pop으로 다시 나타난 화면의 접근성 좌표는 전환 시작 위치(화면 폭의 -30%)에 머문다(Hotpot, iOS 27.2 실측).
  - XCUITest에서 그 화면 요소의 `tap()`은 빗나간다. 테스트는 보이는 위치를 좌표로 누른다.
  - VoiceOver 초점 위치도 어긋날 것으로 보이지만 확인하지 않았다.
- CMP는 composition에 활성 `BackHandler`나 NavigationEvent 핸들러가 있으면, window 바로 아래 뷰에 자체 가장자리 스와이프 recognizer를 켠다(`UIKitNavigationEventInput`). 화면 뷰가 window에 있을 때만 붙인다. 라이브러리 화면은 콘텐츠에 자체 dispatcher를 제공하므로 이 recognizer를 쓰지 않는다(6.9장).

**API 사용 예** (예제 `App.kt`, commonMain)
```kotlin
@Composable
fun App(navigation: AppNavigation = rememberAppNavigation()) {   // rememberNavBackStack ×3
    NativeTabs(
        tabs = listOf(
            NativeTab("Home", "house", navigation.stacks[0]),
            NativeTab("Gallery", "square.grid.2x2", navigation.stacks[1]),
            NativeTab("Settings", "gearshape", navigation.stacks[2]),
        ),
        selectedTab = navigation.selectedTab,
        onTabSelected = { navigation.selectedTab = it },
        entryDecorators = rememberAppEntryDecorators(),    // saveable + ViewModel
        entryProvider = entryProvider,
    ) { MaterialChrome() }                                 // Android/Desktop만: Scaffold + NavDisplay(entries)
}

// App()의 바깥: 테마는 Android에서 앱 전체를 한 번, iOS에서 각 화면을 감싼다.
NativeNavigationFrame(frame = { content -> AppTheme(content) }) { /* 위의 NativeTabs / NativeStack */ }

// iosMain                                              // Android: setContent { App() }
fun MainViewController(restoredState: NSData?) = NativeNavigationViewController(restoredState, stateVersion = 3) { App() }
```

**남은 일과 한계**
- 탑바 액션과 toolbar는 6.7장, 검색(`UISearchController`)은 6.10장에서 다룬다.
- `hidesTabBar`는 UIKit의 `hidesBottomBarWhenPushed` 의미를 따른다. 이 값이 켜진 화면 위로 push한 화면은 자기 값과 상관없이 탭 바가 숨겨진 채로 있다(실측 확인).
- 프로세스 종료 후 상태 복원은 6.8장에서 다룬다.
- `onBack`이 실제로 pop하지 않으면 네이티브 화면은 이미 사라진 상태로 남는다. 다음 재구성 때 Kotlin 상태로 다시 push된다.
- 탭 개수가 바뀌면 모든 탭 스택을 새로 만든다. 이전 스택의 화면은 내린다.
- 탭 바는 루트에만 둘 수 있다. push한 화면 안의 탭(중첩 탭)은 지원하지 않는다. iOS 관례상 탭 바는 앱의 최상위 구조다.
- 모달(sheet, full-screen cover) API가 없다. 로그인처럼 흐름이 바뀌는 경우는 루트 전환으로 처리한다.
- 같은 decorator를 두 선언이 공유하고 같은 `contentKey`가 양쪽에 있으면, 이전 화면의 composition이 내려가기 전에 새 화면이 같은 key를 쓸 수 있다. 이 경우는 확인하지 않았다. decorator는 분기마다 따로 만든다.
- 호스트 content는 UI를 그리면 안 된다(그리면 예외).
- 화면 전환 애니메이션은 UIKit 기본값이다. Navigation 3의 transition spec은 iOS에서 쓰지 않는다.
- 화면 안의 `BackHandler`와 `NavigationBackHandler`는 6.9장에서 다룬다.
- Hotpot을 공통 `App()` 구조로 옮기면서(2026-11-14, Hotpot worktree `feature/test-cmp-swift-nav`) 앱이 직접 채운 부분이다.
  - 해결함(2026-11-15): 호스트의 `LocalDensity`·`LocalUIViewController`는 라이브러리가 제공한다. 네이티브 화면 여부는 `LocalNativeScreen`, 테마·DI·루트 Scaffold는 `NativeNavigationFrame`으로 준다. Hotpot의 iOS 전용 코드는 `MainViewController` 한 줄(`NativeNavigationViewController { App(rootGraph) }`)만 남았고, expect/actual 3개 파일과 `LocalNativeChrome`을 지웠다.
  - 루트 전환(`NativeStack` ↔ `NativeTabs`)은 Android에서 블록이 통째로 바뀌어 전환 애니메이션이 없다.
    - 앱에서 새 블록을 페이드인해 봤다. 그런데 Main의 첫 구성이 무거운 동안 프레임이 늦어 흰 화면이 4~6초 보였다(에뮬레이터). 그래서 뺐다.
  - Main 위에 올라온 스택(게스트 로그인)의 첫 화면에서 Android 시스템 뒤로가기는 블록의 `NavDisplay`가 받지 않는다(엔트리가 하나뿐). 앱이 `NavigationBackHandler`로 Main에 돌아가게 했다.

### 6.7 탑바 액션과 toolbar

**API** (commonMain `NativeBarItems.kt`)
- 화면 Composable 안에서 선언한다. 선언 위치가 화면 안이라 ViewModel 상태를 따라 바뀔 수 있다.

```kotlin
NativeBarItems(
    title = "Detail 3 (2)",                  // 선택. nativeScreen 타이틀을 덮어쓴다
    leading = listOf(...),
    trailing = listOf(
        NativeBarButton("Add one", systemImage = "plus", onClick = viewModel::increment),
        NativeBarButton("More", systemImage = "ellipsis.circle", menu = listOf(NativeMenuAction("Reset", destructive = true) { ... })),
    ),
    toolbar = listOf(NativeBarButton("Decrease", systemImage = "minus.circle", enabled = count > 0) { ... }),
)
```

**iOS** (`NativeBarApplier.kt`)
- **매핑**
  - `leading`과 `trailing`은 `navigationItem`의 왼쪽·오른쪽 버튼(`UIBarButtonItem`)이 된다. 둘 다 왼쪽에서 오른쪽 순서로 선언한다.
  - 뒤로 버튼은 유지한다(`leftItemsSupplementBackButton`).
  - 버튼 동작은 `UIAction` 클로저, 메뉴는 `UIMenu`다. destructive 속성을 지원하고, `title`은 접근성 레이블이 된다.
- **툴바**
  - `toolbar`는 화면의 `toolbarItems`가 된다. 버튼 사이에는 flexible space를 넣는다.
  - 툴바는 navigation controller 하나에 하나뿐이다. 그래서 나타나는 화면(`viewWillAppear`)이 자기 항목 유무에 따라 보이거나 숨긴다. 스와이프를 취소해 원래 화면이 다시 나타날 때도 같다.
- **재생성 최소화**: 버튼 모양(`BarShape`: 제목, 아이콘, 활성 여부, 메뉴 구성)이 같으면 UIKit 아이템을 새로 만들지 않는다. 클릭할 때 최신 선언의 람다를 찾아 호출하므로, 다시 그릴 때마다 람다가 새로 생겨도 깜빡임이 없고 열린 메뉴도 닫히지 않는다. 버튼·메뉴·툴바의 액션은 `NativeBarApplier`를 약하게 참조해 화면 컨트롤러로 돌아가는 강한 참조 순환을 만들지 않는다.
- **적용 시점**: 선언은 `NativeBarState`에 등록되고, 화면 호스트가 리스너로 같은 프레임에 바로 반영한다.
- **제목**: 선언한 제목이 있으면 그것을, 없으면 metadata 제목을 쓴다. 값이 바뀔 때만 `navigationItem.title`에 쓴다. 화면은 자주 다시 그려지고(예: 1초마다 바뀌는 타이머) 그때마다 같은 제목을 다시 선언하기 때문이다.
- **제목의 적용 시점**: `NativeBarItems`의 제목은 화면 내용이 composition된 뒤에 등록된다. 그때는 push가 이미 시작된 뒤다(6.6장 CMP 동작).
  - UIKit은 push를 시작할 때의 제목으로 바를 전환한다. 그래서 이 제목은 전환이 끝난 뒤에 나타난다.
  - Hotpot 녹화로 확인했다(2026-11-14, iOS 27.2). push하는 동안 들어오는 화면에 큰 제목과 뒤로 버튼이 없었다. Hotpot은 화면 내용이 Scaffold 안에 있어 첫 측정 때 등록되므로 특히 늦었다.
  - 그래서 화면 상태와 무관한 제목은 `nativeScreen(title)`로 선언한다. `NativeBarItems`의 `title`은 화면이 보이는 동안 바뀌는 제목에 쓴다.
  - Hotpot은 iOS 호스트에서 라우트별 제목을 엔트리 metadata로 넣었다. 그 뒤로는 push 첫 프레임부터 큰 제목이 내용과 함께 들어왔다.
  - 버튼(`leading`, `trailing`)도 같은 시점에 등록되므로 전환이 끝난 뒤에 나타날 수 있다.
- **제목 이미지** (2026-11-15, 시험용. 사용자가 되돌릴 예정이다): `nativeScreen(titleImage = { painterResource(Res.drawable.logo) })`
  - 로고처럼 제목 글자 대신 이미지를 보일 때 쓴다. `title`은 그대로 둔다. VoiceOver와 위에 push한 화면의 뒤로 버튼이 쓴다.
  - iOS에서는 `navigationItem.titleView`의 `UIImageView`가 된다(`TitleImage.kt`, `NativeBarApplier`). 접근성 레이블은 제목이고 trait는 header다.
  - **그리는 곳**: painter 람다는 호스트 composition에서 실행한다(`rememberTitleImages`). 만든 `UIImage`를 content key별로 `TitleImages`에 넣은 뒤 스택을 갱신하므로, push할 화면이 만들어질 때 이미 이미지가 있다.
    - 화면 composition에서 그리면 늦다. 화면 내용은 push가 시작된 뒤에 composition되므로(6.6장 CMP 동작) 이미지가 전환이 끝난 뒤에 나타난다.
  - **크기**: 높이 28pt, painter 비율 유지(intrinsic size가 없으면 정사각형). 화면 배율의 비트맵으로 만든다.
    - painter보다 작게 그릴 때(512px PNG 등) 먼저 목표의 최대 4배 크기로 그린다. 그다음 Core Graphics 고품질 보간으로 줄인다. painter는 bilinear(밉맵 없음)로 줄여서, 크게 줄이면 계단이 생기기 때문이다.
    - 변환은 Skia(`asSkiaBitmap`, PNG 인코딩) → `UIImage` → `UIGraphicsImageRenderer`다. 엔트리마다 painter가 바뀔 때만 한다.
  - **한계**
    - 호스트에는 화면의 테마와 다크 모드가 없다. 다크 모드용 리소스(`drawable-dark`)와 테마 색은 적용되지 않는다.
    - 높이는 고정이다. 바꾸는 옵션이 없다.
  - **큰 제목 화면**: 펼쳐진 동안 로고는 위 인라인 줄에, 큰 제목 글자는 그 아래에 함께 나온다. 스크롤해 바가 접히면 인라인 줄에는 제목 글자 대신 로고가 남는다(iOS 27.2 실측). 로고만 보이려면 `largeTitle = false`로 둔다.
  - **그 밖의 플랫폼**: `NativeNavigationScope.titleImage`로 넘긴다. 예제는 `TopAppBar`의 title 자리에 `Image(painter, Modifier.height(28.dp))`를 그린다(Pixel 7a에서 확인).
  - XCUITest의 `navigationBars["Profile"]`는 `titleView`가 있어도 그대로 바를 찾는다(`testSharedStack` 통과).

**Android와 Desktop**
- 라이브러리는 아무것도 그리지 않는다.
- `NativeStack`/`NativeTabs`가 블록에 `NativeBarState`를 제공하고, 블록의 `NativeNavigationScope`로 `title`, `leading`, `trailing`, `toolbar`, `search`를 넘긴다. 앱은 이것으로 자기 Material UI를 그린다(예제 `App.kt`의 `MaterialChrome()`).
- 두 함수 없이 `NavDisplay`만 쓰는 앱은 `NativeBarState`를 만들어 `ProvideNativeBarState(state) { NavDisplay(...) }`로 감싸면 같은 값을 받는다.
- `NavDisplay`가 전환 중에 두 화면을 함께 그려도 가장 최근에 등록한 화면이 이긴다(`commonTest`).

**아이콘**: `systemImage`는 SF Symbol 이름이다. Android 아이콘은 앱이 정한다(예제는 텍스트 버튼).

**검증** (2026-11-13, iOS 27.2와 18.5, `BarItemsUITests` 통과)
- 바 버튼과 툴바 버튼이 ViewModel을 바꾼다. 개수가 0이면 "Decrease"가 비활성이 된다.
- 동적 타이틀 "Detail 3 (1)"이 메뉴의 Reset 뒤에 metadata 타이틀로 돌아간다.
- 메뉴의 내비게이션 동작이 동작한다.
- 툴바는 Detail에서만 보이고 Home으로 돌아오면 숨겨진다. 탭 바가 보이는 화면(Detail 42)에서는 툴바가 탭 바 위에 표시된다.
- 아직 composition되지 않은 화면은 툴바 표시를 결정하지 않는다. 그래서 툴바가 있는 화면끼리 push하거나 pop할 때 깜빡임이 없다(100ms 간격 추적으로 확인).
- iOS 26 이상에서는 내비게이션 바 버튼이 glass 캡슐로 묶이고 툴바 버튼이 떠 있는 glass 버튼이 된다. iOS 18에서는 기존 모양이다.

**남은 일과 한계**
- 같은 `contentKey`를 연속으로 push하면, 새 화면은 이전 화면이 내용을 내려놓은 뒤에야 바 아이템을 등록한다(6.6장). 이 경우에만 툴바가 짧게 사라졌다 나타날 수 있다.
- 뒤로 갈 때 큰 제목과 인라인 제목이 겹쳐 보이는 현상(Hotpot 실기기, 2026-11-14)은 UIKit 자체 동작이다(2026-11-15 확인).
  - 조건
    - 큰 제목 루트를 스크롤해 바가 접혀 있다.
    - 그 상태에서 큰 제목(펼침) 상세 화면을 push한다.
    - 가장자리 스와이프로 돌아간다.
  - 스와이프 도중 바가 큰 제목 높이에서 인라인 높이로 줄어든다. 그래서 떠나는 화면의 큰 제목이 바 윗줄로 올라가 인라인 제목과 겹친다.
  - Compose와 라이브러리 없이 순수 UIKit으로 같은 구조를 만들어 확인했다. `UITabBarController` + `UINavigationController`, `prefersLargeTitles`, `largeTitleDisplayMode = always`, `setContentScrollView`로 구성했다. iOS 27.2 시뮬레이터에서 스와이프를 멈추고 찍으니 같은 모습이었다.
  - 상세 화면을 인라인 제목(`nativeScreen(largeTitle = false)`)으로 두면 겹치지 않는다(같은 실험).
  - 권장: 큰 제목은 스택의 첫 화면(탭 루트)에만 쓰고, push되는 화면은 인라인 제목으로 둔다. 예제가 이미 그렇고, Hotpot도 상세 화면을 인라인으로 바꿨다(2026-11-15). 라이브러리는 metadata 값을 그대로 따르고 강제하지 않는다.
  - 처음에 재현하지 못한 이유: 루트를 스크롤하지 않은 상태만 봤다. 이번 재현에서는 XCUITest의 `tap()`이 화면 밖 항목을 누르려고 목록을 스크롤해 루트의 바가 접혔다. Hotpot 스크린샷도 설정 목록 아래쪽 항목에서 들어간 경우였다.
  - 앞서 고친 두 경우(늦게 등록되는 제목, 같은 제목의 반복 설정)는 이 현상의 원인이 아니었다. 다만 전환 중 제목이 늦게 나타나거나 바뀌는 다른 문제를 막으므로 그대로 둔다.
- 바 버튼에 Compose UI를 넣는 기능(`customView`)은 지원하지 않는다(네이티브가 아님).
- iOS 26의 `UIBarButtonItem` 그룹과 공유 배경 같은 세부 설정은 노출하지 않는다. 시스템 기본값을 쓴다.

### 6.8 상태 복원 (iOS)

**범위**: 시스템이 백그라운드에서 앱을 종료한 뒤 다시 실행했을 때 다음을 되살린다.
- 탭별 백스택과 선택된 탭
- 엔트리별 `rememberSaveable` 상태(스크롤 위치 포함)

ViewModel은 저장하지 않는다(Android도 프로세스가 죽으면 잃는다). 화면별 composition의 `SavedStateHandle`도 저장하지 않는다.

**API**
- `saveNativeNavigationState(controller): NSData?`가 상태를 직렬화한다.
- `NativeNavigationViewController(restoredState: NSData?) { ... }`가 그 데이터로 복원한다.
- 어디에 저장할지는 앱이 정한다. 예제는 SwiftUI `@SceneStorage`를 쓴다(`ContentView.swift`, 약 15줄).
  - iOS 고유 의미 그대로다. 시스템이 종료하면 복원하고, 사용자가 앱 전환기에서 강제 종료하면 버리고, scene(창)마다 따로 저장한다.
  - UIKit 앱은 `UIScene.stateRestorationActivity`로 같은 일을 한다.
- **저장 시점**: `scenePhase`가 `.inactive`가 될 때 저장한다(`.background` 때도 다시 저장한다). 실측 결과, iOS는 `.background` 처리 전에 scene 상태를 기록한다. 그래서 `.background`에서만 저장하면 복원되지 않는다.

**구현** (`HostComposition.kt`, `SavedStateCodec.kt`)
- 호스트 composition의 `SaveableStateRegistry`와 `SavedStateRegistry`를 통째로 저장한다.
- 엔트리별 상태는 `SaveableStateHolder`의 saver가 모은다. saver는 아직 composition에 있는 다른 화면의 registry까지 함께 저장한다.
- **직렬화 형식**: 자체 텍스트 형식이다(태그 + 길이 접두). 새 의존성이 없다.
  - 지원 타입: 기본형, 문자열, Char, 기본형 배열, Array, List, Set, Map, `SavedState`, `mutableStateOf`(기본 policy 3종), `mutableIntStateOf`/`Long`/`Float`/`Double`
  - snapshot state는 정확한 타입으로 되살린다. `MutableIntState`를 `MutableState`로 되살리면 `ClassCastException`이 나기 때문이다.
- **저장할 수 없는 값**(enum, 임의 객체)
  - 등록은 막지 않는다(`canBeSaved = { true }`). Android처럼 막으면, 지금 iOS에서 메모리 저장으로 문제없이 도는 공통 코드가 크래시한다.
  - 대신 저장할 때 그 값을 담은 registry 항목만 뺀다(`prune`). 그 `rememberSaveable`만 초기값으로 시작한다. 뺀 항목은 로그로 남긴다.
- 읽을 수 없는 데이터(형식이 다르거나 손상됨)는 무시하고 새로 시작한다.
- 복원된 화면의 스크롤 위치는 화면이 보이기 전에 proxy에 반영된다. 그래서 스크롤된 화면으로 돌아왔을 때 바가 접힌 상태로 맞게 나온다(실측).

**검증** (`RestorationUITests`, `SavedStateCodecTest`)
- 상태를 만들었다. Settings 카운트 2, Home 스크롤, 스택 Home → Detail 42 → Detail 43.
- 홈 버튼을 눌러 백그라운드로 보낸 뒤 종료하고 다시 실행했다.
- 결과: 맨 위 Detail 43, 뒤로 가면 Detail 42, Home 스크롤 위치와 접힌 바, Settings 카운트 2가 모두 복원됐다.
- `simctl terminate`로 시스템 종료와 비슷하게 끝낸 수동 실험에서도 복원됐다(`-demoDeepLink`).

**남은 일과 한계**
- 앱을 업데이트해 라우트 구조(직렬화)나 `rememberSaveable` 호출 구성이 바뀌면, 예전 데이터를 복원하다 예외가 날 수 있다.
  - `rememberSaveable` 값은 호출 위치(composite key)로 짝지어진다. 그래서 호출을 끼워 넣으면 뒤 호출이 다른 타입의 값을 받는다.
  - 실제 사례(2026-11-13): 예제 `rememberAppNavigation()`에 `rememberNavBackStack(SignInRoute)`를 넣었다. 이전 빌드가 저장한 상태를 복원하자, 그 호출이 이전의 `selectedTab`(`MutableIntState`)을 받아 `ClassCastException`이 났다.
  - iOS는 같은 scene 데이터를 실행할 때마다 다시 준다. 그래서 크래시가 실행마다 반복됐다(강제 종료나 재설치 전까지).
  - **버전으로 구분**: `NativeNavigationViewController(restoredState, stateVersion = N)`의 버전을 올리면, 이전 버전 데이터를 무시하고 새로 시작한다. 버전은 저장 데이터에 함께 들어간다. 예제는 위 변경으로 1이 됐다.
  - **안전장치**: 복원 데이터로 시작한 호스트의 첫 composition이 예외로 실패하면, 복원 데이터를 버리고 새 호스트 composition으로 다시 시작한다(`startHost`). 로그를 남긴다. 복원 데이터 없이 실패하면 앱 오류이므로 그대로 던진다. 같은 시나리오로 실측해, 크래시 없이 새로 시작하고 이후 저장과 복원이 정상인 것을 확인했다.
  - **안전장치의 한계**
    - 예외가 나지 않고 타입이 우연히 맞아 엉뚱한 값으로 복원되는 경우는 막지 못한다. 그래서 구조를 바꾸면 버전을 올려야 한다.
    - 나중에 처음 composition되는 곳에서 나는 예외도 막지 못한다. 예: 각 화면 composition 안의 `rememberSaveable`, 처음에는 그려지지 않던 분기.
  - 라이브러리 코드가 바뀌어도 composite key가 달라질 수 있다. 실측에서는 라이브러리의 호스트 content 람다 위치만 바뀐 빌드가 이전 데이터를 크래시 없이 무시했다. 원인까지는 확인하지 않았다.
- 저장 데이터 크기는 예제 기준 약 1.4KB다. 스택과 화면이 많아지면 커진다. `@SceneStorage`에 적합한 크기인지 아직 측정하지 않았다.

### 6.9 화면 안의 뒤로가기 핸들러 (iOS)

화면 Composable 안의 `BackHandler`(deprecated), `NavigationBackHandler`, `NavigationEventHandler`(navigationevent)를 Android와 같은 의미로 연결한다.
- 뒤로가기(back)가 켜진 핸들러가 있으면 뒤로가기는 pop하지 않고 그중 가장 위의 핸들러로 간다.
- 없으면 네이티브 pop이 된다. 앞으로 가기(forward)만 켜진 핸들러는 뒤로가기를 받지 않는다. `NavigationEventHandler`는 `isForwardEnabled`가 기본 `true`라서, 저장 확인 가드를 꺼 둔 화면이 흔히 이 경우다.

**구현** (`NativeBackHandling.kt`, `NativeBackSwipe.kt`)
- **화면 전용 dispatcher**: 화면마다 `NavigationEventDispatcher`를 만들어 콘텐츠에 `LocalNavigationEventDispatcherOwner`로 제공한다. 콘텐츠의 핸들러는 CMP의 dispatcher가 아니라 여기에 등록된다.
  - navigationevent는 활성 핸들러 유무를 back과 forward를 합쳐서만 알려 준다(`onHasEnabledHandlersChanged`). 그래서 뒤로가기를 받을 핸들러를 미리 정하지 않고, dispatcher가 실제로 보낼 때 고르게 한다(back이 켜진 핸들러 중 가장 위).
  - CMP의 dispatcher에는 핸들러가 없으므로 CMP의 자체 가장자리 스와이프는 켜지지 않는다.
- **뒤로 버튼**: 활성 핸들러가 있는 동안 `navigationItem.backAction`(iOS 16+)이 dispatcher로 뒤로가기를 보낸다. 받는 핸들러가 없으면 dispatcher의 fallback(`onBackCompletedFallback`)이 네이티브 pop을 한다.
  - 루트 화면에는 `backAction`을 두지 않는다. 루트에 `backAction`이 있는 채로 왼쪽 바 아이템이 다시 설정되면 UIKit이 루트에도 뒤로 버튼을 보인다(실측).
  - 화면의 위치가 바뀔 수 있으므로 나타날 때(`viewWillAppear`)와 스택이 바뀔 때마다 다시 정한다.
- **가장자리 스와이프**: 내비게이션 컨트롤러마다 `UIScreenEdgePanGestureRecognizer`를 하나 둔다(`NativeBackSwipe`). `interactivePopGestureRecognizer`는 이것이 실패하기를 기다린다.
  - 스와이프가 시작될 때 맨 위 화면이 정한다. 가장 낮은 우선순위의 탐지 핸들러를 잠깐 켜고 시작 이벤트를 보낸다.
  - 콘텐츠 핸들러가 받으면 진행 이벤트(predictive back)와 완료·취소를 계속 그 핸들러에 보낸다.
  - 탐지 핸들러가 받으면 취소하고 recognizer를 실패시킨다. 그러면 UIKit 인터랙티브 pop이 동작한다. 콘텐츠 핸들러에는 아무 이벤트도 가지 않는다.
  - 완료 여부는 손을 뗄 때 정한다. 빠르게 튕기면 방향으로, 아니면 화면 절반을 넘었는지로 정한다(`BackSwipeDecision.kt`, `commonTest`).
  - 라이브러리 화면이 처음 나타날 때(`viewWillAppear`) 그 내비게이션 컨트롤러에 한 번 설치한다. 그래서 앱이 직접 만든 `UINavigationController`에 단독 화면(`ComposeScreenViewController`)을 넣어도 동작한다.
  - 내비게이션 컨트롤러의 뷰가 recognizer를, recognizer가 스와이프 객체를 잡는다. 스와이프 객체는 내비게이션 컨트롤러를 약하게 참조해 순환을 만들지 않는다(6.3장 7). 뒤로 버튼의 `UIAction`도 화면을 약하게 참조한다.
  - 계층이 루트에서 내려가면(`StackCoordinator.dispose`) 진행 중인 스와이프를 취소하고 recognizer를 끈다. recognizer는 내비게이션 컨트롤러와 함께 사라진다.

**검증** (2026-11-14)
- `NativeBackHandlingTest`(iosTest, 실제 dispatcher와 UIKit 컨트롤러)
  - forward 전용 핸들러는 스와이프와 버튼을 받지 않고, 버튼은 pop한다.
  - back 핸들러는 스와이프(시작·진행·완료·취소)와 버튼을 받는다.
  - 루트 화면에는 `backAction`이 없다.
- `GestureUITests`(실제 터치)
  - `testBackHandler`: 가드를 켠 Detail 3에서 뒤로 버튼과 스와이프가 핸들러로 갔고 pop은 없었다. 그 위 Detail 4는 네이티브 스와이프로 pop됐다. 가드를 끄면 뒤로 버튼과 스와이프로 pop된다.
  - Detail 화면에는 forward 전용 핸들러를 항상 둔다. 그래서 Detail을 쓰는 모든 테스트(`NavigationUITests` 포함)가 이 경로를 지난다. 수정 전 코드에서는 G5b(스와이프가 pop되지 않음)에서 실패했다.
  - `testRootHandlerHasNoBackButton`: Settings 탭 루트에서 핸들러를 켜고 왼쪽 바 아이템을 바꿔도 뒤로 버튼이 없다. 수정 전 코드에서는 `Back` 버튼이 생겨 실패했다.
- 두 문제는 Hotpot 앱 연동에서 발견했다. 저장 확인용 `NavigationEventHandler`가 있는 화면에서 뒤로가기가 막혔고, 바텀시트(Compose `Dialog`)를 쓴 뒤 탭 루트에 뒤로 버튼이 생겼다. 고친 라이브러리로 두 화면 모두 정상인 것을 실제 터치로 확인했다.

**한계**
- 뒤로 버튼을 길게 눌러 여는 메뉴로 여러 단계를 한 번에 pop하면 핸들러를 거치지 않는다.
- 핸들러가 받은 스와이프에는 UIKit 전환 애니메이션이 없다. 화면이 진행 이벤트로 직접 표현해야 한다.
- CMP의 dispatcher에 직접 연결된 다른 입력(예: 하드웨어 키보드)이 있다면, 그 뒤로가기는 화면 핸들러에 닿지 않는다. 확인하지 않았다.

### 6.10 검색 (iOS)

**API** (commonMain `NativeBarItems.kt`): 화면 Composable 안에서 `NativeSearchBar(query, onQueryChange, placeholder)`로 선언한다. 검색어 상태와 필터링은 앱(화면)이 갖는다.

**iOS**: 화면의 `navigationItem.searchController`(`UISearchController`)가 된다.
- **설정**: `obscuresBackgroundDuringPresentation = false`, `hidesSearchBarWhenScrolling = true`, 화면에 `definesPresentationContext = true`를 준다.
- **입력 반영**: 입력은 `UISearchResultsUpdating`으로 `onQueryChange`에 전달된다. 앱이 검색어를 바꾸면 필드 텍스트에 반영한다. 같은 값이면 쓰지 않아 커서가 튀지 않는다.
- **모양**: 큰 타이틀 아래에 붙고, 스크롤하면 숨는다(proxy 덕분에 네이티브 동작). 검색 중에는 내비게이션 바가 올라가고 시스템 지우기·취소 버튼이 나타난다.

**Android와 Desktop**: 라이브러리는 아무것도 그리지 않는다. 앱이 블록의 `search`(또는 `NativeBarState.search`)를 읽어 직접 그린다(예제: `TopAppBar` 아래 `OutlinedTextField`).

**검증** (`SearchUITests`, iOS 27.2와 18.5)
- "Row 4"를 입력하면 Compose 리스트가 걸러진다.
- 키보드가 올라온 상태에서 마지막 결과를 키보드 위로 스크롤해 탭할 수 있다.
- 지우면 모든 행이 돌아온다.

**한계**
- 검색 범위(scope bar), 토큰, 별도 결과 컨트롤러는 지원하지 않는다.
- iOS 26의 탭 바 검색 탭(`UISearchTab`)은 iOS 26 전용이라 쓰지 않는다(6.4장).

## 7. 결정 기록

결정을 내릴 때마다 날짜, 결정, 이유, 검토한 대안을 짧게 추가한다.

| 날짜 | 결정 | 이유 / 대안 |
| --- | --- | --- |
| 2026-11-13 | 라이브러리 이름을 `compose-swift-navigation3`로 한다. `rootProject.name`과 Maven artifactId에 쓴다. (아래 2026-11-15 결정으로 artifactId가 바뀜) | 저장소 이름과 같다. |
| 2026-11-13 | group, 소스 패키지, Android namespace를 모두 `zone.ien.navigation`으로 한다. | Maven 좌표는 `zone.ien.navigation:navigation`이다(2026-11-15에 artifactId 변경). |
| 2026-11-13 | 최소 지원 iOS 버전은 18.2다. | 예제 앱의 기존 Deployment Target과 같다. iOS 18 UIKit API(`UITab`, `UITabGroup`)를 쓸 수 있다. |
| 2026-11-13 | OS 버전 분기를 두지 않는다. iOS 18.2에서 쓸 수 있는 API만 사용한다. | 코드 경로가 하나라 버전별 동작 차이와 검증 부담이 없다. Liquid Glass는 시스템이 자동 적용하므로 분기 없이도 iOS 26 요구사항을 충족한다. 대신 탭 바 최소화, `bottomAccessory` 같은 iOS 26 전용 기능은 제공하지 않는다. |
| 2026-11-13 | iOS 구현은 UIKit(`UITabBarController` + 탭별 `UINavigationController`)으로 하고, `iosMain`의 Kotlin/Native로만 제어한다. 라이브러리에 Swift 코드를 두지 않는다. | klib 하나로 배포되고 사용자가 Swift를 추가할 필요가 없다. Kotlin 백스택을 주인으로 두는 구조와 바로 맞는다. 네이티브 동작과 Liquid Glass는 SwiftUI와 같다. 대안이었던 SwiftUI(`TabView` + `NavigationStack`)는 Swift 코드를 복사하거나 별도로 배포해야 하고, 그 이점인 iOS 26 전용 modifier는 버전 분기를 두지 않아 쓰지 않는다. 비용: 백스택 diff와 인터랙티브 pop 처리를 직접 구현해야 한다. 상세 내용은 6.1장. |
| 2026-11-13 | iOS에서 화면의 주 세로 스크롤은 proxy `UIScrollView`가 소유하고, Compose 리스트는 그 offset을 따라간다(6.5장). | 큰 타이틀이 손가락을 따라 접히는 동작은 실제 pan 제스처가 있어야 일어난다. 대안인 프로그램 offset 동기화와 드래그 상태 위장은 실험에서 실패했다. 비용: iOS에서는 주 리스트의 스크롤 물리가 Compose가 아니라 UIKit이 되고, 화면이 스크롤 상태를 라이브러리에 연결해야 한다. |
| 2026-11-13 | 스크롤 연동 공개 API는 `rememberNativeScroll(state)`가 돌려주는 `NativeScroll(userScrollEnabled, contentPadding)`다. 화면은 이 두 값을 자기 리스트에 넘긴다. | 화면 코드는 한 번만 작성하고, Android에서는 아무 일도 하지 않는다. 대안이었던 라이브러리 전용 `LazyColumn` 래퍼는 Compose 리스트 API 전체를 다시 노출해야 하고 Grid나 `verticalScroll`로 확장하기 어렵다. |
| 2026-11-13 | 라이브러리의 웹 타깃은 브라우저 전용이다. | Compose UI의 Skiko 런타임은 Node.js에서 불러올 수 없어 `wasmJsNodeTest`가 실패했다. |
| 2026-11-13 | iOS의 Navigation 3 상태는 UI 없는 호스트 composition(자체 Recomposer)이 소유한다. 엔트리는 화면별 `ComposeUIViewController`에서 그린다. | 화면마다 composition이 따로 있어도 decorator의 정리 시점이 소스상 보장된다. 대안이었던 숨긴 Compose 뷰는 크기가 0일 때 재구성이 계속되는지 보장할 수 없었다. 단일 composition 안에 UIKit 내비게이션을 interop으로 넣는 방식은 interop 제약 때문에 택하지 않았다. |
| 2026-11-13 | 네이티브 pop은 사라진 화면 수만큼 `onBack()`을 호출해 Kotlin에 반영한다. API 모양은 `NavDisplay`와 맞춘다. | 기존 Navigation 3 코드의 `onBack`과 back stack을 그대로 쓴다. 대안이었던 "백스택 직접 수정"은 앱의 내비게이터 로직을 우회한다. |
| 2026-11-13 | 같은 `contentKey`는 한 화면만 composition에 올린다. 가장 최근에 나타난 화면이 가져가고, 이전 화면이 내려놓은 뒤 그린다. | Navigation 3 의미(같은 key는 같은 상태)를 지키면서 `SaveableStateHolder`의 중복 key 예외를 막는다. |
| 2026-11-13 | 바 아이템은 화면 Composable 안의 `NativeBarItems(...)`로 선언한다. iOS는 `navigationItem`과 `toolbarItems`로 그리고, 다른 플랫폼에서는 앱이 `NativeBarState`로 받아 직접 그린다. | 버튼 상태와 동작은 화면의 ViewModel에 있으므로 화면 안에서 선언해야 한다. 대안이었던 엔트리 metadata는 고정값이라 화면 상태에 접근할 수 없다. 라이브러리가 Android UI를 그리지 않는 원칙(6.2장)을 지킨다. |
| 2026-11-13 | iOS UI 테스트 하네스를 `example/iosUITests`(별도 Xcode 프로젝트)로 저장소에 둔다. | 저장소 밖 임시 디렉터리에 두었던 하네스가 사라진 일이 있었다. 예제 앱 Xcode 프로젝트를 바꾸지 않고 설치된 앱을 bundle id로 구동한다. |
| 2026-11-13 | iOS 상태 복원은 라이브러리가 `NSData`를 주고받고, 저장소는 앱이 고른다(예제: `@SceneStorage`). 저장은 `.inactive`에서 한다. | 시스템 의미(백그라운드 종료 시 복원, 강제 종료 시 폐기, scene별 저장)를 그대로 얻는다. 대안이었던 라이브러리 파일 저장은 강제 종료를 구분할 수 없어 네이티브와 다르게 동작한다. 앱 쪽 Swift는 약 15줄이다. |
| 2026-11-13 | 호스트의 `canBeSaved`는 모든 값을 허용하고, 저장할 때 직렬화할 수 없는 registry 항목만 뺀다. | Android처럼 등록 시점에 막으면 enum 같은 값을 쓰는 공통 코드가 iOS에서 크래시한다. 빠진 항목만 초기값으로 돌아가는 편이 안전하다. |
| 2026-11-13 | 화면 안의 뒤로가기 핸들러가 켜져 있으면 iOS 뒤로 버튼(`backAction`)과 스와이프(CMP 제스처)를 핸들러로 보내고, `UINavigationController` 스와이프 pop을 끈다. (아래 결정으로 구현 방식이 바뀜) | Android의 `BackHandler` 의미와 같다. 두 스와이프 recognizer가 경쟁하지 않는다. 대안이었던 "핸들러 무시하고 항상 네이티브 pop"은 저장 확인 같은 흔한 패턴을 깨뜨린다. |
| 2026-11-13 | 세로 드래그 충돌은 요소 단위 제외(`Modifier.nativeScrollExclusion()`)로 푼다. | 제외 영역에서 시작한 pan만 Compose에 넘기면 나머지 화면은 네이티브 스크롤을 유지한다. 대안이었던 화면 단위로 네이티브 스크롤을 끄는 방식은 큰 타이틀 연동을 잃는다. |
| 2026-11-13 | iOS 26 미만에서도 네이티브 컨트롤러를 쓴다. Liquid Glass만 iOS 26 이상에서 시스템이 적용한다. | 지원하는 모든 iOS 버전에서 내비게이션 코드 경로가 하나다. 대안이었던 KotlinConf 방식(26 미만은 Compose 내비게이션으로 대체)은 iOS에서 두 경로를 유지해야 해서 택하지 않았다. |
| 2026-11-13 | 복원 데이터로 시작한 호스트의 첫 composition이 실패하면, 데이터를 버리고 한 번 새로 시작한다. | 이전 버전의 저장 구조가 맞지 않아 나는 예외는 iOS가 같은 scene 데이터를 계속 주기 때문에 실행마다 반복된다(실측). 대안이었던 "그대로 크래시"는 사용자가 앱을 강제 종료하거나 재설치하기 전까지 앱을 쓸 수 없게 만든다. `stateVersion`이 정확한 수단이고, 이것은 안전장치다. 복원 데이터 없이 실패하면 앱 오류로 보고 그대로 던진다. |
| 2026-11-13 | 루트 선언(`NativeStack`/`NativeTabs`)을 실행 중에 바꿀 수 있게 한다. 바뀌면 이전 계층을 내리고 교차 페이드로 새 계층을 보인다. 탭 바는 계속 루트에만 둔다. | 로그인 → 메인 탭 같은 흐름을 Navigation 3 코드에서 `if`로 그대로 쓸 수 있다. 의미는 `NavDisplay`가 composition을 떠날 때와 같다. 검토한 대안: 중첩 탭(push한 화면 안의 `UITabBarController`)은 iOS 관례가 아니다. 앱이 Swift에서 `NativeNavigationViewController`를 두 개 갈아 끼우는 방식은 호스트 composition이 나뉘어 상태 공유와 복원이 어렵다. 모달 API는 별도 작업으로 남긴다. |
| 2026-11-14 | 화면 콘텐츠의 뒤로가기 핸들러를 화면 전용 dispatcher에 모은다. 뒤로 버튼은 dispatcher의 fallback으로, 스와이프는 시작 시점의 판별로 처리한다. 루트 화면에는 `backAction`을 두지 않는다(6.9장). | navigationevent는 back과 forward를 합친 활성 여부만 알려 준다. 그래서 이전 방식(활성 핸들러가 있으면 가로챔)은 forward만 켜진 핸들러(`NavigationEventHandler` 기본값)가 있는 화면의 뒤로가기를 막았다(Hotpot에서 발견). 검토한 대안: 앱에 `isForwardEnabled = false`를 요구하는 방식은 실수하기 쉽고 기본값과 어긋난다. 비용: CMP의 가장자리 스와이프 대신 라이브러리의 recognizer를 쓴다. |
| 2026-11-14 | 스택에서 제거된 화면은 내용이 composition을 떠날 때까지 content key를 쥔다. 앱이 애니메이션으로 pop한 화면의 내용은 전환이 끝난 뒤 내린다(6.6장). | window 밖의 composition은 다시 그려지지 않아, 제거된 화면의 내용은 CMP가 scene을 dispose할 때까지 `SaveableStateHolder`에 등록된 채로 남는다. 제거 즉시 key를 놓으면 그사이 같은 라우트를 다시 연 화면이 같은 key를 써서 예외가 났다(Hotpot). 비용: 닫은 직후 같은 라우트를 다시 열면 새 화면의 내용이 최대 약 0.5초 늦게 그려진다. 검토한 대안: CMP에는 scene을 즉시 dispose하는 공개 API가 없다. |
| 2026-11-14 | 화면 상태와 무관한 내비게이션 바 제목은 엔트리 metadata(`nativeScreen(title)`)로 선언하도록 문서화한다. `NativeBarItems`의 제목은 바뀌는 제목에 쓴다. 제목은 바뀔 때만 UIKit에 쓴다(6.7장). | 화면 내용은 push가 시작된 뒤에 composition되므로, `NativeBarItems` 제목은 전환에 들어가지 못하고 끝난 뒤에 나타났다(녹화로 확인). 검토한 대안: 새 화면을 먼저 composition한 뒤 push하는 방식은 CMP가 window 밖에서는 composition하지 않아 화면을 임시로 window에 붙여야 하고, 탭에 대한 push 반응도 늦어진다. |
| 2026-11-14 | proxy의 `contentSize`는 Compose가 끝까지 남은 거리를 알 때 그 값으로 정확히 정한다. `ScrollState`는 항상, 리스트와 그리드는 마지막 아이템이 배치되면 안다. 끝을 넘는 이동은 Compose에 보내지 않는다(6.5장). | `canScrollForward`만 보고 끝에서야 `contentSize`를 줄이면, 바운스 중에 Compose가 끝에서 조금만 떨어져도 크기가 커졌다 줄기를 반복해 UIKit이 offset을 잘라 냈다(Hotpot "바닥에서 끊김", 프레임 추적으로 확인). 남은 거리로 정하면 크기가 Compose 위치와 무관해서 왕복이 없다. 검토한 대안: 끝 근처에서 `canScrollForward` 변화를 무시하는 방식은 끝을 지난 뒤에야 크기를 줄이는 문제(감속 중 잘림)가 그대로 남는다. |
| 2026-11-14 | 사용자 스크롤을 끌 수 없는 컨테이너는 proxy 스크롤과 함께 쓸 수 없다고 문서화한다(6.5장). 상위 nested scroll로 막는 API는 넣지 않는다. | 실험에서 Compose 드래그 감지가 터치를 가져가 proxy pan이 시작되지 않았고, 리스트가 아예 움직이지 않았다. 동작하지 않는 API는 내보내지 않는다. 앱은 스크롤을 끌 수 있는 컨테이너로 직접 그린다(Hotpot의 라이선스 목록과 설정 화면). |
| 2026-11-14 | `NativeStack`, `NativeTabs`, `NativeTab`을 commonMain으로 옮긴다. iOS에서는 네이티브 컨트롤러를 선언하고, 다른 플랫폼에서는 앱이 넘긴 마지막 블록(`otherPlatforms`)을 `NativeNavigationScope`(decorated 엔트리, 제목, 바 아이템, 탭)와 함께 실행한다. 앱의 루트 `App()`을 모든 플랫폼이 함께 쓴다(6.6장). | 이전에는 iOS 진입점과 Android `App()`이 백스택 사용, decorator, 탭 구성을 따로 가져 흐름이 갈라졌다. 블록으로 Android의 Material 구성만 남기면, 기존 앱은 `NavDisplay`를 감싸고 iOS 진입점 한 줄만 바꾸면 된다. 라이브러리는 여전히 Android UI를 그리지 않고 `navigation3-ui`·`material3`에 의존하지 않는다(6.2장). 검토한 대안: 라이브러리가 `material3`와 `navigation3-ui`에 의존해 Android 셸까지 그리는 방식은 Material 설정을 모두 API로 다시 노출해야 하고 6.2장과 맞지 않는다. Material 슬롯(`TopAppBar` 등)의 내용을 읽어 iOS 네이티브 바로 옮기는 방식은 Composable 슬롯의 내용을 읽을 수 없어 불가능하다. iOS에서 호스트가 없을 때 블록으로 대신 그리는 방식은 6.4장(대체 경로 없음)에 어긋나므로 예외로 막는다. |
| 2026-11-15 | 앱의 iOS 전용 코드를 진입점 한 줄로 줄인다. 호스트 composition이 `LocalUIViewController`와 `LocalDensity`를 제공하고, 공개 `LocalNativeScreen`과 `NativeNavigationFrame(providers, frame)`을 둔다(6.6장). | Hotpot에서 앱이 iOS용으로 따로 쓴 코드(호스트 locals, 화면 데코레이터와 앱 틀 expect/actual, 자체 `LocalNativeChrome`, 화면마다 다시 넣는 DI)는 모두 "화면마다 composition이 따로 있다"는 같은 이유였다. 앱이 지금 `NavDisplay`를 감싸는 코드를 그대로 `providers`와 `frame`으로 나눠 넘기면 Android는 지금과 같고 iOS는 화면마다 씌운다. 검토한 대안: 호스트의 CompositionLocal을 화면으로 자동 전달하는 방식은 앱 코드를 더 줄이지만, 화면이 직접 갖는 owner(lifecycle, ViewModel, saved state, density)와 겹치는 key를 골라내야 하고 호스트에서 계산한 값(예: 다크 모드)이 화면과 어긋난다. 그래서 앱이 넘길 범위를 `providers`로 명시하게 했다. |
| 2026-11-15 | (시험용) 제목 이미지는 `nativeScreen(titleImage = { Painter })`로 받는다. iOS는 호스트 composition에서 painter를 `UIImage`로 그려 `titleView`에 넣는다(6.7장). | 공통 코드에 로고를 한 번만 선언하고, Android는 같은 painter를 그린다. 검토한 대안: iOS 에셋 카탈로그 이름(`UIImage(named:)`)은 다크 모드 변형과 벡터를 iOS가 그대로 처리해 더 네이티브하지만, 로고를 Xcode에 한 번 더 넣어야 하고 Android는 이름을 보고 앱이 그림을 골라야 한다. 사용자가 Compose 리소스 방식으로 시험하기로 했다. 화면 composition에서 그리는 방식은 이미지가 push 전환 뒤에 나타난다. |
| 2026-11-15 | `NativeTabs`에 탭 위의 공유 화면(`sharedBackStack`, `onSharedBack`)을 둔다. iOS는 선택된 탭의 `UINavigationController`에 이어서 push하고, 공유 화면은 항상 탭 바를 숨긴다. 앱이 탭을 바꾸면 공유 화면이 새 탭으로 옮겨 간다(6.6장). | 탭 화면 전체를 바깥 `NavDisplay`로 덮는 앱(Hotpot)은 상세 라우트를 탭마다 따로 쌓도록 내비게이션을 다시 짜야 했다(약 1,000줄). 공유 백스택이면 원래 백스택과 이동 코드를 그대로 쓴다. 탭 바를 항상 숨기면 "공유 화면이 보이는 중 사용자의 탭 전환"이 생기지 않아 의미가 단순하다. 검토한 대안: 탭 바를 보이게 두고 탭 전환 때 공유 화면을 비우거나 남기는 방식은 사용자가 보는 결과를 예측하기 어렵다. 중첩 선언(공유 스택의 루트 엔트리 안에 `NativeTabs`)은 iOS에서 화면 composition 안에 탭 컨트롤러를 두어야 해 6.6장 구조와 맞지 않는다. iOS 관례(탭마다 독립 스택)와 다르지만, 탭 바를 가리는 화면에서는 사용자 눈에 차이가 없다. |
| 2026-11-15 | Maven artifactId를 `navigation`으로 줄인다. 좌표는 `zone.ien.navigation:navigation`이다. POM `name`과 iOS 로그 접두어(`zone.ien.navigation:`)도 맞춘다. `rootProject.name`, GitHub 저장소 이름과 URL, README 제목은 `compose-swift-navigation3`로 둔다. | 사용자 요청: 의존성 선언에 쓰기에 이름이 너무 길다. group이 이미 라이브러리를 구분하므로 artifactId는 짧아도 된다. 저장소 이름은 바꾸지 않았으므로 저장소를 가리키는 값은 그대로 둔다. mavenLocal로 받는 앱(Hotpot)은 버전 카탈로그의 `name`을 바꿔야 한다. |

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

## 9. Git과 배포

- 기본 브랜치는 `main`이고 작업 브랜치는 `dev`다. CI(`gradle.yml`)는 `main` 대상 push와 PR에서만 실행된다.
- 커밋, 푸시, PR 생성은 사용자가 요청할 때만 한다.
- 파일을 만들거나 고친 뒤에는 `git add .`로 스테이징한다(커밋은 하지 않는다).
- `local.properties`, `xcuserdata`, `.kotlin`, `build`는 gitignore 대상이다. 커밋하지 않는다.
- 첫 프리릴리스 버전은 `1.0.0-alpha01`이며 `gradle/libs.versions.toml`에서 관리한다.
- `publish.yml`은 GitHub Release·프리릴리스 공개(`published`) 시 같은 커밋의 CI 전체를 실행하고, 통과하면 `:library`를 서명해 Maven Central에 업로드·자동 공개한다. 태그는 `v<lib-version-name>`과 일치해야 한다.
- 배포 Secret 이름은 compose-hig와 같은 `SONATYPE_USERNAME`, `SONATYPE_PASSWORD`, `GPG_KEY`, `GPG_PASSWORD`다. 실제 Secret 값은 변경하지 않는다. 로컬 Maven 배포는 서명 없이 실행한다.

## 10. 참고 자료

- JetBrains 공식 가이드, iOS Liquid Glass와 네이티브 내비게이션: https://kotlinlang.org/docs/multiplatform/ios-liquid-glass.html
- KotlinConf 앱 공식 구현(`lg-nav` 브랜치): https://github.com/JetBrains/kotlinconf-app/tree/lg-nav
- KotlinConf 앱 `main...lg-nav` 비교: https://github.com/JetBrains/kotlinconf-app/compare/main...lg-nav

공식 예제는 참고만 한다. 이 프로젝트는 앱이 아니라 라이브러리이므로 공개 API와 아키텍처를 그에 맞게 설계한다.

`lg-nav`는 Kotlin 2.3.20, Compose Multiplatform 1.11.0-beta03, `navigation3-ui` 1.1.0-beta01, Lifecycle 2.11.0-alpha02를 사용한다. 이 프로젝트의 버전(4장)보다 낮으므로 API를 옮길 때 현재 버전에서 시그니처를 다시 확인한다.
