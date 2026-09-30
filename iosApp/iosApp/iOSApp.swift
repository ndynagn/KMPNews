import SwiftUI

@main
struct iOSApp: App {
    @State private var homeViewModel = HomeViewModel()

    var body: some Scene {
        WindowGroup {
            Group {
                if let dependencies = homeViewModel.dependencies {
                    ContentView(dependencies: dependencies, isConfigured: !homeViewModel.apiKey.isEmpty)
                } else if homeViewModel.storageFailed {
                    VStack {
                        Text("feed.storageError")
                        Button("feed.retry") { homeViewModel.prepare() }
                    }
                } else {
                    ProgressView().task { homeViewModel.prepare() }
                }
            }
        }
    }
}
