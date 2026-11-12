# compose-swift-navigation3

A Kotlin Multiplatform library that lets Compose Multiplatform apps built on Navigation 3 use the
**real iOS navigation controllers** (`UINavigationController`, `UITabBarController`) on iOS, while
Android keeps its Compose Navigation 3 and Material 3 navigation unchanged.

> **Status:** early development. Native stacks and tabs synced with Navigation 3 back stacks,
> and the scroll bridge for the iOS navigation bar, work and are verified on iOS 27.2 and 18.5
> simulators. Not yet available: bar button / toolbar API, iOS state restoration after process death.

## Navigation 3 on iOS

Routes, back stacks, the `entryProvider` and the screens stay in common code. Android keeps
using `NavDisplay`; iOS shows the same back stacks with `UITabBarController` /
`UINavigationController`:

```kotlin
// commonMain: screen chrome lives in entry metadata
val entryProvider = entryProvider<NavKey> {
    entry<Home>(metadata = nativeScreen(title = "Home", largeTitle = true)) { HomeScreen() }
    entry<Detail>(metadata = { key -> nativeScreen(title = "Detail ${key.id}", hidesTabBar = true) }) { DetailScreen(it.id) }
}

// iosMain
fun MainViewController() = NativeNavigationViewController {
    val home = rememberNavBackStack(config, Home)
    val settings = rememberNavBackStack(config, Settings)
    var selected by rememberSaveable { mutableIntStateOf(0) }
    NativeTabs(
        tabs = listOf(NativeTab("Home", "house", home), NativeTab("Settings", "gearshape", settings)),
        selectedTab = selected,
        onTabSelected = { selected = it },
        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
        entryProvider = entryProvider,
    )
}
```

- The Kotlin back stack is the source of truth: adding/removing/replacing keys pushes, pops or
  sets the native stack (deep links included). Native back button, edge swipe, back menu and tab
  re-tap call `onBack()` once per popped screen; a cancelled swipe changes nothing.
- Each entry is its own native screen, so decorators (saveable state, per-entry ViewModels) keep
  Navigation 3 semantics, including clean-up when an entry is popped.
- Use `NativeStack(backStack, ...)` for a single stack without tabs.
- The content block of `NativeNavigationViewController` holds navigation state only; it must not draw UI.

## Scroll bridge (iOS navigation bar ⇄ Compose list)

UIKit only collapses a large title, switches the bar to its scrolled appearance and applies the
iOS 26 scroll edge effect for a `UIScrollView` that the user actually pans. Compose has no such
scroll view, so this library puts one behind each screen: it owns the vertical gesture and
physics, and the Compose list follows it.

Shared screen code:

```kotlin
val listState = rememberLazyListState()
val nativeScroll = rememberNativeScroll(listState) // also accepts a ScrollState

LazyColumn(
    state = listState,
    userScrollEnabled = nativeScroll.userScrollEnabled,
    contentPadding = nativeScroll.contentPadding,
) { /* ... */ }
```

On Android, desktop and web this is a no-op (`userScrollEnabled = true`, zero padding).
Screens that do not scroll use `nativeContentPadding()` instead.

iOS (`iosMain`), hosting the screen in a native navigation controller:

```kotlin
val screen = ComposeScreenViewController { MyScreen() }
screen.title = "Title"
val navigation = UINavigationController(rootViewController = screen).apply {
    navigationBar.prefersLargeTitles = true
}
```

Known limitations (see `AGENTS.md`, section 6.5): one connected vertical scroll container per
screen; Compose gestures that drag vertically conflict with the native pan; no native scroll
indicator for lazy lists; `LazyGridState` is not supported yet.

## Platforms

Android, JVM (desktop), iOS (`iosArm64`, `iosSimulatorArm64`, iOS 18.2+), JS and Wasm (browser).

## Project structure

```
.
├── library/              # The published library (zone.ien.navigation:compose-swift-navigation3)
└── example/
    ├── shared/           # Shared Compose UI of the example app (depends on :library)
    ├── androidApp/       # Android entry point
    ├── desktopApp/       # Desktop (JVM) entry point
    └── iosApp/           # iOS entry point (Xcode project, imports the `Shared` framework)
```

### Running the examples

* **Android**: `./gradlew :example:androidApp:installDebug`
* **Desktop**: `./gradlew :example:desktopApp:run`
* **iOS**: open `example/iosApp/iosApp.xcodeproj` in Xcode and run. The build phase calls `./gradlew :example:shared:embedAndSignAppleFrameworkForXcode`.

### Running the tests

* `./gradlew jvmTest testAndroidHostTest iosSimulatorArm64Test jsTest wasmJsTest`
* Web tests run in a headless browser. When web dependencies change, run `./gradlew kotlinUpgradeYarnLock kotlinWasmUpgradeYarnLock`.

### JDK

The Gradle daemon JVM is pinned in `gradle/gradle-daemon-jvm.properties` (JetBrains Runtime 25), so `./gradlew` behaves the same regardless of `JAVA_HOME`. Sources are compiled with a JDK 17 toolchain (`jvmToolchain(17)`), auto-provisioned through the foojay resolver when missing.

## Contributing

Read [`AGENTS.md`](AGENTS.md) first: it holds the goals, the architecture decisions and how changes are verified.

## License

[Apache License 2.0](LICENSE)
