import SwiftUI

@main
struct iOSApp: App {
    @State private var services = AppServices()

    var body: some Scene {
        WindowGroup {
            #if DEBUG
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
