import KotlinModules
import SwiftUI

@main
struct KMPNewsApp: App {
    private let services = AppServices()

    var body: some Scene {
        WindowGroup {
            #if FEATURE_VERIFICATION
                if ProcessInfo.processInfo.arguments.contains("--search-navigation-prototype") {
                    SearchNavigationPrototype()
                } else {
                    ContentView(services: services)
                }
            #else
                ContentView(services: services)
            #endif
        }
    }
}
