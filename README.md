# compose-swift-navigation3

**Navigation 3**로 만든 Compose Multiplatform 앱을 위한 Kotlin Multiplatform 라이브러리입니다.
iOS에서는 백스택을 **실제 UIKit 내비게이션 컨트롤러**로 보여줍니다.
사용하는 컴포넌트는 `UITabBarController`, `UINavigationController`, `UINavigationBar`, `UIToolbar`, `UISearchController`입니다.
Android에서는 지금처럼 `NavDisplay`와 Material 3를 그대로 씁니다.

라우트, 백스택, `entryProvider`, 화면, ViewModel, 그리고 루트 `App()`까지 모두 공통 코드에 둡니다. 플랫폼마다 다른 것은 진입점 한 줄과 Android용 Material 화면 구성뿐입니다.

iOS 사용자는 Compose로 흉내 낸 UI가 아니라 시스템 컴포넌트를 그대로 씁니다.

- 탭마다 별도의 내비게이션 스택이 있는 탭 바. 탭을 바꿔도 각 탭의 스택과 화면 상태가 유지됩니다.
- 어느 탭에서든 여는 화면(프로필, 편집 화면 등)은 선택된 탭 위에 push됩니다.
- 네이티브 push/pop 애니메이션, 뒤로 버튼, 취소할 수 있는 가장자리 스와이프 뒤로가기, 뒤로 버튼 길게 누르기 메뉴
- 내비게이션 바 타이틀, 스크롤에 따라 접히는 큰 타이틀, 스크롤 상태에 따른 바 배경 전환
- 바 버튼, 메뉴, 하단 툴바, 검색 필드
- 같은 코드로 iOS 26 이상에서는 Liquid Glass, iOS 18.2~25에서는 기존 디자인

