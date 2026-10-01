import Foundation

@MainActor
private final class RequestGate {
    var calls = 0
    var continuation: CheckedContinuation<Void, Never>?
    func pause(_: Duration) async {
        calls += 1
        await withCheckedContinuation { continuation = $0 }
    }
    func finish() { continuation?.resume(); continuation = nil }
}

@main
struct ComponentCatalogTests {
    @MainActor static func main() async {
        var date = Date(timeIntervalSince1970: 1000)
        let gate = RequestGate()
        let model = CatalogAuthViewModel(now: { date }, pause: { await gate.pause($0) })
        model.setEmail("reader@example.test")
        model.navigate(.register)
        model.setPassword("fixture-password")
        model.setRepeatedPassword("fixture-password")
        model.submit()
        model.submit()
        await wait { gate.calls == 1 }
        precondition(model.busy)
        gate.finish()
        await wait { model.step == .confirm }
        precondition(model.password.isEmpty && model.repeatedPassword.isEmpty)
        precondition(model.resendSeconds == 60)
        model.resend()
        precondition(gate.calls == 1)
        model.setCode("012345")
        precondition(model.code == "012345")
        model.setOutcome(.error)
        model.submit()
        await wait { gate.calls == 2 }
        gate.finish()
        await wait { !model.busy }
        precondition(model.message == "kit.codeError" && !model.completed)
        model.setPath([.register])
        precondition(model.email == "reader@example.test" && model.code.isEmpty)
        date = date.addingTimeInterval(20)
        model.navigate(.confirm)
        precondition(model.resendSeconds == 40)
        date = date.addingTimeInterval(40)
        model.updateCountdown()
        model.setOutcome(.success)
        model.resend()
        await wait { gate.calls == 3 }
        gate.finish()
        await wait { !model.busy }
        precondition(model.resendSeconds == 60)
        model.setCode("012345")
        model.submit()
        await wait { gate.calls == 4 }
        gate.finish()
        await wait { model.completed }
        precondition(model.code.isEmpty)
        model.close()
        precondition(model.email.isEmpty && model.path.isEmpty && !model.completed)

        model.setEmail("reader@example.test")
        model.navigate(.recovery)
        model.submit()
        await wait { gate.calls == 5 }
        gate.finish()
        await wait { !model.busy }
        precondition(model.message == "kit.recoverySent")
        model.setPath([])
        model.setPassword("secret")
        model.submit()
        await wait { gate.calls == 6 }
        model.close()
        gate.finish()
        for _ in 0..<20 { await Task.yield() }
        precondition(!model.completed && !model.busy && model.password.isEmpty)

        model.setEmail("reader@example.test")
        model.navigate(.register)
        model.setPassword("secret")
        model.setRepeatedPassword("secret")
        model.submit()
        await wait { gate.calls == 7 }
        model.setPath([])
        gate.finish()
        for _ in 0..<20 { await Task.yield() }
        precondition(model.path.isEmpty && model.password.isEmpty && !model.busy)
        model.close()
        print(
            "PASS: catalog registration, confirmation, errors, resend, recovery, duplicate submission, back and close cancellation"
        )
    }

    @MainActor private static func wait(_ predicate: () -> Bool) async {
        for _ in 0..<1000 {
            if predicate() { return }
            await Task.yield()
        }
        preconditionFailure("Presentation transition did not complete")
    }
}
