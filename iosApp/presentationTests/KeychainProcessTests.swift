import Foundation
import KotlinModules

/// Runs twice under a separate Simulator app identity; never reads or clears the production service.
@main
struct KeychainProcessTests {
    static func main() {
        guard let identity = Bundle.main.bundleIdentifier, identity.contains(".verification.") else {
            preconditionFailure("An isolated verification identity is required")
        }

        let storage = KeychainAuthStorage(service: identity + ".auth")
        let value = "non-sensitive-session-fixture"
        let writePhase = ProcessInfo.processInfo.arguments.contains("write")

        if writePhase {
            precondition(storage.write(value: value))
            precondition(storage.read().value == value)
        } else {
            precondition(!storage.read().failed)
            precondition(storage.read().value == value)
            precondition(storage.write(value: nil))
            precondition(storage.read().value == nil)
        }
        print("PASS: Keychain process phase \(writePhase ? "write" : "read")")
    }
}
