# compose-swift-navigation3

[English](README.md) | [한국어](README_ko.md)

**Navigation 3**로 만든 Compose Multiplatform 앱에 실제 UIKit 내비게이션을 연결하는 라이브러리입니다.
iOS에서는 Kotlin 백스택을 실제 `UITabBarController`와 `UINavigationController`로 보여줍니다. Android·Desktop·Web에서는 `otherPlatforms` 블록으로 기존 `NavDisplay`와 앱 UI를 실행합니다.

라우트, 백스택, 엔트리 제공자, 화면, ViewModel, 루트 `App()`은 공통 코드에 둡니다. 라이브러리는 Kotlin으로 구현되어 별도의 Swift 내비게이션 계층이 필요 없습니다.

> 초기 개발 단계이며 버전은 `0.0.0`입니다. 2026-11-15 확인 시 Maven Central 메타데이터가 없었습니다. 아래 소스 또는 로컬 Maven 설치 방법을 사용하세요.

<a id="toc"></a>

## 목차

- [주요 기능](#features)
- [설치](#installation)
- [빠른 시작](#quick-start)
- [기존 Navigation 3 앱에서 옮기기](#migration)
- [API](#api)
- [호환성과 한계](#compatibility)
- [예제 앱](#examples)
- [빌드와 테스트](#testing)
- [프로젝트 구조](#structure)
- [기여와 참고 자료](#contributing)
- [라이선스](#license)

<a id="전체-구조"></a>
<a id="앱이-하지-않아도-되는-것"></a>
<a id="features"></a>

## 주요 기능

- 네이티브 push/pop 전환, 뒤로 버튼, 취소 가능한 가장자리 스와이프, 뒤로 버튼 길게 누르기 메뉴, 선택한 탭 다시 누르기
- 탭별 독립 백스택과 화면 상태 유지, 선택된 탭 위에 여는 공유 화면
- 내비게이션 바 제목, 접히는 큰 제목, 바 버튼, 메뉴, 하단 툴바, `UISearchController` 검색
- UIKit 스크롤 물리, 바운스, 상태 바 탭으로 맨 위 이동, 키보드 여백
- Navigation 3 decorator를 통한 저장 가능 상태와 엔트리별 ViewModel 관리, 선택적인 iOS scene 상태 복원
- 로그인·메인 흐름처럼 `NativeStack`과 `NativeTabs` 사이 루트 전환
- iOS 26 SDK 이상으로 빌드하면 iOS 26 이상에서 시스템 Liquid Glass 적용. 앱이 `UIDesignRequiresCompatibility`로 끄면 예외이며, 이전 지원 버전은 해당 시스템 디자인 사용

백스택의 주인은 Kotlin입니다. 네이티브 pop은 제거된 엔트리마다 앱의 뒤로가기 콜백을 한 번 호출하고, 취소된 제스처는 Kotlin 스택을 바꾸지 않습니다.

<a id="설치"></a>
<a id="installation"></a>

## 설치

저장소 루트에서 현재 소스를 로컬 Maven 저장소에 배포합니다.

```bash
./gradlew :library:publishToMavenLocal
```

사용하는 앱의 의존성 저장소에 `mavenCentral()`보다 앞서 `mavenLocal()`을 추가하고 `google()`은 유지합니다. 공유 모듈에는 다음 의존성을 추가합니다.

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenLocal()
        mavenCentral()
    }
}
```

```kotlin
// shared/build.gradle.kts
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("zone.ien.navigation:navigation:0.0.0")
        }
    }
}
```

또는 [예제 모듈](example/shared/build.gradle.kts)처럼 기존 `library` 모듈을 빌드에 포함하고 프로젝트 의존성으로 사용합니다.

앱에는 다음 설정도 필요합니다.

- `@Serializable` 라우트를 위한 Kotlin serialization 플러그인. 앱의 Kotlin 버전과 맞춥니다.
- iOS 외 플랫폼의 `NavDisplay`를 위한 `org.jetbrains.androidx.navigation3:navigation3-ui:1.2.0`
- 아래 Material UI를 사용한다면 Compose Material 3
- 엔트리별 ViewModel이 필요하다면 `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-navigation3:2.11.0`

라이브러리는 `androidx.navigation3:navigation3-runtime:1.2.0`을 전이 의존성으로 공개하며 `navigation3-ui`나 Material 3에는 의존하지 않습니다. 저장소의 툴체인은 [버전 카탈로그](gradle/libs.versions.toml)와 [호환성](#compatibility)을 참고하세요.

<a id="3단계-navdisplay를-nativestack으로-감쌉니다"></a>
<a id="4단계-ios-진입점을-한-줄로-바꿉니다"></a>
<a id="quick-start"></a>

## 빠른 시작

설치 후 `commonMain`에 다음 최소 스택을 둡니다. import를 포함한 예제이며 serialization 플러그인을 먼저 적용해야 합니다. Android 외 타깃에서도 백스택을 저장할 수 있도록 `NavKey` 하위 타입을 등록합니다.

```kotlin
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import zone.ien.navigation.NativeNavigationFrame
import zone.ien.navigation.NativeStack
import zone.ien.navigation.nativeContentPadding
import zone.ien.navigation.nativeScreen

@Serializable data object Home : NavKey
@Serializable data class Detail(val id: Int) : NavKey

private val navigationConfiguration = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(Home::class)
            subclass(Detail::class)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    NativeNavigationFrame(frame = { content -> MaterialTheme(content = content) }) {
        val backStack = rememberNavBackStack(navigationConfiguration, Home)
        val provider = remember(backStack) {
            entryProvider<NavKey> {
                entry<Home>(metadata = nativeScreen(title = "Home")) {
                    Button(
                        onClick = { backStack.add(Detail(1)) },
                        modifier = Modifier.padding(nativeContentPadding()),
                    ) { Text(text = "Open detail") }
                }
                entry<Detail>(metadata = { key -> nativeScreen(title = "Detail ${key.id}") }) { key ->
                    Text(text = "Detail ${key.id}", modifier = Modifier.padding(nativeContentPadding()))
                }
            }
        }
        NativeStack(backStack = backStack, entryProvider = provider) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(text = title.orEmpty()) },
                        navigationIcon = {
                            if (canGoBack) TextButton(onClick = onBack) { Text(text = "Back") }
                        },
                    )
                },
            ) { padding ->
                NavDisplay(entries = entries, modifier = Modifier.padding(padding), onBack = onBack)
            }
        }
    }
}
```

Android는 `setContent { App() }`, Desktop은 윈도우 안에서 `App()`을 호출합니다. `iosMain`에서는 네이티브 루트를 공개합니다.

```kotlin
import platform.UIKit.UIViewController
import zone.ien.navigation.NativeNavigationViewController

