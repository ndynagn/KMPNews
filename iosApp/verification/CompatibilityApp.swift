import Foundation
import KotlinModules
import SwiftUI

/// Built only in the disposable probe checkout; results are stored in its separate app container.
@main
struct CompatibilityApp: App {
    var body: some Scene {
        WindowGroup {
            Text("Compatibility probe")
                .task {
                    await verifyCompatibility()
                }
        }
    }

    @MainActor
    private func verifyCompatibility() async {
        let directory = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        let result: String
        do {
            let database = directory.appendingPathComponent("probe-\(UUID().uuidString).db")
            guard let server = ProcessInfo.processInfo.environment["KMPNEWS_FIXTURE_SERVER"] else {
                throw ProbeFailure.missingServer
            }
            let probe = ProbeFactoryKt.createCompatibilityProbe()
            let libraries = try await probe.verify(databasePath: database.path, serverUrl: server)

            let interop = InteropProbe()
            for _ in 0..<2 {
                var values: [String] = []
                for try await value in interop.values() { values.append(value) }
                guard values == ["value", "expected-error"] else { throw ProbeFailure.flowValues }
            }
            guard try await interop.value() == "value",
                try await interop.expectedFailure() == "expected-error"
            else { throw ProbeFailure.suspendValues }

            let waiting = Task { try await interop.waitingValue() }
            try await waitForJob(interop)
            waiting.cancel()
            do {
                _ = try await waiting.value
                throw ProbeFailure.unexpectedSuspendSuccess
            } catch is CancellationError {}
            try await waitForIdle(interop)

            let observer = Task {
                for try await _ in interop.waitingValues() {}
            }
            try await waitForJob(interop)
            observer.cancel()
            do {
                try await observer.value
            } catch is CancellationError {}
            try await waitForIdle(interop)

            result = libraries + "; PASS: Swift Flow/resubscription/suspend/errors/cancellation"
        } catch {
            result = "FAIL: \(error)"
        }

        do {
            try result.write(
                to: directory.appendingPathComponent("compatibility.txt"), atomically: true, encoding: .utf8)
        } catch { print("Cannot record compatibility result: \(error)") }
    }

    @MainActor
    private func waitForJob(_ probe: InteropProbe) async throws {
        for _ in 0..<100 {
            if probe.activeJobs > 0 { return }
            try await Task.sleep(nanoseconds: 20_000_000)
        }
        throw ProbeFailure.jobStartTimeout
    }

    @MainActor
    private func waitForIdle(_ probe: InteropProbe) async throws {
        for _ in 0..<100 {
            if probe.activeJobs == 0 { return }
            try await Task.sleep(nanoseconds: 20_000_000)
        }
        throw ProbeFailure.jobCancellationTimeout
    }
}

private enum ProbeFailure: String, Error, CustomStringConvertible {
    case missingServer
    case flowValues
    case suspendValues
    case unexpectedSuspendSuccess
    case jobStartTimeout
    case jobCancellationTimeout

    var description: String { rawValue }
}
