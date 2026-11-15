# compose-swift-navigation3

[English](README.md) | [한국어](README_ko.md)

Native UIKit navigation for Compose Multiplatform apps built with **Navigation 3**.
On iOS, the library connects your Kotlin back stacks to real `UITabBarController` and `UINavigationController` instances. Android, desktop, and web run your existing `NavDisplay` and app UI through the `otherPlatforms` block.

Routes, back stacks, entry providers, screens, ViewModels, and the root `App()` stay in common code. The library is implemented in Kotlin; it requires no Swift navigation layer.

> The first prerelease version is `1.0.0-alpha01`. Until it is published to Maven Central, use the source or local Maven installation below.

<a id="toc"></a>

## Table of contents

- [Features](#features)
- [Installation](#installation)
- [Quick start](#quick-start)
- [Migrating an existing Navigation 3 app](#migration)
- [API](#api)
- [Compatibility and limitations](#compatibility)
- [Example apps](#examples)
- [Build and test](#testing)
- [Project structure](#structure)
- [Contributing and references](#contributing)
- [License](#license)

<a id="전체-구조"></a>
<a id="앱이-하지-않아도-되는-것"></a>
<a id="features"></a>

## Features

- Native push/pop transitions, back buttons, cancellable edge-swipe back, back-button history menus, and tab reselection.
- Independent tab stacks with retained screen state; shared screens can be pushed above any selected tab.
- Navigation bar titles, collapsing large titles, bar buttons, menus, bottom toolbars, and `UISearchController` search.
- Vertical scrolling backed by UIKit physics, bounce, scroll-to-top, and keyboard insets.
- Navigation 3 decorators for saveable state and per-entry ViewModels; optional iOS scene restoration.
- Root switching between `NativeStack` and `NativeTabs`, such as sign-in and main flows.
- System Liquid Glass on iOS 26+ when built with iOS 26 SDK or later, unless the app opts out with `UIDesignRequiresCompatibility`. Earlier supported iOS versions use their native system appearance.

Kotlin owns the back stack. Native pops call the app's back callback once per removed entry; cancelled gestures leave the Kotlin stack unchanged. A new Kotlin back stack received during a transition takes precedence; the earlier native pop is not applied again to that new stack.

<a id="설치"></a>
<a id="installation"></a>

## Installation

Run from this repository root to publish the current source to your local Maven repository:

```bash
./gradlew :library:publishToMavenLocal
```

In the consuming app, add `mavenLocal()` to its dependency repositories before `mavenCentral()`, keeping `google()`. Add the library to the shared module:

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
            implementation("zone.ien.navigation:navigation:1.0.0-alpha01")
        }
    }
}
```

Alternatively, include the existing `library` module in your build and use a project dependency, as the [example module](example/shared/build.gradle.kts) does.

Once published to Maven Central, the same dependency works with `google()` and `mavenCentral()` alone, without `mavenLocal()`.

### Automated publishing

The [publishing workflow](.github/workflows/publish.yml) runs when a GitHub Release or prerelease is published. Its tag must match `lib-version-name` in `gradle/libs.versions.toml` (first tag: `v1.0.0-alpha01`). After the full CI passes for the same release commit, it signs all library targets, uploads them to Maven Central, and automatically releases them.

Configure the same Actions Secrets as compose-hig: `SONATYPE_USERNAME`, `SONATYPE_PASSWORD` (Central Portal token), `GPG_KEY` (ASCII-armored private key), and `GPG_PASSWORD` (key passphrase). The Central Portal account must have permission to publish `zone.ien.navigation`, and the signing public key must be available on a key server. The workflow does not print secret values.

Your app also needs:

- The Kotlin serialization plugin for `@Serializable` routes, matching the app's Kotlin version.
- `org.jetbrains.androidx.navigation3:navigation3-ui:1.2.0` for `NavDisplay` outside iOS.
- Compose Material 3 if your app uses the Material UI shown below.
- `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-navigation3:2.11.0` if you need per-entry ViewModels.

The library exposes `androidx.navigation3:navigation3-runtime:1.2.0` transitively and does not depend on `navigation3-ui` or Material 3. See the [version catalog](gradle/libs.versions.toml) and [compatibility](#compatibility) for the repository's toolchain.

<a id="3단계-navdisplay를-nativestack으로-감쌉니다"></a>
<a id="4단계-ios-진입점을-한-줄로-바꿉니다"></a>
<a id="quick-start"></a>

## Quick start

After installation, put this minimal stack in `commonMain`. Imports are included; enable the serialization plugin first. Register `NavKey` subtypes so non-Android targets can save the back stack.

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

Android calls `setContent { App() }`; desktop calls `App()` inside its window. In `iosMain`, expose the native root:

```kotlin
import platform.UIKit.UIViewController
import zone.ien.navigation.NativeNavigationViewController

fun MainViewController(): UIViewController = NativeNavigationViewController { App() }
```

Embed it from SwiftUI as below, with `import SwiftUI`, `import UIKit`, and your Kotlin framework import (`Shared` in the example). Keep the existing `UIViewControllerRepresentable` if you already have one.

```swift
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
// ComposeView().ignoresSafeArea()
```

A UIKit app can assign the result to `window.rootViewController`. Tap **Open detail**, then go back. iOS uses the system navigation controller; Android/desktop use the Material bar above. To try the complete repository example, run `example/run-sample.sh ios` or `example/run-sample.sh android` from the repository root.

<a id="도입-체크리스트"></a>
<a id="반드시-할-일"></a>
<a id="앱-구조에-따라-할-일"></a>
<a id="마지막-확인"></a>
<a id="기존-navigation-3-앱에서-옮기기"></a>
<a id="한눈에-보기"></a>
<a id="2단계-화면에서-탑바를-빼고-선언으로-바꿉니다"></a>
<a id="옮긴-뒤-확인할-것"></a>
<a id="migration"></a>

## Migrating an existing Navigation 3 app

Keep your routes, navigator, back stacks, entry provider, ViewModels, and screen back handlers. Make these scoped changes:

| Existing code | Change |
| --- | --- |
| `NavDisplay(backStack, entryProvider, entryDecorators, ...)` | Wrap it with `NativeStack` or `NativeTabs`; pass the decorated `entries` and `onBack` from its receiver to `NavDisplay` |
| Material `Scaffold`, top and bottom bars | Keep them in `otherPlatforms`; render `title`, bar items, search, and tabs from `NativeNavigationScope` |
| Screen titles | Declare static titles in `nativeScreen`; dynamic titles in `NativeBarItems` |
| Per-screen `TopAppBar` and back button | Move to the app chrome, or hide existing screen chrome with `if (!LocalNativeScreen.current)` |
| Theme, DI, root scaffold, snackbar host | Put DI locals in `NativeNavigationFrame.providers`; theme and screen UI wrappers in `frame` |
| Screen `innerPadding` | Use `rememberNativeScroll(state)` for scrolling screens or `nativeContentPadding()` otherwise |
| Outer stack over the tabs | Pass its detail entries to `NativeTabs(sharedBackStack = ...)`, excluding the entry that represented the tabs |
| iOS `ComposeUIViewController { App() }` | Use `NativeNavigationViewController { App() }`; embed with `.ignoresSafeArea()` |

Check Android behavior after moving the chrome. On iOS, test push, native back, edge swipe and cancellation, large-title scrolling, tab state retention, shared screens, and optional restoration on both iOS 18.x (18.2+) and iOS 26+.

<a id="지켜야-할-규칙"></a>
<a id="테마와-앱-전역-compositionlocal"></a>
<a id="composition"></a>

### Themes, locals, and state ownership

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

This fragment uses your existing `LocalAppGraph`, `graph`, `AppTheme`, and `RootScaffold`.

- Outside iOS, `providers { frame { content() } }` wraps the app once. On iOS, every entry has its own controller and composition: `providers` wraps both the UI-free navigation host and each screen; `frame` wraps each screen.
- Emit no UI in `providers` or outside `frame`/the navigation declaration's `otherPlatforms` block. The iOS host rejects UI nodes. Move app-level dialogs into screens, or use an implementation backed by a system alert on iOS.
- The host supplies lifecycle, ViewModel, saved-state owners, `LocalUIViewController`, and `LocalDensity`, so shared resource lookups and native alert integrations can work there. UI-dependent themes belong in `frame`.
- Read `LocalNativeScreen.current` inside screen content/frame to hide Compose chrome and apply `imePadding()` only outside native screens. Do not add system-bar insets again; native content padding already includes them.
- Native bars follow the system light/dark appearance, not a forced Compose theme. Use a system-following theme to keep them aligned. You may also wrap entry content with an existing `NavEntryDecorator` instead of `NativeNavigationFrame`.
- Entry providers can retain captured ordinary values because entries are recreated on back-stack changes. Read changing values through State, `rememberUpdatedState`, or a ViewModel.
- State holders should own the `MutableState` returned by `rememberSaveable` directly. Copying to a saved value in `SideEffect` misses changes when the caller does not recompose.

<a id="api"></a>

## API

Public APIs live in `zone.ien.navigation`. The [common source](library/src/commonMain/kotlin/zone/ien/navigation) and [iOS source](library/src/iosMain/kotlin/zone/ien/navigation) contain their declarations and KDoc.

| API | Purpose |
| --- | --- |
| `NativeStack`, `NativeTabs`, `NativeTab` | Common declarations for a stack, tabs, and each tab's stack |
| `NativeNavigationScope<T>` | Decorated entries and chrome data passed to `otherPlatforms` |
| `NativeNavigationFrame`, `LocalNativeScreen` | App-wide providers/frame and native-screen detection |
| `nativeScreen`, `NavEntry.nativeTitle`, `nativeLargeTitle`, `nativeHidesTabBar`, `nativeTitleImage` | Entry metadata and accessors |
| `rememberNativeScroll`, `NativeScroll`, `nativeContentPadding`, `Modifier.nativeScrollExclusion()` | Native vertical scroll and insets |
| `NativeBarItems`, `NativeBarButton`, `NativeMenuAction`, `NativeBarContent` | Bar declarations and data |
| `NativeSearchBar`, `NativeSearch` | Search declaration and data |
| `NativeBarState`, `ProvideNativeBarState` | Collect declarations around a plain `NavDisplay` without `NativeStack`/`NativeTabs` |
| `NativeNavigationViewController`, `saveNativeNavigationState` | iOS navigation host and optional state serialization |
| `ComposeScreenViewController` | iOS standalone Compose screen for an app-owned `UINavigationController` |

<a id="1-라우트와-백스택-공통"></a>
<a id="5-ios"></a>
<a id="탭-위의-공유-화면"></a>
<a id="딥링크"></a>
<a id="stack"></a>

### Stacks, tabs, and shared screens

Use observable back stacks (`rememberNavBackStack` or a snapshot state list). Navigation is ordinary list mutation:

```kotlin
backStack.add(Detail(3))
backStack.removeLastOrNull()
backStack[backStack.lastIndex] = Detail(103)
while (backStack.size > 1) backStack.removeLastOrNull()
```

These are push, pop, replace, and pop-to-root respectively. For tabs, register every tab/shared route in your provider and serializer configuration, then declare:

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

This fragment extends the quick start with your `Settings` route, provider, and `NativeNavigationScope<NavKey>.MaterialChrome()` renderer; see [App.kt](example/shared/src/commonMain/kotlin/zone/ien/utils/example/App.kt) for the complete implementation.

- iOS creates a `UITabBarController` with one `UINavigationController` per tab. Each tab's decorated entries retain saveable state and ViewModels on other platforms too.
- `sharedBackStack` may be empty. Its screens always hide the tab bar and push above the selected tab. `onSharedBack` removes one shared entry. If the app changes `selectedTab` while a shared screen is visible, the shared screens move to the new tab without animation.
- `NativeStack` and `NativeTab` default `onBack` to removing the last entry when the supplied list is mutable. Custom callbacks, including `onSharedBack`, must pop exactly one entry. Otherwise UIKit and Kotlin diverge, and a subsequent stack change can push the screen again.
- `NativeStack`/`NativeTabs` default to the saveable-state decorator. Add `rememberViewModelStoreNavEntryDecorator()` when using per-entry ViewModels. Do not redecorate the `entries` passed to `NavDisplay`.
- Declare one root at a time. Switch between `NativeTabs` and `NativeStack` with `if (signedIn)`. iOS cross-fades between hierarchies and disposes the old screens; creating decorators inside each branch clears the departing flow's ViewModels and saveable state.
- Deep links are parsed by your app. Select the tab and replace/extend its Kotlin stack with all destinations; controllers below the top are created when first shown.

<a id="4-android와-desktop"></a>
<a id="scope"></a>

### Rendering on Android, desktop, and web

The library draws no UI on these platforms. Read these `NativeNavigationScope` values in the block and render your existing components:

| Value | Meaning |
| --- | --- |
| `entries` | Selected tab/stack entries followed by shared entries, already decorated |
| `tabEntries`, `sharedEntries` | The two lists separately, for an outer `NavDisplay` above the tabs |
| `onBack`, `canGoBack` | Pop the visible stack, and whether another entry is below the top |
| `title`, `largeTitle`, `titleImage` | Dynamic title or metadata fallback, large-title flag, and optional painter lambda |
| `leading`, `trailing`, `toolbar`, `search` | Collected bar and search declarations |
| `tabs`, `selectedTab`, `onTabSelected` | Tab configuration and selection; no tabs for `NativeStack` |
| `tabBarVisible` | Tabs exist, no shared screen is shown, and no pushed tab entry hides the bar |

For an outer stack that covers the tab chrome, use `listOf(NavEntry<NavKey>(TabsKey) { TabsChrome(tabEntries) }) + sharedEntries` as its entries. `TabsKey` and `TabsChrome` belong to your app.

The most recently registered, still-composed screen's bar/search declarations win. An outgoing screen's declarations can remain temporarily when the incoming screen declares none. `systemImage` names are iOS SF Symbols; choose Android icons in your renderer. Menus, toolbars, and search rendering are demonstrated in [App.kt](example/shared/src/commonMain/kotlin/zone/ien/utils/example/App.kt).

<a id="1단계-엔트리에-화면-정보를-더합니다"></a>
<a id="2-엔트리와-화면-메타데이터-공통"></a>
<a id="metadata"></a>

### Screen metadata and title images

```kotlin
entry<Detail>(metadata = { key -> nativeScreen(title = "Detail ${key.id}", hidesTabBar = true) }) { key ->
    DetailScreen(key.id)
}
```

`DetailScreen` is your screen composable.

| Parameter | Default | iOS behavior |
| --- | --- | --- |
| `title` | `null` | `navigationItem.title`; set static titles before push starts |
| `largeTitle` | `false` | A large title that collapses with scrolling; prefer it only on stack roots |
| `hidesTabBar` | `false` | `hidesBottomBarWhenPushed`; screens pushed above it also keep the bar hidden |
| `titleImage` | `null` | A `@Composable () -> Painter` rendered as a 28pt-high `titleView` with its aspect ratio |

For a logo, use `nativeScreen(title = "Home", titleImage = { painterResource(Res.drawable.logo) })` with your existing Compose resource. Keep `title` for VoiceOver and the next screen's back button. On iOS the painter runs in the host before push, outside the screen's theme; screen theme colors and dark-mode resources are not applied. Other platforms render `titleImage` themselves, for example `Image(image(), contentDescription = title, modifier = Modifier.height(28.dp))` after checking the lambda is non-null. Title images are an experimental feature in this project.

<a id="3-화면-작성-공통"></a>
<a id="스크롤하는-화면"></a>
<a id="스크롤하지-않는-화면"></a>
<a id="scroll"></a>

### Scrolling and content padding

Connect the screen's primary vertical container. UIKit owns pan, deceleration, bounce, scroll-to-top, and keyboard insets; Compose follows its offset. Pass both returned values to the container:

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

- The overloads accept `LazyListState`, `LazyGridState`, `LazyStaggeredGridState`, or `ScrollState`. Use the same `userScrollEnabled`/`contentPadding` wiring for vertical grids.
- On iOS, disable the container's user scrolling with the returned flag. Otherwise Compose captures the drag and the large title may fail to expand again. Containers that cannot disable their own scrolling need to be replaced or configured with a controllable outer container.
- Outside native screens, the flag is `true` and padding is zero. Your existing Scaffold still supplies its usual insets.
- `background` fills the area exposed by overscroll; omitting it uses iOS `systemBackgroundColor`.
- Connect one vertical container per screen. Leave horizontal lists/grids unconnected. Use `Modifier.nativeScrollExclusion()` on vertical sliders or reorder handles so drags starting there go to Compose.
- A non-scrolling screen uses `Modifier.padding(nativeContentPadding())`. Native bar insets are already included; avoid duplicate `WindowInsets` or native-screen `imePadding()`.
- Programmatic Compose scrolling, such as `animateScrollToItem` or `bringIntoView`, is synchronized back to the native bar.

<a id="바-버튼-메뉴-툴바"></a>
<a id="검색"></a>
<a id="bars"></a>

### Bar items, menus, and search

Declare actions inside screen content so they can read the screen's state and ViewModel:

```kotlin
NativeBarItems(
    title = if (count > 0) "Detail $id ($count)" else null,   // Optional: overrides the nativeScreen title
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

- `leading` and `trailing` become `UIBarButtonItem`s in left-to-right order, keeping the native back button. Non-empty `menu` opens a `UIMenu` instead of calling the button's `onClick`; actions support `destructive`.
- `toolbar` appears only while this screen is on top. `enabled` controls button availability; `systemImage` selects an SF Symbol and `title` supplies its accessibility label. Native buttons are recreated only when their appearance changes.
- Screen declarations are registered after content composes, which is already after push starts. Use `nativeScreen(title)` for static titles that must animate with push; reserve `NativeBarItems(title)` for state-dependent updates.

Search keeps the query and filtering in your app:

```kotlin
var query by rememberSaveable { mutableStateOf("") }
NativeSearchBar(query = query, onQueryChange = { query = it }, placeholder = "Search")
```

iOS uses a `UISearchController` below the large title, hides it when scrolling down, and supplies clear/cancel controls. Outside iOS, render `scope.search` yourself. To collect declarations around a plain `NavDisplay`, use `ProvideNativeBarState(state) { ... }` with a remembered `NativeBarState`.

<a id="뒤로가기-핸들러"></a>
<a id="back-handlers"></a>

### Screen back handlers

Existing `NavigationBackHandler`, `NavigationEventHandler`, and legacy `BackHandler` work inside native screens. Enabled back handlers receive the native button and edge swipe instead of popping; forward-only handlers do not block native back.

```kotlin
NavigationBackHandler(
    state = rememberNavigationEventState(NavigationEventInfo.None),
    isBackEnabled = hasUnsavedChanges,
    onBackCompleted = { showDiscardDialog = true },
)
```

Use your existing unsaved-state and dialog state. Back-button history menus that pop multiple screens bypass handlers. Handler-owned swipes receive progress/completion/cancellation but have no UIKit transition animation; the screen must render progress itself.

<a id="6-상태-복원-ios-선택"></a>
<a id="restoration"></a>

### Optional iOS state restoration

The library serializes navigation and entry `rememberSaveable` state (including scroll positions) to `NSData`; the app owns storage. Expose these wrappers from `iosMain`:

```kotlin
fun MainViewController(restoredState: NSData?): UIViewController =
    NativeNavigationViewController(restoredState, stateVersion = 1) { App() }

fun saveNavigationState(controller: UIViewController): NSData? =
    saveNativeNavigationState(controller)
```

Use `platform.Foundation.NSData`, `platform.UIKit.UIViewController`, and the two `zone.ien.navigation` APIs above. Replace the quick start's no-argument Swift bridge with this scene-storage bridge:

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
                // Save on .inactive: iOS records scene state before .background.
                if phase != .active, let controller = holder.controller {
                    navigationState = MainViewControllerKt.saveNavigationState(controller: controller)
                }
            }
    }
}
```

- Save when the scene becomes `.inactive`; saving only at `.background` is too late for scene-state capture. `@SceneStorage` restores after system termination, discards state after user force-quit, and keeps each scene separate. UIKit apps can use `UIScene.stateRestorationActivity`.
- Increment `stateVersion` (default `0`) when route serialization or the number/order of `rememberSaveable`/`rememberNavBackStack` calls changes. Mismatched versions are ignored. A failed initial host restoration retries without saved data, but matching types at the wrong call site and later screen-composition failures are not caught by that safeguard.
- Unsupported saveable values, such as arbitrary objects or enums without a suitable saver, are omitted and logged; those values restart with defaults. ViewModels and per-screen `SavedStateHandle` are not serialized.
- Shared screens use the same restoration mechanism; they always sit over the selected tab.

<a id="요구-사항"></a>
<a id="ios에서-쓰지-않는-것"></a>
<a id="동작-규칙과-한계"></a>
<a id="compatibility"></a>

## Compatibility and limitations

These are the repository's configured versions, not a claim that every version combination is supported:

| Component | Configuration |
| --- | --- |
| Kotlin / Compose Multiplatform | 2.4.21 / 1.12.2 |
| Navigation 3 runtime / UI | 1.2.0 / 1.2.0 |
| Lifecycle / Material 3 (example) | 2.11.0 / 1.12.0-alpha03 |
| AGP / Gradle | 9.3.1 / 9.7.0 |
| Gradle daemon / compilation | JetBrains Runtime 25 / JDK 17 toolchain |
| Android | minSdk 29, compileSdk/targetSdk 37 |
| iOS | 18.2+, `iosArm64` and `iosSimulatorArm64`; Liquid Glass requires an iOS 26+ SDK build |
| Other library targets | `jvm`, browser-only `js` and `wasmJs` |

CI runs JVM, Android host, iOS simulator, JS, and Wasm tests and builds the Android/desktop examples. It does not build the iOS app or run XCUITest. The repository records XCUITest runs on iOS 18.5 and 27.2 and manual Android checks in [AGENTS.md](AGENTS.md); these are prior results, not a guarantee for every current change. The latest recorded title-image run on iOS 27.2 passed 15 UI tests; iOS 18.x was not rerun for that feature.

Known boundaries:

- No runtime OS-version branches. iOS 26-only features such as tab-bar minimization, `bottomAccessory`, explicit glass effects, and search tabs are not exposed.
- Tabs are root-only. Switch root declarations for login/main flows; nested tabs and a navigation modal API are not supported.
- iOS never calls `otherPlatforms`, so its `NavDisplay` transition settings and `sceneStrategy` do not apply. All entries are pushed full screens. Present dialogs and bottom sheets within screen content instead of making them navigation entries.
- Large titles on pushed screens can overlap inline titles during interactive back when the previous large title is collapsed, as in UIKit. Prefer large titles only on stack roots.
- Equal content keys share Navigation 3 state. Duplicate routes work, but are not independent state instances merely because they were pushed twice.
- One vertical scroll container per screen; nested vertical containers are not synchronized. Lazy layouts have no native scroll indicator. Containers must allow user scrolling to be disabled.
- Custom Compose views in native bar buttons, search scopes/tokens, and separate search-results controllers are not supported.
- Physical devices, iPad, VoiceOver, hardware-keyboard back delivery, Compose text-field focus/keyboard interactions, and scroll performance have not been fully verified. Full-screen gesture overlays and fine landscape safe-area behavior also need further validation.

<a id="예제-앱"></a>
<a id="examples"></a>

## Example apps

The [example](example) shares one `App()` across Android, desktop, and iOS. It has Home, Gallery, and Settings tabs; grids and staggered grids; detail screens with and without the tab bar; shared Profile screens; PNG/vector title images; and a sign-out/sign-in root switch.

```bash
example/run-sample.sh
example/run-sample.sh ios
example/run-sample.sh android
./gradlew :example:desktopApp:run
```

Run from the repository root. The script builds, installs, and launches the app. Use `IOS_SIM_UDID` to select an iOS simulator; `ANDROID_SERIAL` selects an Android device, and `ANDROID_AVD` selects an emulator to start if needed. The iOS viewer is DeviceHub on Xcode 27; the script opens the available viewer. There is no web example app.

<a id="테스트"></a>
<a id="testing"></a>

## Build and test

Run these commands from the repository root; iOS tasks require macOS and Xcode. The Gradle daemon uses the pinned JetBrains Runtime 25; compilation uses JDK 17, automatically provisioned when absent.

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

- Pick an available simulator name/OS or UDID. Use a separate simulator for UI tests because they terminate/relaunch the app.
- The `example.iosApp` scheme currently lives in ignored `xcuserdata`; a fresh clone may need a scheme created in Xcode before the command above works.
- The UI harness builds/installs the example and tests it from a separate Xcode project. Results, app logs, and periodic screenshots go to `build/ui-tests/<UDID>/`.
- Web tests run in a headless browser, not Node.js. After changing web dependencies, run `./gradlew kotlinUpgradeYarnLock kotlinWasmUpgradeYarnLock`.
- If restoration tests report `RESTORE none` on a reused simulator, check scene-session logs and retry on a fresh supported simulator; the repository records simulator scene-state failures. Search tests have also shown simulator keyboard-dependent failures; see [the verification history](AGENTS.md).

<a id="프로젝트-구조"></a>
<a id="structure"></a>

## Project structure

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

`library` is the publishable KMP module. `example/shared` owns the common app and the static `Shared` framework; the other app folders are platform entry points. `iosApp` and `iosUITests` are Xcode projects, not Gradle modules. CI is in `gradle.yml`; `publish.yml` publishes the library to Maven Central when a GitHub Release is created.

<a id="기여"></a>
<a id="contributing"></a>

## Contributing and references

Read [AGENTS.md](AGENTS.md) for project goals, architecture decisions, contributor instructions, and verification history. Keep library code in `library` and demo/test-only behavior in `example`.

Related official work:

- [JetBrains: Liquid Glass in a Compose Multiplatform app](https://kotlinlang.org/docs/multiplatform/ios-liquid-glass.html)
- [KotlinConf app, `lg-nav` branch](https://github.com/JetBrains/kotlinconf-app/tree/lg-nav)
- [KotlinConf native-navigation comparison](https://github.com/JetBrains/kotlinconf-app/compare/main...lg-nav)

These are references, not dependencies or an identical architecture: this library uses UIKit from Kotlin/Native, keeps Kotlin back stacks authoritative, and uses native controllers on all supported iOS versions.

<a id="라이선스"></a>
<a id="license"></a>

## License

[Apache License 2.0](LICENSE)
