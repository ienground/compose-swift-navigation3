import SwiftUI

/// Test runner host only. The UI tests drive the example app (installed separately) by bundle id.
@main
struct HostApp: App {
    var body: some Scene {
        WindowGroup { Text("UI test host") }
    }
}