도입할 때 앱에서 할 일은 [도입 체크리스트](#도입-체크리스트)에 모았습니다. 이미 Navigation 3를 쓰고 있다면 단계별 안내는 [기존 Navigation 3 앱에서 옮기기](#기존-navigation-3-앱에서-옮기기)를 보세요.

> **상태:** 초기 개발 단계이며 아직 배포 전입니다(버전 `0.0.0`).
> 검증 환경: iOS 27.2·18.5 시뮬레이터(XCUITest, 실제 터치)와 Android 에뮬레이터

## 요구 사항

| | |
| --- | --- |
| Kotlin | 2.4.21 |
| Compose Multiplatform | 1.12.2 |
| Navigation 3 | `androidx.navigation3:navigation3-runtime` 1.2.0. Android/Desktop UI는 `org.jetbrains.androidx.navigation3:navigation3-ui` 1.2.0 |
| iOS | 18.2 이상. Liquid Glass를 쓰려면 iOS 26 SDK 이상(Xcode 26 이상)으로 빌드 |
| Android | minSdk 29 |
| 타깃 | android, jvm, iosArm64, iosSimulatorArm64, js, wasmJs(브라우저) |

## 설치

```kotlin
// shared 모듈, commonMain
dependencies {
    implementation("zone.ien.navigation:compose-swift-navigation3:<version>")
}
```

Maven Central에 배포되기 전까지는 두 가지 방법 중 하나를 씁니다.
- `./gradlew :library:publishToMavenLocal`로 로컬에 배포하고 `mavenLocal()` 저장소를 추가합니다.
- `library` 모듈을 빌드에 직접 포함합니다.

앱에는 다른 Navigation 3 앱과 같은 의존성도 필요합니다.
- `@Serializable` 라우트용 kotlinx-serialization 플러그인
- Android/Desktop의 `NavDisplay`용 `navigation3-ui`
- 엔트리별 ViewModel이 필요하면 `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-navigation3`

## 전체 구조

```
commonMain   라우트(NavKey) · 백스택 · entryProvider · 화면 · ViewModel · App()
   │
   └── App(): NativeTabs(...) 또는 NativeStack(...) { 다른 플랫폼 UI }
         │
         ├── Android / Desktop / Web: 블록 안의 앱 UI(Material 3 Scaffold + NavDisplay)를 그대로 그림
         │
         └── iOS: NativeNavigationViewController { App() }
                    └── UITabBarController → 탭마다 UINavigationController
                          └── Navigation 3 엔트리 하나당 네이티브 화면 하나 (안에 Compose 콘텐츠)
```

`App()`은 모든 플랫폼이 함께 씁니다. 플랫폼마다 다른 것은 두 가지뿐입니다.
- 진입점 한 줄: Android는 `setContent { App() }`, iOS는 `NativeNavigationViewController { App() }`
- `NativeStack`/`NativeTabs`의 마지막 블록(`otherPlatforms`): iOS가 아닌 플랫폼에서만 실행되는 앱의 Material 화면 구성

기준은 Kotlin 백스택입니다.
- 백스택에 키를 추가, 제거, 교체하면 네이티브 스택이 push, pop, 전체 설정으로 따라갑니다.
- UIKit이 스스로 pop하면 사라진 화면 수만큼 앱의 `onBack`을 호출합니다. 뒤로 버튼, 가장자리 스와이프, 뒤로 버튼 메뉴, 탭 다시 누르기가 여기에 해당합니다.

## 도입 체크리스트

이 라이브러리를 도입할 때 앱에서 할 일을 모았습니다. 자세한 설명은 각 항목의 링크에 있습니다.
iOS 전용으로 쓸 코드는 진입점 한 줄뿐입니다. 상태 복원을 쓰면 Swift 코드가 약 15줄 더 필요합니다.

### 반드시 할 일

1. **의존성**: commonMain에 `compose-swift-navigation3`를 추가합니다. Android/Desktop의 `NavDisplay`를 위해 `navigation3-ui`는 그대로 둡니다. ([설치](#설치))
2. **`NavDisplay`를 선언으로 감싸기**: `NavDisplay`를 `NativeStack`(탭이 있으면 `NativeTabs`)으로 감쌉니다.
   - 지금의 Material `Scaffold`와 `NavDisplay`는 마지막 블록으로 옮깁니다. 이 블록은 iOS가 아닌 플랫폼에서만 실행됩니다.
   - 블록 안에서는 블록이 주는 값을 씁니다: `NavDisplay(entries, onBack = onBack)`, 탑바의 `title`·`canGoBack`·`trailing`. ([3단계](#3단계-navdisplay를-nativestack으로-감쌉니다), [4. Android와 Desktop](#4-android와-desktop))
3. **앱 틀 옮기기**: `App()`을 감싸던 것을 `NativeNavigationFrame`에 넘깁니다. 테마와 루트 Scaffold는 `frame`, DI `CompositionLocalProvider`는 `providers`입니다. ([테마와 앱 전역 CompositionLocal](#테마와-앱-전역-compositionlocal))
4. **iOS 진입점**: `ComposeUIViewController { App() }`를 `NativeNavigationViewController { App() }`로 바꿉니다. Swift의 `UIViewControllerRepresentable`은 그대로 두고 `.ignoresSafeArea()`를 씁니다. ([4단계](#4단계-ios-진입점을-한-줄로-바꿉니다))
5. **화면 제목**: 엔트리마다 `metadata = nativeScreen(title = ...)`을 더합니다.
   - 큰 제목(`largeTitle = true`)은 탭 루트처럼 스택의 첫 화면에만 씁니다. push되는 화면은 인라인 제목(기본값)으로 둡니다. ([1단계](#1단계-엔트리에-화면-정보를-더합니다), [동작 규칙과 한계](#동작-규칙과-한계))
6. **화면의 탑바 정리**: 화면 안의 `TopAppBar`를 지웁니다. 지우기 어려우면 `if (!LocalNativeScreen.current)`로 감싸 iOS에서만 숨깁니다.
   - 뒤로 버튼은 지웁니다. iOS는 네이티브 뒤로 버튼, Android는 블록의 Scaffold가 그립니다.
   - 액션과 메뉴는 `NativeBarItems(trailing = ...)`, 하단 바는 `NativeBarItems(toolbar = ...)`, 검색 필드는 `NativeSearchBar`로 선언합니다. ([3. 화면 작성](#3-화면-작성-공통))
7. **여백과 스크롤**: `Scaffold`의 `innerPadding` 대신 아래 값을 씁니다.
   - 스크롤하는 화면: `rememberNativeScroll(state)`의 `contentPadding`과 `userScrollEnabled`를 주 세로 스크롤 컨테이너에 넘깁니다.
   - 스크롤하지 않는 화면: `nativeContentPadding()`
   - 바 높이를 `WindowInsets`로 또 더하지 않습니다. `imePadding()`은 Android에서만 붙입니다(`LocalNativeScreen.current`가 `false`일 때). ([스크롤하는 화면](#스크롤하는-화면))

### 앱 구조에 따라 할 일

| 이런 경우 | 할 일 |
| --- | --- |
| 하단 탭 | `NativeTabs`에 `NativeTab(title, systemImage, backStack)`을 넘깁니다. `systemImage`는 SF Symbol 이름입니다. Android 하단 탭은 블록의 `tabs`, `selectedTab`, `onTabSelected`, `tabBarVisible`로 그립니다 ([5. iOS](#5-ios)) |
| 어느 탭에서든 여는 화면, 또는 탭 화면 전체를 덮는 바깥 `NavDisplay` | 그 백스택을 `NativeTabs(sharedBackStack = ...)`로 넘깁니다 ([탭 위의 공유 화면](#탭-위의-공유-화면)) |
| 상세 화면에서 탭 바 숨기기 | `nativeScreen(hidesTabBar = true)`. 공유 화면은 따로 지정하지 않아도 숨깁니다 |
| 로그인 → 메인처럼 흐름이 바뀜 | `if (signedIn) NativeTabs(...) else NativeStack(...)`처럼 선언을 바꿉니다. 떠나는 쪽의 상태를 버리려면 decorator를 분기 안에서 만듭니다 ([5. iOS](#5-ios)) |
| 화면 상태에 따라 바뀌는 제목 | `NativeBarItems(title = ...)` |
| 제목 자리에 로고 | `nativeScreen(title = "Home", titleImage = { painterResource(Res.drawable.logo) })`. iOS는 높이 28pt의 `titleView`로 보입니다. `title`은 VoiceOver와 뒤로 버튼용으로 그대로 둡니다. Android는 블록의 `titleImage`로 탑바에 그립니다. iOS에서는 다크 모드용 리소스와 테마 색이 적용되지 않습니다 |
| 저장 확인처럼 뒤로가기를 가로채는 화면 | 기존 `NavigationBackHandler`를 그대로 둡니다 ([뒤로가기 핸들러](#뒤로가기-핸들러)) |
| 세로로 끄는 요소(세로 slider, 순서 바꾸기 핸들) | `Modifier.nativeScrollExclusion()` |
| iOS에서 앱 종료 후에도 화면 복원 | `NativeNavigationViewController(restoredState, stateVersion)`과 `saveNativeNavigationState`를 쓰고, Swift에서 `@SceneStorage`에 저장합니다 ([6. 상태 복원](#6-상태-복원-ios-선택)) |
| 딥링크 | 링크를 라우트로 바꿔 백스택을 설정합니다 ([딥링크](#딥링크)) |

### 지켜야 할 규칙

- **iOS에서 `App()`은 UI 없는 composition에서 실행됩니다.**
  - `NativeStack`/`NativeTabs` 바깥과 `providers`에는 UI를 두지 않습니다. 두면 예외가 납니다.
  - `App()` 수준에서 띄우던 대화상자는 화면 안으로 옮기거나, iOS에서 시스템 알림창(`UIAlertController`)이 되는 구현을 씁니다.
  - 테마처럼 화면 환경이 필요한 Composable은 `frame`이나 화면 안에서만 씁니다.
- **주 세로 스크롤 컨테이너의 사용자 스크롤은 꺼야 합니다**(`nativeScroll.userScrollEnabled`). 켜 두면 큰 제목이 다시 펼쳐지지 않습니다. 이 값을 받지 않는 컨테이너는 스크롤을 끌 수 있는 컨테이너로 바꿉니다.
- **화면 상태와 무관한 제목은 `nativeScreen`에 둡니다.** `NativeBarItems`의 제목은 화면 내용이 그려진 뒤에 등록되므로 push 전환이 끝난 뒤에 나타납니다.
- **다이얼로그와 바텀시트는 엔트리로 두지 않고 화면 안에서 띄웁니다.** iOS는 모든 엔트리를 push되는 화면으로 만들고, `NavDisplay`의 `sceneStrategy`와 전환 설정을 쓰지 않습니다. ([iOS에서 쓰지 않는 것](#ios에서-쓰지-않는-것))
- **`onBack`과 `onSharedBack`은 엔트리 하나를 pop해야 합니다.** pop하지 않으면 네이티브 화면만 사라졌다가 다음에 백스택이 바뀔 때 다시 push됩니다.
- **엔트리 안에서 `App()`의 일반 값을 캡처하지 않습니다.** 엔트리는 백스택이 바뀔 때만 다시 만들어집니다(Navigation 3의 `rememberDecoratedNavEntries`). 그래서 매개변수 같은 일반 값은 갱신되지 않을 수 있습니다. `by`로 읽는 State, `rememberUpdatedState`, ViewModel로 읽습니다.
- **상태 홀더는 `rememberSaveable`의 `MutableState`를 직접 듭니다.** `SideEffect`로 저장 값에 복사하면, 호출한 곳이 다시 그려지지 않을 때 저장되지 않습니다.
- **`App()` 안의 `rememberNavBackStack`·`rememberSaveable` 호출을 더하거나 순서를 바꾸면 `stateVersion`을 올립니다.** 상태 복원을 쓸 때만 해당합니다.
- **테마는 시스템 라이트·다크 모드를 따르게 합니다.** 시스템 바는 Compose 테마가 아니라 iOS 모드를 따릅니다.

### 앱이 하지 않아도 되는 것

라이브러리가 처리합니다.
- 뒤로 버튼, 가장자리 스와이프(취소 포함), 뒤로 버튼 길게 누르기 메뉴, 탭 다시 누르기를 백스택에 반영합니다(`onBack`).
- 스크롤에 따라 접히는 큰 제목, 바 배경 전환, 상태 바 탭으로 맨 위 이동, 키보드 여백을 처리합니다.
- iOS 26 이상에서 Liquid Glass가 적용됩니다. OS 버전별 코드가 필요 없습니다.
- 엔트리별 ViewModel과 `rememberSaveable` 상태를 Navigation 3 의미대로 유지하고 정리합니다.
- UI 없는 composition에 `LocalUIViewController`와 `LocalDensity`를 넣어 둡니다. 그래서 공통 코드의 알림창과 `stringResource`가 그대로 동작합니다.

### 마지막 확인

- Android: 탑바가 앱의 Scaffold로 옮겨진 것 말고는 전과 같아야 합니다.
- iOS: push, 뒤로 버튼, 가장자리 스와이프와 그 취소, 스크롤에 따라 접히는 큰 제목, 탭 전환 뒤 상태 유지, 공유 화면, 상태 복원을 확인합니다. ([옮긴 뒤 확인할 것](#옮긴-뒤-확인할-것))
- iOS 26 이상과 18.x(18.2 이상) 모두에서 확인합니다.

## 기존 Navigation 3 앱에서 옮기기

Compose Multiplatform과 Navigation 3(`NavDisplay`)로 이미 만든 앱을 기준으로 설명합니다.
라우트, 백스택 조작, `entryProvider`, ViewModel, 뒤로가기 핸들러, 루트 `App()`은 그대로 둡니다.
바뀌는 것은 세 가지입니다.
- 탑바에 들어갈 내용을 화면이 그리지 않고 선언합니다.
- `NavDisplay`를 `NativeStack`(탭이 있으면 `NativeTabs`)으로 감쌉니다.
- iOS 진입점이 `NativeNavigationViewController { App() }`가 됩니다.

### 한눈에 보기

| 지금 | 바꾼 뒤 |
| --- | --- |
| `NavKey` 라우트, `rememberNavBackStack`, `add`·`removeLastOrNull`로 이동 | 그대로 |
| `entryProvider { entry<K> { ... } }` | 그대로. 엔트리마다 `metadata = nativeScreen(title = ...)`을 더합니다 |
| 화면 안 `TopAppBar`의 타이틀 | `nativeScreen(title)`. 화면 상태에 따라 바뀌면 `NativeBarItems(title = ...)` |
| 화면 안 `TopAppBar`의 뒤로 버튼(`navigationIcon`) | 지웁니다. iOS는 네이티브 뒤로 버튼, Android는 앱의 Scaffold가 그립니다 |
| 화면 안 `TopAppBar`의 액션과 메뉴 | `NativeBarItems(trailing = ...)` |
| 화면 안 `BottomAppBar` | `NativeBarItems(toolbar = ...)` |
| 탑바의 검색 필드 | `NativeSearchBar(query, onQueryChange)` |
| `Scaffold`의 `innerPadding` | 스크롤 화면은 `rememberNativeScroll(state).contentPadding`, 그 밖에는 `nativeContentPadding()` |
| `BackHandler`, `NavigationBackHandler` | 그대로 |
| `NavDisplay(backStack, onBack, entryDecorators, entryProvider)` | `NativeStack(backStack, onBack, entryDecorators, entryProvider) { Scaffold + NavDisplay(entries, onBack = onBack) }` |
| 백스택 여러 개 + Material `NavigationBar`(탭) | `NativeTabs(tabs, selectedTab, onTabSelected, ...) { ... }`. Android의 `NavigationBar`는 블록 안에서 `tabs`로 그립니다 |
| 탭 화면을 바깥 `NavDisplay`가 감싸고, 상세 화면이 탭 바까지 덮는 구조 | 바깥 백스택을 `NativeTabs(sharedBackStack = ...)`로 넘깁니다. 탭 자리였던 첫 엔트리는 뺍니다([탭 위의 공유 화면](#탭-위의-공유-화면)) |
| `App()`을 감싼 테마, DI `CompositionLocalProvider`, 루트 Scaffold | 같은 내용을 `NativeNavigationFrame(providers = ..., frame = ...)`에 넘깁니다 |
| 화면마다 직접 그리는 Material `TopAppBar` | 지우거나, 지우기 어려우면 `if (!LocalNativeScreen.current)`로 감싸 iOS에서만 숨깁니다 |
| iOS `ComposeUIViewController { App() }` | `NativeNavigationViewController { App() }` |
| Swift의 `UIViewControllerRepresentable` | 그대로. `.ignoresSafeArea()`로 전체 화면을 쓰게 합니다 |

### 1단계: 엔트리에 화면 정보를 더합니다

```kotlin
fun appEntryProvider(backStack: NavBackStack<NavKey>) = entryProvider<NavKey> {
    entry<Home>(metadata = nativeScreen(title = "Home", largeTitle = true)) {
        HomeScreen(onOpen = { backStack.add(Detail(it)) })
    }
    entry<Detail>(metadata = { key -> nativeScreen(title = "Detail ${key.id}") }) { key ->
        DetailScreen(key.id)
    }
}
```

- 타이틀은 push 애니메이션이 시작되기 전에 정해져야 전환과 함께 보입니다. 그래서 화면 상태와 무관한 타이틀은 여기서 선언합니다.
- 백스택은 `NavDisplay`를 쓸 때처럼 snapshot state 리스트여야 합니다(`rememberNavBackStack`, `mutableStateListOf`). 그래야 바뀐 것이 iOS에 반영됩니다.
- 탭이 여러 개이거나 이동 로직이 많으면 백스택들을 상태 홀더 하나에 모읍니다(예제의 `AppNavigation`과 `rememberAppNavigation()`).

### 2단계: 화면에서 탑바를 빼고 선언으로 바꿉니다

```kotlin
// 전
@Composable
fun DetailScreen(id: Int, onBack: () -> Unit, viewModel: DetailViewModel = viewModel()) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detail $id") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { IconButton(onClick = viewModel::share) { Icon(Icons.Default.Share, "Share") } },
            )
        },
    ) { innerPadding ->
        LazyColumn(contentPadding = innerPadding) { /* ... */ }
    }
}
```

```kotlin
// 후: 타이틀은 1단계의 nativeScreen(...)에 있습니다
@Composable
fun DetailScreen(id: Int, viewModel: DetailViewModel = viewModel()) {
    NativeBarItems(
        trailing = listOf(NativeBarButton("Share", systemImage = "square.and.arrow.up", onClick = viewModel::share)),
    )
    val listState = rememberLazyListState()
    val nativeScroll = rememberNativeScroll(listState)
    LazyColumn(
        state = listState,
        userScrollEnabled = nativeScroll.userScrollEnabled,
        contentPadding = nativeScroll.contentPadding,
    ) { /* ... */ }
}
```

- 뒤로 버튼은 화면에서 지웁니다. iOS에서는 `UINavigationController`가 만들고, Android에서는 3단계의 Scaffold가 그립니다.
- 화면마다 `TopAppBar`를 그리는 구조를 그대로 두고 싶다면, 그 탑바를 `if (!LocalNativeScreen.current) { ... }`로 감쌉니다. iOS 네이티브 화면에서는 시스템 바가 그 자리를 대신하므로 숨기고, Android에서는 지금처럼 그립니다. 제목과 버튼은 iOS용으로 `nativeScreen`·`NativeBarItems`에도 선언합니다.
- 스크롤하지 않는 화면은 `Modifier.padding(nativeContentPadding())`을 씁니다.
- iOS 내비게이션 바와 탭 바의 높이는 이 여백에 이미 들어 있습니다. 화면에서 `WindowInsets`로 같은 여백을 또 주지 않습니다.
- `LazyColumn`이 아닌 경우(그리드, `verticalScroll`), 바 버튼, 검색은 [3. 화면 작성](#3-화면-작성-공통)을 봅니다.

### 3단계: `NavDisplay`를 `NativeStack`으로 감쌉니다

`NavDisplay`와 Material Scaffold를 `NativeStack`의 마지막 블록(`otherPlatforms`)으로 옮깁니다.
이 블록은 iOS가 아닌 플랫폼에서만 실행되고, 화면이 선언한 내용과 백스택 상태를 바로 읽을 수 있습니다(`NativeNavigationScope`).

```kotlin
@Composable
fun App() {
    // 지금 App()을 감싸던 테마를 frame으로 옮깁니다(아래 "테마와 앱 전역 CompositionLocal").
    NativeNavigationFrame(frame = { content -> AppTheme(content) }) {
        val backStack = rememberNavBackStack(config, Home)
        NativeStack(
            backStack = backStack,
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = remember(backStack) { appEntryProvider(backStack) },
        ) {
            // iOS가 아닌 플랫폼에서만 실행됩니다. 지금 쓰는 Material 3 구성을 그대로 둡니다.
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(title.orEmpty()) },
                        navigationIcon = {
                            if (canGoBack) {
                                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                            }
                        },
                        actions = {
                            trailing.forEach { TextButton(onClick = it.onClick, enabled = it.enabled) { Text(it.title) } }
                        },
                    )
                },
            ) { innerPadding ->
                NavDisplay(entries = entries, modifier = Modifier.padding(innerPadding), onBack = onBack)
            }
        }
    }
}
```

- `NativeStack`은 `NavDisplay`처럼 `backStack`, `onBack`, `entryDecorators`, `entryProvider`를 받습니다. `onBack`의 기본값은 마지막 엔트리를 지우는 것입니다.
- 블록의 `entries`는 decorator가 이미 적용된 엔트리입니다. `NavDisplay(entries = entries, onBack = onBack)`에 그대로 넘기고, decorator와 `entryProvider`를 다시 넘기지 않습니다.
- `title`, `trailing` 같은 블록 안의 값은 [4. Android와 Desktop](#4-android와-desktop)에 정리했습니다. 탭이 있으면 `NativeTabs`를 씁니다([5. iOS](#5-ios)).
- `NativeNavigationFrame`의 `frame`과 블록 밖에는 UI를 두지 않습니다. iOS에서는 그 부분이 UI 없는 composition에서 실행되기 때문입니다.
- 탑바가 화면마다 있지 않고 앱에 하나만 남습니다. 그래서 화면이 전환될 때 탑바는 제자리에 있고 내용만 바뀝니다.

### 4단계: iOS 진입점을 한 줄로 바꿉니다

```kotlin
// 전: iosMain
fun MainViewController() = ComposeUIViewController { App() }
```

```kotlin
// 후: iosMain
fun MainViewController(): UIViewController = NativeNavigationViewController { App() }
```

- iOS에서 `NativeStack`과 `NativeTabs`는 네이티브 컨트롤러를 선언하고, 마지막 블록은 실행하지 않습니다. 그래서 `App()`은 iOS에서 아무것도 그리지 않습니다.
- iOS용으로 따로 쓸 코드는 이 한 줄뿐입니다. UI 없는 composition에도 `LocalUIViewController`(알림창을 띄울 루트)와 `LocalDensity`(문자열·이미지 리소스)가 들어 있어서, 공통 코드의 adaptive 알림창과 `stringResource`가 그대로 동작합니다.
- 함수 이름과 매개변수를 그대로 두면 Swift 코드는 바꿀 필요가 없습니다. SwiftUI에서 `.ignoresSafeArea(.keyboard)`만 쓰고 있었다면 `.ignoresSafeArea()`로 바꿉니다. 안전 영역은 내비게이션 바와 탭 바가 직접 처리합니다.

### 테마와 앱 전역 CompositionLocal

지금 `App()`을 감싸고 있는 것(테마, DI `CompositionLocalProvider`, 루트 Scaffold)은 `NativeNavigationFrame`에 그대로 넘깁니다.

```kotlin
// 전
@Composable
fun App() {
    CompositionLocalProvider(LocalAppGraph provides graph) {
        AppTheme {
            RootScaffold { NavDisplay(...) }
        }
    }
}

// 후
@Composable
fun App() {
    NativeNavigationFrame(
        providers = { content -> CompositionLocalProvider(LocalAppGraph provides graph, content = content) },
        frame = { content -> AppTheme { RootScaffold { content() } } },
    ) {
        NativeStack(...) { NavDisplay(entries, onBack = onBack) }
    }
}
```

- **Android·Desktop·Web**: `providers { frame { 블록 } }`으로 앱 전체를 한 번 감쌉니다. 지금과 같습니다.
- **iOS**: 엔트리마다 `UIViewController`와 composition이 따로 있습니다. 그래서 `frame`과 `providers`를 각 화면에 씌우고, `providers`는 UI 없는 composition(백스택, 상태 홀더, 알림창)에도 씌웁니다.
- `providers`에는 UI가 없는 것만 둡니다(DI, 디자인 토큰). `frame`에는 테마, 루트 Scaffold, 스낵바 호스트처럼 화면을 감싸는 UI를 둡니다.
- `frame` 안에서 `LocalNativeScreen.current`로 iOS 네이티브 화면인지 알 수 있습니다. 예: iOS 화면은 키보드를 네이티브 스크롤 여백으로 처리하므로 `imePadding()`을 Android에서만 붙입니다.
- `NativeNavigationFrame`을 쓰지 않고 엔트리 decorator(`NavEntryDecorator { entry -> AppTheme { entry.Content() } }`)로 화면마다 감싸도 됩니다.

내비게이션 바, 탭 바, 툴바는 시스템 바입니다. Compose 테마가 아니라 iOS의 라이트·다크 모드를 따릅니다.
- 앱 테마가 `isSystemInDarkTheme()`을 따르면 화면과 바의 모드가 일치합니다.
- 시스템이 라이트 모드인데 앱이 어두운 테마를 강제하면, 어두운 화면 위에 라이트 모드 바(검은 타이틀)가 올라갑니다.

### iOS에서 쓰지 않는 것

- 마지막 블록 전체. 그 안의 `NavDisplay`와 설정(`transitionSpec` 등 전환 애니메이션, `sceneStrategy`)도 쓰지 않습니다.
  - iOS는 UIKit 기본 전환을 씁니다.
  - 모든 엔트리는 push되는 전체 화면이 됩니다. 다이얼로그와 바텀시트는 엔트리로 두지 말고 화면 안에서 띄웁니다.
- Material `TopAppBar`와 `NavigationBar`. iOS에서는 시스템 바가 그 자리를 대신합니다.

### 옮긴 뒤 확인할 것

1. Android: 탑바가 앱의 Scaffold로 옮겨진 것 말고는 전과 같아야 합니다.
2. iOS: push, 뒤로 버튼, 가장자리 스와이프, 스크롤에 따라 접히는 큰 타이틀, 탭 전환을 확인합니다.
3. iOS에서 앱 종료 후에도 화면을 되살리려면 [6. 상태 복원](#6-상태-복원-ios-선택)을 추가합니다.
   - 복원을 쓰면 `NativeNavigationViewController(restoredState, stateVersion = 1)`처럼 버전을 넘깁니다.
   - 이후 `App()` 안의 `rememberNavBackStack`·`rememberSaveable` 호출을 더하거나 순서를 바꾸면 버전을 올립니다. 올리지 않으면 이전 저장 값이 엉뚱한 호출에 들어갑니다.

## 1. 라우트와 백스택 (공통)

Navigation 3를 평소처럼 씁니다.

```kotlin
@Serializable data object Home : NavKey
@Serializable data object Settings : NavKey
@Serializable data class Detail(val id: Int) : NavKey

// Android가 아닌 타깃에서 백스택을 저장하려면 NavKey 하위 타입을 등록해야 합니다.
val navigationConfiguration = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(Home::class)
            subclass(Settings::class)
            subclass(Detail::class)
        }
    }
}

// Composable 안에서:
val home = rememberNavBackStack(navigationConfiguration, Home)
```

화면 이동은 백스택을 바꾸는 것으로 합니다. 모든 플랫폼에서 똑같이 동작합니다.

```kotlin
backStack.add(Detail(3))                                 // push
backStack.removeLastOrNull()                             // pop
backStack[backStack.lastIndex] = Detail(103)             // replace
while (backStack.size > 1) backStack.removeLastOrNull()  // pop to root
```

## 2. 엔트리와 화면 메타데이터 (공통)

화면마다 내비게이션 바 정보를 엔트리 metadata의 `nativeScreen(...)`으로 선언합니다.

```kotlin
val entryProvider = entryProvider<NavKey> {
    entry<Home>(metadata = nativeScreen(title = "Home", largeTitle = true)) {
        HomeScreen(onOpen = { home.add(Detail(it)) })
    }
    entry<Detail>(metadata = { key -> nativeScreen(title = "Detail ${key.id}", hidesTabBar = true) }) { key ->
        DetailScreen(key.id)
    }
}
```

| 매개변수 | iOS에서의 동작 |
| --- | --- |
| `title` | `navigationItem.title` |
| `largeTitle` | 인라인 타이틀 대신 큰 타이틀(스크롤하면 접힘) |
| `hidesTabBar` | 이 화면이 push되어 있는 동안 탭 바를 숨김(`hidesBottomBarWhenPushed`) |

Android/Desktop에서는 같은 값이 `NativeStack`/`NativeTabs` 블록의 `title`, `largeTitle`, `tabBarVisible`로 들어옵니다([4. Android와 Desktop](#4-android와-desktop)). 엔트리에서 직접 읽을 때는 `entry.nativeTitle`, `entry.nativeLargeTitle`, `entry.nativeHidesTabBar`를 씁니다.

## 3. 화면 작성 (공통)

화면 안에 탑바(`TopAppBar`)를 직접 그리지 **않습니다**.
- iOS에서는 탑바가 네이티브입니다.
- Android에서는 `NativeStack`/`NativeTabs` 블록 안의 앱 Scaffold가 탑바를 그립니다.

그래서 화면은 탑바에 들어갈 내용만 선언합니다.

### 스크롤하는 화면

화면의 주 세로 스크롤 컨테이너를 연결합니다. 그러면 iOS 내비게이션 바가 스크롤을 따라 동작합니다.
- 큰 타이틀이 손가락을 따라 접힙니다.
- 바가 스크롤된 모양으로 바뀝니다.
- 상태 바를 탭하면 맨 위로 스크롤됩니다.
- 키보드가 올라와도 내용을 키보드 위로 스크롤할 수 있습니다.

```kotlin
val listState = rememberLazyListState()
val nativeScroll = rememberNativeScroll(listState, background = MaterialTheme.colorScheme.background)

LazyColumn(
    state = listState,
    userScrollEnabled = nativeScroll.userScrollEnabled,
    contentPadding = nativeScroll.contentPadding,
) { /* ... */ }
```

`Modifier.verticalScroll`을 쓰는 경우:

```kotlin
val scrollState = rememberScrollState()
val nativeScroll = rememberNativeScroll(scrollState)

Column(
    Modifier
        .verticalScroll(scrollState, enabled = nativeScroll.userScrollEnabled)
        .padding(nativeScroll.contentPadding),
) { /* ... */ }
```

그리드도 같습니다(`LazyVerticalStaggeredGrid`는 `rememberLazyStaggeredGridState()`):

```kotlin
val gridState = rememberLazyGridState()
val nativeScroll = rememberNativeScroll(gridState)

LazyVerticalGrid(
    columns = GridCells.Fixed(3),
    state = gridState,
    userScrollEnabled = nativeScroll.userScrollEnabled,
    contentPadding = nativeScroll.contentPadding,
) { /* ... */ }
```

- iOS에서는 스크롤 제스처와 물리 동작을 UIKit이 처리하고, Compose 리스트가 따라갑니다. 다른 플랫폼에서는 아무 일도 하지 않습니다(`userScrollEnabled`는 `true`, 여백은 0).
- 컨테이너의 사용자 스크롤은 반드시 `nativeScroll.userScrollEnabled`로 끕니다. 켜 두면 Compose가 드래그를 가져가서, 맨 위로 돌아와도 큰 타이틀이 다시 펼쳐지지 않습니다. 이 값을 받지 않는 컨테이너(스크롤을 내장한 서드파티 리스트 등)는 스크롤을 끌 수 있는 컨테이너로 직접 그립니다.
- `background`는 끝을 넘어 당길 때(고무줄 효과) 드러나는 영역의 색입니다.
- 화면당 컨테이너 하나만 연결합니다. `LazyListState`, `LazyGridState`, `LazyStaggeredGridState`, `ScrollState`를 지원합니다. 세로 방향만 연결합니다(`LazyRow`나 가로 그리드는 연결하지 않아도 그대로 동작합니다).
- 세로로 직접 끄는 요소(세로 slider, 순서 바꾸기 핸들 등)에는 `Modifier.nativeScrollExclusion()`을 붙입니다. 그 안에서 시작한 드래그는 Compose가 받습니다.

### 스크롤하지 않는 화면

```kotlin
Column(Modifier.fillMaxSize().padding(nativeContentPadding())) { /* ... */ }
```

### 바 버튼, 메뉴, 툴바

화면 안에서 선언하므로 화면 상태를 그대로 따라갑니다.

```kotlin
NativeBarItems(
    title = if (count > 0) "Detail $id ($count)" else null,   // 선택. nativeScreen의 타이틀을 덮어씁니다
    trailing = listOf(
        NativeBarButton("Add one", systemImage = "plus", onClick = viewModel::increment),
        NativeBarButton(
            title = "More",
            systemImage = "ellipsis.circle",
            menu = listOf(NativeMenuAction("Reset count", destructive = true) { viewModel.reset() }),
        ),
    ),
    toolbar = listOf(
        NativeBarButton("Decrease", systemImage = "minus.circle", enabled = count > 0, onClick = viewModel::decrement),
    ),
)
```

iOS에서의 동작은 다음과 같습니다.
- `leading`과 `trailing`은 `UIBarButtonItem`이 됩니다. 둘 다 왼쪽에서 오른쪽 순서로 적고, 뒤로 버튼은 그대로 남습니다.
- `menu`는 `UIMenu`가 됩니다.
- `toolbar`는 하단 툴바가 됩니다. 그 화면이 맨 위에 있을 때만 보입니다.
- `systemImage`는 SF Symbol 이름이고, `title`은 접근성 레이블로 쓰입니다.
- 네이티브 버튼은 모양이 바뀔 때만 새로 만들고, 다시 그릴(recomposition) 때마다 만들지는 않습니다.
- 여기서 선언한 항목은 화면 내용이 처음 그려진 뒤에 등록됩니다. 그때는 push 애니메이션이 이미 시작된 뒤라서, `title`은 전환이 끝난 뒤에 나타납니다(내용이 `Scaffold` 안에 있으면 더 늦습니다). 화면 상태와 무관한 타이틀은 `nativeScreen(title)`로 선언해야 전환과 함께 보입니다.

### 검색

```kotlin
var query by rememberSaveable { mutableStateOf("") }
NativeSearchBar(query = query, onQueryChange = { query = it }, placeholder = "Search")
// query로 화면 내용을 걸러냅니다
```

iOS에서는 그 화면의 `UISearchController`가 됩니다. 큰 타이틀 아래에 붙고, 아래로 스크롤하면 숨으며, 시스템 취소 버튼이 붙습니다.

### 뒤로가기 핸들러

Android에서처럼 `NavigationBackHandler`(또는 예전 `BackHandler`)를 씁니다.
- 핸들러가 켜져 있으면 iOS 뒤로 버튼과 가장자리 스와이프가 pop 대신 핸들러로 갑니다. 뒤로 버튼은 네이티브 모양 그대로입니다.
- 꺼져 있으면 다시 네이티브 pop이 됩니다.

```kotlin
NavigationBackHandler(
    state = rememberNavigationEventState(NavigationEventInfo.None),
    isBackEnabled = hasUnsavedChanges,
    onBackCompleted = { showDiscardDialog = true },
)
```

## 4. Android와 Desktop

라이브러리는 Android UI를 그리지 않습니다.
`NativeStack`과 `NativeTabs`는 마지막 블록(`otherPlatforms`)을 실행하고, 화면이 선언한 내용을 블록의 receiver(`NativeNavigationScope`)로 넘깁니다.
앱은 이 값으로 자기 Material 3 컴포넌트를 그립니다.

| 값 | 내용 | iOS에서 같은 역할 |
| --- | --- | --- |
| `entries` | 보이는 백스택의 decorated 엔트리. 탭이면 선택된 탭의 엔트리 뒤에 공유 화면이 붙습니다. `NavDisplay(entries, onBack = onBack)`에 넘깁니다 | 네이티브 스택 |
| `tabEntries`, `sharedEntries` | `entries`를 나눈 것: 선택된 탭 자신의 엔트리와 `sharedBackStack`의 엔트리. 탭 위에 바깥 `NavDisplay`를 따로 둘 때 씁니다 | 탭 스택, 그 위에 push된 공유 화면 |
| `onBack`, `canGoBack` | 보이는 백스택에서 하나 pop하기(공유 화면이 있으면 `onSharedBack`), 뒤로 갈 화면이 있는지 | 뒤로 버튼 |
| `title`, `largeTitle` | 맨 위 화면의 타이틀(`NativeBarItems`의 타이틀, 없으면 `nativeScreen`의 타이틀)과 큰 타이틀 여부 | 내비게이션 바 타이틀 |
| `leading`, `trailing`, `toolbar` | 맨 위 화면의 `NativeBarItems` | 바 버튼, 하단 툴바 |
| `search` | 맨 위 화면의 `NativeSearchBar` | `UISearchController` |
| `tabs`, `selectedTab`, `onTabSelected` | `NativeTabs`의 탭. `NativeStack`이면 빈 목록 | 탭 바 |
| `tabBarVisible` | 탭이 있고, 공유 화면이 없고, 선택된 탭에 push된 화면 중 `hidesTabBar`인 화면이 없으면 `true` | `hidesBottomBarWhenPushed` |

```kotlin
// 예제 App.kt의 MaterialChrome()을 줄인 것
@Composable
fun NativeNavigationScope<NavKey>.MaterialChrome() {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title.orEmpty()) },
                navigationIcon = { if (canGoBack) TextButton(onClick = onBack) { Text("Back") } },
                actions = { trailing.forEach { BarButton(it) } },
            )
        },
        bottomBar = {
            if (tabBarVisible) NavigationBar {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = index == selectedTab,
                        onClick = { onTabSelected(index) },
                        icon = { /* Android 아이콘 */ },
                        label = { Text(tab.title) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavDisplay(entries = entries, modifier = Modifier.padding(innerPadding), onBack = onBack)
    }
}
```

- 같은 함수를 `NativeTabs`와 `NativeStack`의 블록에서 함께 쓸 수 있습니다(`NativeStack`이면 탭 바가 없습니다).
- `NativeTabs`는 탭마다 엔트리를 따로 만듭니다(Navigation 3의 `rememberDecoratedNavEntries`). 그래서 Android에서도 탭을 바꿨다 돌아오면 각 탭의 `rememberSaveable` 상태와 ViewModel이 그대로 있습니다.
- 바 아이템과 검색은 블록 아래(`NavDisplay`)에서 가장 최근에 그려진 화면의 것입니다. 전환 중에 두 화면이 함께 그려져도 맨 위 화면이 이깁니다.
- `systemImage`는 iOS용 SF Symbol 이름입니다. Android 아이콘은 앱이 정합니다.
- 메뉴(`button.menu`), 툴바, 검색까지 그린 전체 예는 `example/shared/.../App.kt`에 있습니다.
- `NativeStack`/`NativeTabs` 없이 `NavDisplay`만 쓰는 화면에서 선언을 받고 싶다면 `NativeBarState`를 만들어 `ProvideNativeBarState(state) { NavDisplay(...) }`로 감쌉니다.

## 5. iOS

`iosMain`에서 루트 뷰 컨트롤러를 만들고, 그 안에서 공통 `App()`을 부릅니다.

```kotlin
fun MainViewController(restoredState: NSData?): UIViewController =
    NativeNavigationViewController(restoredState, stateVersion = 1) { App() }

fun saveNavigationState(controller: UIViewController): NSData? = saveNativeNavigationState(controller)
```

- 블록은 UI 없는 composition에서 실행됩니다. 여기서 `NativeStack`과 `NativeTabs`는 네이티브 컨트롤러를 선언하고, 마지막 블록은 실행하지 않습니다.
- 그래서 `App()`에서 `NativeStack`/`NativeTabs` 바깥에 UI(`Scaffold`, `Text` 등)를 두면 안 됩니다. 두면 예외가 납니다.
- iOS에서 `NativeNavigationViewController` 밖에서 `NativeStack`이나 `NativeTabs`를 부르면 예외가 납니다. iOS에서 Compose로 대신 그리는 경로는 없습니다.

탭이 있으면 공통 코드에서 `NativeTabs`로 선언합니다.

```kotlin
// commonMain
@Composable
fun App() {
    val home = rememberNavBackStack(navigationConfiguration, Home)
    val settings = rememberNavBackStack(navigationConfiguration, Settings)
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    NativeTabs(
        tabs = listOf(
            NativeTab("Home", systemImage = "house", backStack = home),
            NativeTab("Settings", systemImage = "gearshape", backStack = settings),
        ),
        selectedTab = selectedTab,
        onTabSelected = { selectedTab = it },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider,
    ) { MaterialChrome() }   // 4장. Android/Desktop만
}
// 테마는 이 호출을 감싼 NativeNavigationFrame(frame = ...)이 화면마다 씌웁니다(3단계).
```

- iOS에서는 `UITabBarController`와 탭마다 `UINavigationController`가 됩니다. `systemImage`는 탭 바 아이콘(SF Symbol)입니다.
- 한 번에 하나만 선언하되, 어느 것을 선언할지는 바꿀 수 있습니다. 로그인 흐름에서 메인 탭으로 넘어가는 경우가 대표적입니다.

  ```kotlin
  if (signedIn) NativeTabs(tabs, ...) { MaterialChrome() } else NativeStack(signInBackStack, ...) { MaterialChrome() }
  ```

  iOS는 윈도우의 루트 뷰 컨트롤러를 바꿀 때처럼 교차 페이드로 전환하고, 이전 계층의 화면은 모두 내립니다. `NavDisplay`가 composition을 떠날 때와 같은 의미입니다. decorator를 분기 안에서 만들면 떠나는 쪽의 ViewModel과 `rememberSaveable` 상태가 정리됩니다.
- `NativeTab`과 `NativeStack`은 `NavDisplay`처럼 `onBack`을 받습니다. 기본값은 마지막 엔트리를 지우는 것이고, 직접 넘길 때도 엔트리 하나를 pop해야 합니다.

### 탭 위의 공유 화면

프로필이나 편집 화면처럼 어느 탭에서든 여는 화면은 `sharedBackStack`에 둡니다. 탭마다 같은 라우트를 따로 쌓지 않아도 되고, 지금 앱이 바깥 `NavDisplay`로 탭 화면을 덮고 있다면 그 백스택을 그대로 넘기면 됩니다.

```kotlin
val details = rememberNavBackStack(navigationConfiguration)   // 비어 있어도 됩니다

NativeTabs(
    tabs = tabs,
    selectedTab = selectedTab,
    onTabSelected = { selectedTab = it },
    sharedBackStack = details,              // details.add(Profile)로 엽니다
    entryProvider = entryProvider,          // 탭 라우트와 공유 라우트를 모두 등록합니다
) { MaterialChrome() }
```

- 공유 화면은 항상 탭 바를 숨깁니다. 그래서 공유 화면이 보이는 동안 사용자는 탭을 바꿀 수 없습니다.
- iOS: 선택된 탭의 `UINavigationController`에서 그 탭 화면 위에 push됩니다. 뒤로 버튼, 가장자리 스와이프, 전환 애니메이션이 모두 네이티브입니다. 뒤로가기는 `onSharedBack`(기본값: 마지막 엔트리 삭제)으로 들어옵니다.
- 공유 화면이 보이는 동안 앱이 `selectedTab`을 바꾸면 공유 화면이 새 탭으로 옮겨 갑니다. 옮길 때는 애니메이션이 없습니다.
- Android/Desktop: 블록의 `entries`가 선택된 탭의 엔트리 뒤에 공유 엔트리를 붙인 목록이라, 4장의 `MaterialChrome()`을 그대로 쓸 수 있습니다. 상세 화면이 탭 바까지 덮으며 들어오게 하려면, 바깥 `NavDisplay`에 `sharedEntries`를 넘기고 탭 화면은 그 아래에 둡니다.

  ```kotlin
  NavDisplay(
      entries = listOf(NavEntry<NavKey>(TabsKey) { TabsChrome(tabEntries) }) + sharedEntries,
      onBack = onBack,
  )
  ```
- 상태 복원은 다른 백스택과 같습니다. 공유 화면은 늘 선택된 탭 위에 있으므로 따로 저장할 것이 없습니다.

Swift에서는 다른 Compose 뷰 컨트롤러처럼 붙입니다.

```swift
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController(restoredState: nil)
    }
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
// ContentView: ComposeView().ignoresSafeArea()
```

UIKit 앱에서는 `window.rootViewController`로 지정합니다.

## 6. 상태 복원 (iOS, 선택)

시스템이 백그라운드에서 앱을 종료해도, 다시 열면 다음 상태가 복원됩니다.
- 백스택과 선택된 탭
- 엔트리마다의 `rememberSaveable` 상태(스크롤 위치 포함)

라이브러리는 상태를 `NSData`로 넘겨주기만 하고, 저장은 iOS가 scene 상태를 보관하는 곳에 앱이 합니다. SwiftUI 예시입니다.

```swift
final class NavigationHolder { var controller: UIViewController? }

struct ComposeView: UIViewControllerRepresentable {
    let restoredState: Data?
    let holder: NavigationHolder

    func makeUIViewController(context: Context) -> UIViewController {
        let controller = MainViewControllerKt.MainViewController(restoredState: restoredState)
        holder.controller = controller
        return controller
    }
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    @SceneStorage("navigationState") private var navigationState: Data?
    @Environment(\.scenePhase) private var scenePhase
    @State private var holder = NavigationHolder()

    var body: some View {
        ComposeView(restoredState: navigationState, holder: holder)
            .ignoresSafeArea()
            .onChange(of: scenePhase) { _, phase in
                // .inactive에서 저장합니다. iOS는 .background 전에 scene 상태를 기록합니다.
                if phase != .active, let controller = holder.controller {
                    navigationState = MainViewControllerKt.saveNavigationState(controller: controller)
                }
            }
    }
}
```

- iOS 규칙을 그대로 따릅니다. 시스템이 종료했으면 복원하고, 사용자가 강제 종료했으면 버리며, 창(scene)마다 따로 저장합니다.
- 앱 업데이트로 저장되는 라우트 구조나 `App()` 안의 `rememberSaveable` 호출 구성(예: `rememberNavBackStack`을 하나 더 추가)이 바뀌면 `stateVersion`을 올립니다. 버전이 다른 저장 데이터는 디코딩하다 실패하지 않고 무시합니다.
  - `rememberSaveable` 값은 호출 위치로 짝지어집니다. 그래서 버전을 올리지 않으면 다른 호출이 이전 값을 받아 타입 오류가 날 수 있습니다.
  - 안전장치로, 복원 데이터 때문에 시작 composition이 실패하면 라이브러리가 데이터를 버리고 새로 시작합니다(로그를 남깁니다). 다만 타입이 우연히 맞아 엉뚱한 값으로 복원되는 경우는 막지 못합니다.
- 저장할 수 없는 `rememberSaveable` 값(enum, 임의 객체 등)은 빼고 저장합니다. 복원 후에는 그 값만 초기값으로 시작합니다. ViewModel은 저장하지 않습니다.
- UIKit 앱은 `UIScene.stateRestorationActivity`로 같은 일을 할 수 있습니다.

## 딥링크

링크 해석은 앱이 합니다. 링크를 라우트로 바꿔 백스택을 설정하면 네이티브 스택이 따라옵니다.
여러 단계를 한 번에 구성할 수 있고, 맨 위가 아닌 화면은 실제로 보일 때 만들어집니다.

```kotlin
selectedTab = 0
while (home.size > 1) home.removeLastOrNull()
home.addAll(listOf(Detail(3), Detail(7)))   // Home > Detail 3 > Detail 7
```

## 동작 규칙과 한계

- **OS 버전 분기를 두지 않습니다.** iOS 18.2에서 쓸 수 있는 API만 사용합니다.
  - iOS 26 전용 기능(탭 바 최소화, 탭 바 accessory, 검색 탭)은 제공하지 않습니다.
  - Liquid Glass는 시스템 바가 자동으로 적용합니다.
- **`hidesTabBar`는 UIKit 동작을 따릅니다.** 탭 바를 숨기는 화면 위로 push한 화면에서도 탭 바는 숨겨진 채로 있습니다.
- **큰 제목은 UIKit 전환을 그대로 따릅니다.** 큰 제목 화면을 스크롤해 바가 접힌 상태에서 큰 제목 상세 화면을 열고 스와이프로 돌아가면, 스와이프 도중 상세 화면의 큰 제목이 인라인 제목과 겹쳐 보입니다. 순수 UIKit 앱도 똑같이 그립니다. 그래서 큰 제목은 스택의 첫 화면(탭 루트)에만 쓰고, push되는 화면은 인라인 제목(`nativeScreen(largeTitle = false)`, 기본값)으로 두기를 권합니다.
- **탭 바는 앱의 최상위에만 둘 수 있습니다.** push한 화면 안에 다시 탭 바를 두는 구조(중첩 탭)는 지원하지 않습니다. 로그인 → 탭처럼 흐름이 바뀌면 루트 선언을 바꿉니다(5. iOS). 모달(sheet, full-screen cover)은 아직 없습니다.
- **같은 라우트를 두 번 push하면**(content key가 같으면) Navigation 3 의미대로 상태를 공유합니다. 두 화면 모두 정상 동작합니다.
- **`onBack`은 반드시 pop해야 합니다.** pop하지 않으면 네이티브 화면은 이미 사라진 상태가 되고, 다음에 백스택이 바뀔 때 다시 push됩니다.
- **뒤로 버튼 길게 누르기 메뉴**로 여러 화면을 한 번에 pop하면 뒤로가기 핸들러를 거치지 않습니다.
- **스크롤 연결의 한계**
  - 화면당 컨테이너 하나만 연결합니다.
  - 세로 스크롤만 연결합니다(리스트, 그리드, staggered 그리드, `verticalScroll`).
  - Lazy 레이아웃(리스트, 그리드)에는 네이티브 스크롤 인디케이터가 없습니다.
- **아직 확인하지 않은 것:** 실기기, iPad, VoiceOver

설계 결정, 검증 결과, 알려진 빈틈은 [`AGENTS.md`](AGENTS.md)에 정리되어 있습니다.

## 예제 앱

`example/`은 위 기능을 모두 쓰는 탭 3개(Home, Gallery, Settings)짜리 앱입니다. Gallery 탭은 그리드와 staggered 그리드를 보여줍니다. Settings의 "Sign out"을 누르면 탭 대신 로그인 스택(`NativeStack`)으로 바뀌고, "Sign in"으로 다시 탭으로 돌아옵니다. Android, Desktop, iOS가 `example/shared`의 `App()` 하나를 함께 씁니다.

```bash
example/run-sample.sh            # iOS 시뮬레이터와 Android 에뮬레이터에 빌드·설치·실행
example/run-sample.sh ios        # 또는 android
./gradlew :example:desktopApp:run
```

## 테스트

```bash
./gradlew jvmTest testAndroidHostTest iosSimulatorArm64Test jsTest wasmJsTest   # 단위 테스트
example/iosUITests/run.sh <시뮬레이터 UDID>                                       # iOS UI 테스트 (실제 터치)
```

웹 테스트는 헤드리스 브라우저에서 실행됩니다. 웹 의존성이 바뀌면 `./gradlew kotlinUpgradeYarnLock kotlinWasmUpgradeYarnLock`을 실행합니다.

## 프로젝트 구조

```
.
├── library/              # 배포 대상 라이브러리 (zone.ien.navigation:compose-swift-navigation3)
└── example/
    ├── shared/           # 예제 앱: 공통 App()과 화면, Android/Desktop용 Material 구성, iOS 진입점
    ├── androidApp/       # Android 진입점
    ├── desktopApp/       # Desktop(JVM) 진입점
    ├── iosApp/           # iOS 진입점 (Xcode 프로젝트, `Shared` 프레임워크를 import)
    ├── iosUITests/       # 예제 앱을 실제 터치로 구동하는 XCUITest 프로젝트
    └── run-sample.sh     # 예제를 시뮬레이터/에뮬레이터에 띄우는 스크립트
```

Gradle 데몬 JVM은 `gradle/gradle-daemon-jvm.properties`에 고정되어 있습니다(JetBrains Runtime 25). 소스는 JDK 17 툴체인으로 컴파일하며, 없으면 자동으로 받습니다.

## 기여

먼저 [`AGENTS.md`](AGENTS.md)를 읽어 주세요. 목표, 설계 결정, 변경을 검증하는 방법이 정리되어 있습니다.

## 라이선스

[Apache License 2.0](LICENSE)