fun MainViewController(): UIViewController = NativeNavigationViewController { App() }
```

SwiftUI에서는 `import SwiftUI`, `import UIKit`, Kotlin 프레임워크 import(예제는 `Shared`)와 함께 아래처럼 붙입니다. 기존 `UIViewControllerRepresentable`이 있다면 그대로 활용합니다.

```swift
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
// ComposeView().ignoresSafeArea()
```

UIKit 앱은 반환값을 `window.rootViewController`에 지정합니다. **Open detail**을 누른 뒤 뒤로가기를 확인합니다. iOS는 시스템 내비게이션 컨트롤러, Android·Desktop은 위 Material 바를 사용합니다. 저장소의 전체 예제를 보려면 루트에서 `example/run-sample.sh ios` 또는 `example/run-sample.sh android`를 실행합니다.

<a id="도입-체크리스트"></a>
<a id="반드시-할-일"></a>
<a id="앱-구조에-따라-할-일"></a>
<a id="마지막-확인"></a>
<a id="기존-navigation-3-앱에서-옮기기"></a>
<a id="한눈에-보기"></a>
<a id="2단계-화면에서-탑바를-빼고-선언으로-바꿉니다"></a>
<a id="옮긴-뒤-확인할-것"></a>
<a id="migration"></a>

## 기존 Navigation 3 앱에서 옮기기

라우트, 내비게이터, 백스택, 엔트리 제공자, ViewModel, 화면 뒤로가기 핸들러는 유지합니다. 다음 부분을 옮깁니다.

| 기존 코드 | 변경 |
| --- | --- |
| `NavDisplay(backStack, entryProvider, entryDecorators, ...)` | `NativeStack` 또는 `NativeTabs`로 감싸고, receiver가 주는 decorated `entries`와 `onBack`을 `NavDisplay`에 전달 |
| Material `Scaffold`, 상단·하단 바 | `otherPlatforms`에 유지하고 `NativeNavigationScope`의 제목, 바 아이템, 검색, 탭으로 그림 |
| 화면 제목 | 고정 제목은 `nativeScreen`, 동적 제목은 `NativeBarItems`로 선언 |
| 화면별 `TopAppBar`와 뒤로 버튼 | 앱 공통 바로 옮기거나 기존 바를 `if (!LocalNativeScreen.current)`로 감싸 숨김 |
| 테마, DI, 루트 Scaffold, 스낵바 호스트 | DI locals는 `NativeNavigationFrame.providers`, 테마와 화면 UI 래퍼는 `frame`으로 전달 |
| 화면의 `innerPadding` | 스크롤 화면은 `rememberNativeScroll(state)`, 그 외는 `nativeContentPadding()` 사용 |
| 탭 위의 바깥 스택 | 탭 자체를 나타내던 엔트리를 제외한 상세 엔트리를 `NativeTabs(sharedBackStack = ...)`로 전달 |
| iOS `ComposeUIViewController { App() }` | `NativeNavigationViewController { App() }`로 바꾸고 `.ignoresSafeArea()`로 붙임 |

바를 옮긴 뒤 Android 동작을 확인합니다. iOS 18.x(18.2 이상)와 iOS 26 이상에서 push, 네이티브 뒤로가기, 가장자리 스와이프와 취소, 큰 제목 스크롤, 탭 상태 유지, 공유 화면, 선택적인 상태 복원을 확인합니다.

<a id="지켜야-할-규칙"></a>
<a id="테마와-앱-전역-compositionlocal"></a>
<a id="composition"></a>

### 테마, locals, 상태 소유권

```kotlin
NativeNavigationFrame(
    providers = { content -> CompositionLocalProvider(LocalAppGraph provides graph, content = content) },
    frame = { content -> AppTheme { RootScaffold { content() } } },
) {
    NativeStack(backStack = backStack, entryProvider = entryProvider) {
        NavDisplay(entries = entries, onBack = onBack)
    }
}
```

이 코드는 앱에 이미 있는 `LocalAppGraph`, `graph`, `AppTheme`, `RootScaffold`를 사용한 부분 예제입니다.

- iOS 외 플랫폼은 `providers { frame { content() } }`으로 앱 전체를 한 번 감쌉니다. iOS는 엔트리마다 컨트롤러와 composition이 따로 있습니다. `providers`는 UI 없는 내비게이션 호스트와 각 화면을, `frame`은 각 화면을 감쌉니다.
- `providers`나 `frame`·내비게이션 선언의 `otherPlatforms` 블록 밖에는 UI를 두지 않습니다. iOS 호스트는 UI 노드를 거부합니다. 앱 수준의 대화상자는 화면 안으로 옮기거나 iOS에서 시스템 알림창을 사용하는 구현을 씁니다.
- 호스트는 lifecycle·ViewModel·saved state owner, `LocalUIViewController`, `LocalDensity`를 제공합니다. 공통 리소스 조회와 네이티브 알림창 연동에 사용할 수 있습니다. 화면 환경이 필요한 테마는 `frame`에 둡니다.
- 화면 내용이나 `frame` 안에서 `LocalNativeScreen.current`로 Compose 바를 숨기고 `imePadding()`을 네이티브 화면 외에서만 붙입니다. 네이티브 콘텐츠 여백에 바 높이가 들어 있으므로 시스템 바 여백을 다시 더하지 않습니다.
- 네이티브 바는 강제한 Compose 테마가 아니라 시스템 라이트·다크 모드를 따릅니다. 테마도 시스템을 따르면 일치합니다. `NativeNavigationFrame` 대신 기존 `NavEntryDecorator`로 화면 내용을 감싸도 됩니다.
- 엔트리는 백스택 변경 시 다시 만들어지므로 제공자가 캡처한 일반 값이 갱신되지 않을 수 있습니다. 변하는 값은 State, `rememberUpdatedState`, ViewModel로 읽습니다.
- 상태 홀더는 `rememberSaveable`이 반환한 `MutableState`를 직접 소유합니다. `SideEffect`로 저장 값에 복사하면 호출부가 재구성되지 않을 때 변경을 놓칩니다.

<a id="api"></a>

## API

공개 API의 패키지는 `zone.ien.navigation`입니다. 선언과 KDoc은 [공통 소스](library/src/commonMain/kotlin/zone/ien/navigation)와 [iOS 소스](library/src/iosMain/kotlin/zone/ien/navigation)에 있습니다.

| API | 역할 |
| --- | --- |
| `NativeStack`, `NativeTabs`, `NativeTab` | 스택, 탭, 탭별 스택의 공통 선언 |
| `NativeNavigationScope<T>` | `otherPlatforms`에 전달하는 decorated 엔트리와 바 데이터 |
| `NativeNavigationFrame`, `LocalNativeScreen` | 앱 전역 providers·frame과 네이티브 화면 여부 |
| `nativeScreen`, `NavEntry.nativeTitle`, `nativeLargeTitle`, `nativeHidesTabBar`, `nativeTitleImage` | 엔트리 메타데이터와 접근자 |
| `rememberNativeScroll`, `NativeScroll`, `nativeContentPadding`, `Modifier.nativeScrollExclusion()` | 네이티브 세로 스크롤과 여백 |
| `NativeBarItems`, `NativeBarButton`, `NativeMenuAction`, `NativeBarContent` | 바 선언과 데이터 |
| `NativeSearchBar`, `NativeSearch` | 검색 선언과 데이터 |
| `NativeBarState`, `ProvideNativeBarState` | `NativeStack`·`NativeTabs` 없이 일반 `NavDisplay`의 선언 수집 |
| `NativeNavigationViewController`, `saveNativeNavigationState` | iOS 내비게이션 호스트와 선택적인 상태 직렬화 |
| `ComposeScreenViewController` | 앱 소유 `UINavigationController`에 넣는 독립 iOS Compose 화면 |

<a id="1-라우트와-백스택-공통"></a>
<a id="5-ios"></a>
<a id="탭-위의-공유-화면"></a>
<a id="딥링크"></a>
<a id="stack"></a>

### 스택, 탭, 공유 화면

관찰 가능한 백스택(`rememberNavBackStack` 또는 snapshot state 리스트)을 사용합니다. 화면 이동은 리스트를 변경하는 방식입니다.

```kotlin
backStack.add(Detail(3))
backStack.removeLastOrNull()
backStack[backStack.lastIndex] = Detail(103)
while (backStack.size > 1) backStack.removeLastOrNull()
```

순서대로 push, pop, replace, 루트까지 pop입니다. 탭을 쓰려면 제공자와 직렬화 설정에 탭·공유 라우트를 모두 등록하고 다음처럼 선언합니다.

```kotlin
val home = rememberNavBackStack(navigationConfiguration, Home)
val settings = rememberNavBackStack(navigationConfiguration, Settings)
val shared = rememberNavBackStack(navigationConfiguration)
var selectedTab by rememberSaveable { mutableIntStateOf(0) }

