import UIKit
import SwiftUI
import Shared

/// Keeps the Kotlin root controller so its navigation state can be saved.
final class NavigationHolder {
    var controller: UIViewController?
}

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
    /// Scene-scoped state restoration: iOS restores it after terminating the app in the
    /// background and discards it when the user force-quits the app.
    @SceneStorage("navigationState") private var navigationState: Data?
    @Environment(\.scenePhase) private var scenePhase
    @State private var holder = NavigationHolder()

    var body: some View {
        ComposeView(restoredState: navigationState, holder: holder)
            .ignoresSafeArea()
            .onChange(of: scenePhase) { _, phase in
                if ProcessInfo.processInfo.arguments.contains("-traceNav") {
                    NSLog("[ScrollDemo \(Int64(Date().timeIntervalSince1970 * 1000))] SCENE \(String(describing: phase)) restored=\(navigationState?.count ?? 0)")
                }
                // Save before the scene's state is captured: .inactive comes first.
                if phase != .active, let controller = holder.controller {
                    navigationState = MainViewControllerKt.saveNavigationState(controller: controller)
                }
            }
    }
}
