import SwiftUI

@main
struct iOSApp: App {
    @State private var homeViewModel = HomeViewModel()

    var body: some Scene {
        WindowGroup {
            ContentView(homeViewModel: homeViewModel)
        }
    }
}