NativeTabs(
    tabs = listOf(
        NativeTab("Home", systemImage = "house", backStack = home),
        NativeTab("Settings", systemImage = "gearshape", backStack = settings),
    ),
    selectedTab = selectedTab,
    onTabSelected = { selectedTab = it },
    sharedBackStack = shared,
    entryDecorators = listOf(
        rememberSaveableStateHolderNavEntryDecorator(),
        rememberViewModelStoreNavEntryDecorator(),
    ),
    entryProvider = entryProvider,
) { MaterialChrome() }
```

이 부분 예제는 빠른 시작에 앱의 `Settings` 라우트, 제공자, `NativeNavigationScope<NavKey>.MaterialChrome()` 렌더러를 더한 것입니다. 전체 구현은 [App.kt](example/shared/src/commonMain/kotlin/zone/ien/utils/example/App.kt)를 참고하세요.

- iOS에서는 `UITabBarController`와 탭마다 하나의 `UINavigationController`가 됩니다. 다른 플랫폼도 탭별 decorated 엔트리를 유지하므로 저장 가능 상태와 ViewModel이 유지됩니다.
- `sharedBackStack`은 비어 있어도 됩니다. 공유 화면은 항상 탭 바를 숨기고 선택된 탭 위에 push됩니다. `onSharedBack`은 공유 엔트리 하나를 제거합니다. 공유 화면이 보이는 동안 앱이 `selectedTab`을 바꾸면 애니메이션 없이 새 탭으로 옮겨 갑니다.
- `NativeStack`과 `NativeTab`의 기본 `onBack`은 전달한 리스트가 변경 가능하면 마지막 엔트리를 제거합니다. 직접 지정하는 콜백과 `onSharedBack`은 반드시 엔트리 하나를 pop해야 합니다. 그렇지 않으면 UIKit과 Kotlin이 어긋나 다음 스택 변경 때 화면이 다시 push될 수 있습니다.
- `NativeStack`·`NativeTabs`는 기본으로 저장 가능 상태 decorator를 사용합니다. 엔트리별 ViewModel에는 `rememberViewModelStoreNavEntryDecorator()`를 추가합니다. `NavDisplay`에 넘기는 `entries`에 decorator를 다시 적용하지 않습니다.
- 루트는 한 번에 하나만 선언합니다. `if (signedIn)`으로 `NativeTabs`와 `NativeStack`을 바꿀 수 있습니다. iOS는 계층을 교차 페이드하고 이전 화면을 내립니다. 각 분기 안에서 decorator를 만들면 떠나는 흐름의 ViewModel과 저장 가능 상태가 정리됩니다.
- 딥링크 해석은 앱이 합니다. 탭을 선택하고 Kotlin 스택을 목적지 목록으로 교체하거나 확장합니다. 맨 위가 아닌 컨트롤러는 실제로 보일 때 처음 만듭니다.

<a id="4-android와-desktop"></a>
<a id="scope"></a>

### Android·Desktop·Web 렌더링

라이브러리는 이 플랫폼에서 UI를 그리지 않습니다. 블록 안에서 `NativeNavigationScope`의 다음 값을 읽어 기존 컴포넌트로 그립니다.

| 값 | 의미 |
| --- | --- |
| `entries` | 선택된 탭·스택 엔트리 뒤에 공유 엔트리를 붙인 decorated 목록 |
| `tabEntries`, `sharedEntries` | 탭 위에 바깥 `NavDisplay`를 둘 때 나눠 쓰는 두 목록 |
| `onBack`, `canGoBack` | 보이는 스택에서 pop, 맨 위 아래에 엔트리가 있는지 여부 |
| `title`, `largeTitle`, `titleImage` | 동적 제목 또는 metadata 제목, 큰 제목 여부, 선택적인 painter 람다 |
| `leading`, `trailing`, `toolbar`, `search` | 수집한 바·검색 선언 |
| `tabs`, `selectedTab`, `onTabSelected` | 탭 구성과 선택. `NativeStack`에는 탭 없음 |
| `tabBarVisible` | 탭이 있고 공유 화면이 없으며, push된 탭 엔트리 중 바를 숨기는 화면이 없으면 표시 |

탭 바까지 덮는 바깥 스택이 필요하면 엔트리로 `listOf(NavEntry<NavKey>(TabsKey) { TabsChrome(tabEntries) }) + sharedEntries`를 사용합니다. `TabsKey`와 `TabsChrome`은 앱에서 정의합니다.

아직 composition에 남은 화면 중 가장 최근에 등록한 바·검색 선언이 우선합니다. 새 화면이 선언하지 않으면 나가는 화면의 선언이 잠시 남을 수 있습니다. `systemImage`는 iOS SF Symbol 이름이므로 Android 아이콘은 렌더러에서 정합니다. 메뉴·툴바·검색 렌더링 전체 예제는 [App.kt](example/shared/src/commonMain/kotlin/zone/ien/utils/example/App.kt)에 있습니다.

<a id="1단계-엔트리에-화면-정보를-더합니다"></a>
<a id="2-엔트리와-화면-메타데이터-공통"></a>
<a id="metadata"></a>

### 화면 메타데이터와 제목 이미지

```kotlin
entry<Detail>(metadata = { key -> nativeScreen(title = "Detail ${key.id}", hidesTabBar = true) }) { key ->
    DetailScreen(key.id)
}
```

`DetailScreen`은 앱의 화면 Composable입니다.

| 매개변수 | 기본값 | iOS 동작 |
| --- | --- | --- |
| `title` | `null` | `navigationItem.title`. 고정 제목은 push 시작 전에 설정 |
| `largeTitle` | `false` | 스크롤에 따라 접히는 큰 제목. 스택 루트에서만 사용 권장 |
| `hidesTabBar` | `false` | `hidesBottomBarWhenPushed`. 그 위로 push한 화면에서도 탭 바 숨김 유지 |
| `titleImage` | `null` | `@Composable () -> Painter`를 종횡비를 유지한 높이 28pt `titleView`로 표시 |

로고는 기존 Compose 리소스로 `nativeScreen(title = "Home", titleImage = { painterResource(Res.drawable.logo) })`처럼 지정합니다. VoiceOver와 다음 화면의 뒤로 버튼을 위해 `title`을 유지합니다. iOS에서는 push 전에 호스트에서 painter를 실행하므로 화면 테마 색과 다크 모드 리소스가 적용되지 않습니다. 다른 플랫폼은 null 여부를 확인한 뒤 `Image(image(), contentDescription = title, modifier = Modifier.height(28.dp))`처럼 `titleImage`를 직접 그립니다. 제목 이미지는 이 프로젝트에서 시험 중인 기능입니다.

<a id="3-화면-작성-공통"></a>
<a id="스크롤하는-화면"></a>
<a id="스크롤하지-않는-화면"></a>
<a id="scroll"></a>

### 스크롤과 콘텐츠 여백

화면의 주 세로 컨테이너를 연결합니다. UIKit이 pan·감속·바운스·맨 위 이동·키보드 여백을 처리하고 Compose가 offset을 따라갑니다. 반환된 두 값을 컨테이너에 전달합니다.

```kotlin
val state = rememberLazyListState()
val nativeScroll = rememberNativeScroll(state, background = MaterialTheme.colorScheme.background)
LazyColumn(
    state = state,
    userScrollEnabled = nativeScroll.userScrollEnabled,
    contentPadding = nativeScroll.contentPadding,
) { /* screen rows */ }
```

```kotlin
val state = rememberScrollState()
val nativeScroll = rememberNativeScroll(state)
Column(
    Modifier.verticalScroll(state, enabled = nativeScroll.userScrollEnabled)
        .padding(nativeScroll.contentPadding),
) { /* screen content */ }
```

- `LazyListState`, `LazyGridState`, `LazyStaggeredGridState`, `ScrollState`를 받는 오버로드가 있습니다. 세로 그리드에도 같은 방식으로 `userScrollEnabled`와 `contentPadding`을 연결합니다.
- iOS에서는 반환된 플래그로 컨테이너의 사용자 스크롤을 꺼야 합니다. 켜 두면 Compose가 드래그를 가져가 큰 제목이 다시 펼쳐지지 않을 수 있습니다. 자체 스크롤을 끌 수 없는 컨테이너는 제어 가능한 바깥 컨테이너로 구성하거나 교체해야 합니다.
- 네이티브 화면 외에서는 플래그가 `true`, 여백은 0입니다. 기존 Scaffold가 평소처럼 여백을 제공합니다.
- `background`는 overscroll로 드러나는 영역의 색이며, 생략하면 iOS `systemBackgroundColor`를 사용합니다.
- 화면마다 세로 컨테이너 하나만 연결합니다. 가로 리스트·그리드는 연결하지 않습니다. 세로 slider나 순서 변경 핸들에는 `Modifier.nativeScrollExclusion()`을 붙여 그 안에서 시작한 드래그를 Compose에 넘깁니다.
- 스크롤하지 않는 화면은 `Modifier.padding(nativeContentPadding())`을 사용합니다. 바 높이가 이미 포함되므로 `WindowInsets`나 네이티브 화면의 `imePadding()`으로 중복 여백을 주지 않습니다.
- `animateScrollToItem`, `bringIntoView`처럼 Compose에서 실행한 스크롤도 네이티브 바에 반영합니다.

<a id="바-버튼-메뉴-툴바"></a>
<a id="검색"></a>
<a id="bars"></a>

### 바 아이템, 메뉴, 검색

화면 상태와 ViewModel을 읽을 수 있도록 액션을 화면 내용 안에 선언합니다.

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

- `leading`과 `trailing`은 왼쪽에서 오른쪽 순서의 `UIBarButtonItem`이 되며 네이티브 뒤로 버튼은 유지합니다. `menu`가 비어 있지 않으면 버튼의 `onClick` 대신 `UIMenu`를 열고 액션은 `destructive`를 지원합니다.
- `toolbar`는 해당 화면이 맨 위일 때만 보입니다. `enabled`로 활성 여부를 제어하고, `systemImage`는 SF Symbol, `title`은 접근성 레이블로 사용합니다. 네이티브 버튼은 모양이 바뀔 때만 다시 만듭니다.
- 화면 선언은 내용이 composition된 뒤 등록되어 push 시작보다 늦습니다. 전환과 함께 보여야 하는 고정 제목은 `nativeScreen(title)`, 상태에 따른 변경은 `NativeBarItems(title)`을 사용합니다.

검색어와 필터링은 앱이 소유합니다.

```kotlin
var query by rememberSaveable { mutableStateOf("") }
NativeSearchBar(query = query, onQueryChange = { query = it }, placeholder = "Search")
```

iOS는 큰 제목 아래의 `UISearchController`를 사용하며 아래로 스크롤하면 숨기고 지우기·취소 버튼을 제공합니다. iOS 외에서는 `scope.search`를 직접 그립니다. 일반 `NavDisplay` 주변에서 선언을 수집하려면 remember한 `NativeBarState`와 `ProvideNativeBarState(state) { ... }`를 사용합니다.

<a id="뒤로가기-핸들러"></a>
<a id="back-handlers"></a>

### 화면 뒤로가기 핸들러

네이티브 화면 안에서 기존 `NavigationBackHandler`, `NavigationEventHandler`, 구형 `BackHandler`를 사용할 수 있습니다. back이 활성화된 핸들러는 pop 대신 네이티브 버튼과 가장자리 스와이프를 받습니다. forward 전용 핸들러는 네이티브 뒤로가기를 막지 않습니다.

```kotlin
NavigationBackHandler(
    state = rememberNavigationEventState(NavigationEventInfo.None),
    isBackEnabled = hasUnsavedChanges,
    onBackCompleted = { showDiscardDialog = true },
)
```

앱의 미저장 상태와 대화상자 상태를 사용합니다. 뒤로 버튼의 길게 누르기 메뉴로 여러 화면을 pop하면 핸들러를 거치지 않습니다. 핸들러가 받은 스와이프에는 진행·완료·취소 이벤트가 있지만 UIKit 전환 애니메이션은 없으므로 화면이 진행 상태를 직접 표현해야 합니다.

<a id="6-상태-복원-ios-선택"></a>
<a id="restoration"></a>

### 선택적인 iOS 상태 복원

라이브러리는 내비게이션과 엔트리의 `rememberSaveable` 상태(스크롤 위치 포함)를 `NSData`로 직렬화하고 저장은 앱이 담당합니다. `iosMain`에 다음 래퍼를 공개합니다.

```kotlin
fun MainViewController(restoredState: NSData?): UIViewController =
    NativeNavigationViewController(restoredState, stateVersion = 1) { App() }

