import SwiftUI

struct ContentView: View {
    @State private var viewModel = GreetingViewModel()

    var body: some View {
        VStack(spacing: 20) {
            Image(systemName: "newspaper")
                .font(.system(size: 48))
                .foregroundStyle(.tint)
                .accessibilityHidden(true)

            Text("KMPNews")
                .font(.largeTitle.bold())

            Button(
                viewModel.isGreetingVisible ? LocalizedStringKey("greeting.hide") : LocalizedStringKey("greeting.show")
            ) {
                viewModel.toggleGreeting()
            }
            .buttonStyle(.borderedProminent)
            .accessibilityIdentifier("toggleGreeting")

            Text(viewModel.isGreetingVisible ? viewModel.greeting : " ")
                .multilineTextAlignment(.center)
                .textSelection(.enabled)
                .accessibilityIdentifier("greeting")
        }
        .padding(32)
        .frame(minWidth: 400, minHeight: 300)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

#Preview {
    ContentView()
}
