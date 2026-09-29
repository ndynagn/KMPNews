import Observation
import SharedLogic

@MainActor
@Observable
final class GreetingViewModel {
    let greeting = Greeting().greet()
    private(set) var isGreetingVisible = true

    func toggleGreeting() {
        isGreetingVisible.toggle()
    }
}