fun saveNavigationState(controller: UIViewController): NSData? =
    saveNativeNavigationState(controller)
```

`platform.Foundation.NSData`, `platform.UIKit.UIViewController`, 위 두 `zone.ien.navigation` API를 import합니다. 빠른 시작의 인자 없는 Swift 브리지 대신 다음 scene 저장 브리지를 사용합니다.

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

- scene이 `.inactive`가 될 때 저장합니다. `.background`에서만 저장하면 scene 상태 기록보다 늦습니다. `@SceneStorage`는 시스템 종료 뒤 복원하고 사용자 강제 종료 뒤에는 버리며 scene마다 따로 보관합니다. UIKit 앱은 `UIScene.stateRestorationActivity`를 사용할 수 있습니다.
- 라우트 직렬화나 `rememberSaveable`·`rememberNavBackStack` 호출 수·순서가 바뀌면 `stateVersion`(기본값 `0`)을 올립니다. 버전이 다른 데이터는 무시합니다. 호스트의 최초 복원이 실패하면 저장 데이터 없이 재시도하지만, 잘못된 호출 위치에서 우연히 타입이 맞는 경우와 나중의 화면 composition 오류는 이 안전장치가 잡지 못합니다.
- 적합한 saver가 없는 임의 객체나 enum 등 저장할 수 없는 값은 제외하고 로그를 남깁니다. 해당 값은 초기값으로 시작합니다. ViewModel과 화면별 `SavedStateHandle`은 직렬화하지 않습니다.
- 공유 화면도 같은 방식으로 복원하며 항상 선택된 탭 위에 놓입니다.

<a id="요구-사항"></a>
<a id="ios에서-쓰지-않는-것"></a>
<a id="동작-규칙과-한계"></a>
<a id="compatibility"></a>

## 호환성과 한계

다음은 저장소에 설정된 버전입니다. 모든 버전 조합을 지원한다는 의미는 아닙니다.

| 구성 요소 | 설정 |
| --- | --- |
| Kotlin / Compose Multiplatform | 2.4.21 / 1.12.2 |
| Navigation 3 runtime / UI | 1.2.0 / 1.2.0 |
| Lifecycle / Material 3(예제) | 2.11.0 / 1.12.0-alpha03 |
| AGP / Gradle | 9.3.1 / 9.7.0 |
| Gradle 데몬 / 컴파일 | JetBrains Runtime 25 / JDK 17 툴체인 |
| Android | minSdk 29, compileSdk/targetSdk 37 |
| iOS | 18.2 이상, `iosArm64`·`iosSimulatorArm64`. Liquid Glass는 iOS 26 이상 SDK 빌드 필요 |
| 기타 라이브러리 타깃 | `jvm`, 브라우저 전용 `js`·`wasmJs` |

CI는 JVM·Android host·iOS 시뮬레이터·JS·Wasm 테스트와 Android·Desktop 예제 빌드를 실행합니다. iOS 앱 빌드와 XCUITest는 실행하지 않습니다. [AGENTS.md](AGENTS.md)에 iOS 18.5·27.2 XCUITest와 Android 수동 검증 기록이 있습니다. 과거 결과이며 현재의 모든 변경을 보증하지 않습니다. 최근 기록된 제목 이미지 검증은 iOS 27.2 UI 테스트 15개 통과이며, 해당 기능은 iOS 18.x에서 재실행하지 않았습니다.

알려진 제약은 다음과 같습니다.

- 런타임 OS 버전 분기를 두지 않습니다. 탭 바 최소화, `bottomAccessory`, 직접 만드는 glass 효과, 검색 탭 등 iOS 26 전용 기능은 노출하지 않습니다.
- 탭은 루트에서만 사용합니다. 로그인·메인 흐름은 루트 선언을 바꾸며, 중첩 탭과 내비게이션 모달 API는 지원하지 않습니다.
- iOS는 `otherPlatforms`를 실행하지 않으므로 안의 `NavDisplay` 전환 설정과 `sceneStrategy`는 적용되지 않습니다. 모든 엔트리는 push되는 전체 화면입니다. 다이얼로그·바텀시트는 내비게이션 엔트리 대신 화면 내용 안에서 띄웁니다.
- 이전 화면의 큰 제목이 접힌 상태에서 가장자리 스와이프로 돌아가면, push된 화면의 큰 제목이 인라인 제목과 겹칠 수 있습니다. UIKit 동작이며 큰 제목은 스택 루트에서만 사용하는 것을 권장합니다.
- 같은 content key는 Navigation 3 상태를 공유합니다. 같은 라우트를 두 번 push할 수 있지만 push 횟수만으로 독립 상태가 되지는 않습니다.
- 화면마다 세로 스크롤 컨테이너 하나만 연결하고 중첩 세로 스크롤은 동기화하지 않습니다. Lazy 레이아웃에는 네이티브 스크롤 인디케이터가 없습니다. 컨테이너는 사용자 스크롤을 끌 수 있어야 합니다.
- 네이티브 바 버튼 안의 Compose 커스텀 뷰, 검색 scope·token·별도 검색 결과 컨트롤러는 지원하지 않습니다.
- 실기기, iPad, VoiceOver, 하드웨어 키보드 뒤로가기 전달, Compose 텍스트 필드 포커스·키보드 상호작용, 스크롤 성능은 충분히 검증하지 않았습니다. 화면 전체를 덮는 제스처와 가로 화면의 세밀한 안전 영역도 추가 확인이 필요합니다.

<a id="예제-앱"></a>
<a id="examples"></a>

## 예제 앱

[예제](example)는 Android·Desktop·iOS가 `App()` 하나를 공유합니다. Home·Gallery·Settings 탭, 일반·staggered 그리드, 탭 바를 숨기거나 유지하는 상세 화면, 공유 Profile 화면, PNG·벡터 제목 이미지, 로그아웃·로그인 루트 전환을 보여줍니다.

```bash
example/run-sample.sh
example/run-sample.sh ios
example/run-sample.sh android
./gradlew :example:desktopApp:run
```

저장소 루트에서 실행합니다. 스크립트가 앱을 빌드·설치·실행합니다. iOS 시뮬레이터는 `IOS_SIM_UDID`, Android 기기는 `ANDROID_SERIAL`, 필요할 때 시작할 에뮬레이터는 `ANDROID_AVD`로 지정합니다. Xcode 27의 iOS 뷰어는 DeviceHub이며 스크립트가 사용 가능한 뷰어를 엽니다. 웹 예제 앱은 없습니다.

<a id="테스트"></a>
<a id="testing"></a>

## 빌드와 테스트

저장소 루트에서 실행합니다. iOS 태스크에는 macOS와 Xcode가 필요합니다. Gradle 데몬은 고정된 JetBrains Runtime 25, 컴파일은 JDK 17을 사용하며 없으면 자동으로 받습니다.

```bash
./gradlew jvmTest
./gradlew testAndroidHostTest
./gradlew iosSimulatorArm64Test
./gradlew jsTest wasmJsTest
./gradlew :example:androidApp:assembleDebug :example:desktopApp:jar
```

```bash
xcrun simctl list devices available
xcodebuild -project example/iosApp/iosApp.xcodeproj -scheme example.iosApp \
  -configuration Debug -destination 'platform=iOS Simulator,name=iPhone 17,OS=27.2' \
  -derivedDataPath build/xcode build
