import Foundation
import KotlinModules

@main
struct NewsEngineInteropSmoke {
    @MainActor static func main() async throws {
        let arguments = ProcessInfo.processInfo.arguments
        guard let index = arguments.firstIndex(of: "--news-port"), index + 1 < arguments.count,
            let port = Int32(arguments[index + 1])
        else { preconditionFailure("An explicit loopback server port is required") }

        try await NewsEngineInteropFixture(port: port).verify()

        print("PASS: real Darwin mediator JSON, failures, cancellation and close")
    }
}
