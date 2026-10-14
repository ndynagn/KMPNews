import KotlinModules
import SwiftUI

struct ContentView: View {
    @State private var showGreeting = false

    var body: some View {
        VStack(spacing: 24) {
            Button("Click me!") {
                withAnimation {
                    showGreeting.toggle()
                }
            }

            if showGreeting {
                Text(Greeting().greet())
            }
        }
        .padding()
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

@main
struct KMPNewsApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