```

```bash
example/iosUITests/run.sh <UDID>
python3 -I example/iosUITests/timeline.py build/ui-tests/<UDID>
```

- 사용 가능한 시뮬레이터 이름·OS 또는 UDID로 바꿉니다. UI 테스트는 앱을 종료하고 재실행하므로 사람이 보는 예제와 별도 시뮬레이터를 사용합니다.
- `example.iosApp` 스킴은 현재 gitignore 대상 `xcuserdata`에 있습니다. 새 클론에서는 위 명령을 쓰기 전에 Xcode에서 스킴을 만들어야 할 수 있습니다.
- UI 하네스는 예제를 빌드·설치하고 별도 Xcode 프로젝트에서 테스트합니다. 결과·앱 로그·주기적인 캡처는 `build/ui-tests/<UDID>/`에 남습니다.
- 웹 테스트는 Node.js가 아닌 헤드리스 브라우저에서 실행합니다. 웹 의존성을 바꾸면 `./gradlew kotlinUpgradeYarnLock kotlinWasmUpgradeYarnLock`을 실행합니다.
- 오래 사용한 시뮬레이터에서 복원 테스트가 `RESTORE none`으로 실패하면 scene 세션 로그를 확인하고 지원 버전의 새 시뮬레이터에서 재검증합니다. 저장소에 시뮬레이터 scene 상태 실패 기록이 있습니다. 검색 테스트도 시뮬레이터 키보드에 따른 실패 기록이 있으므로 [검증 이력](AGENTS.md)을 확인하세요.

<a id="프로젝트-구조"></a>
<a id="structure"></a>

## 프로젝트 구조

```text
.
├── library/
├── example/
│   ├── shared/
│   ├── androidApp/
│   ├── desktopApp/
│   ├── iosApp/
│   ├── iosUITests/
│   └── run-sample.sh
├── gradle/libs.versions.toml
└── .github/workflows/
```

`library`는 배포할 KMP 모듈입니다. `example/shared`에 공통 앱과 정적 `Shared` 프레임워크가 있고 다른 앱 폴더는 플랫폼 진입점입니다. `iosApp`과 `iosUITests`는 Gradle 모듈이 아닌 Xcode 프로젝트입니다. `gradle.yml`은 CI, `publish.yml`은 GitHub Release 생성 시 라이브러리를 Maven Central에 배포하는 워크플로입니다.

<a id="기여"></a>
<a id="contributing"></a>

## 기여와 참고 자료

프로젝트 목표, 설계 결정, 기여 지침, 검증 이력은 [AGENTS.md](AGENTS.md)를 참고하세요. 라이브러리 코드는 `library`, 예제·검증 전용 코드는 `example`에 둡니다.

관련 공식 자료는 다음과 같습니다.

- [JetBrains: Compose Multiplatform 앱의 Liquid Glass](https://kotlinlang.org/docs/multiplatform/ios-liquid-glass.html)
- [KotlinConf 앱 `lg-nav` 브랜치](https://github.com/JetBrains/kotlinconf-app/tree/lg-nav)
- [KotlinConf 네이티브 내비게이션 변경 비교](https://github.com/JetBrains/kotlinconf-app/compare/main...lg-nav)

참고 자료이며 의존성이나 동일한 아키텍처는 아닙니다. 이 라이브러리는 Kotlin/Native에서 UIKit을 제어하고 Kotlin 백스택을 기준으로 삼으며, 지원하는 모든 iOS 버전에서 네이티브 컨트롤러를 사용합니다.

<a id="라이선스"></a>
<a id="license"></a>

## 라이선스

[Apache License 2.0](LICENSE)
