import Foundation
import SharedLogic
import SwiftUI

/// Standalone simulator harness; never included in the application target or its bundle.
@main
struct KeychainVerificationApp: App {
    @State private var result = "Running"

    var body: some Scene {
        WindowGroup {
            Text(result).task { verify() }
        }
    }

    @MainActor
    private func verify() {
        let storage = KeychainAuthStorage()
        let defaults = UserDefaults.standard
        let value = "non-sensitive-session-fixture"
        let stage = defaults.integer(forKey: "phase")
        let success: Bool
        if stage == 0 {
            success = storage.write(value: value) && storage.read().value == value
            if success { defaults.set(1, forKey: "phase") }
        } else {
            success =
                storage.read().value == value && storage.write(value: nil)
                && storage.read().value == nil && !storage.read().failed
        }
        result = success ? "PASS phase \(stage)" : "FAIL phase \(stage)"
        do {
            let directory = try FileManager.default.url(
                for: .documentDirectory, in: .userDomainMask, appropriateFor: nil, create: true)
            try result.write(to: directory.appendingPathComponent("result.txt"), atomically: true, encoding: .utf8)
        } catch {
            result = "FAIL writing result"
        }
    }
}
