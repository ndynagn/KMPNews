import Foundation

@MainActor
private final class RequestGate {
    var calls = 0
    var continuation: CheckedContinuation<Void, Never>?

    func pause(_: Duration) async {
        calls += 1
        await withCheckedContinuation { continuation = $0 }
    }

    func finish() {
        continuation?.resume()
        continuation = nil
    }
}

@main
struct ComponentCatalogTests {
    @MainActor
    static func main() async {
        await verifyRegistrationAndConfirmation()
        await verifyRecoveryAndCloseCancellation()
        await verifyBackCancellation()

        print(
            "PASS: catalog registration, confirmation, errors, resend, recovery, "
                + "duplicate submission, back and close cancellation"
        )
    }

    @MainActor
    private static func verifyRegistrationAndConfirmation() async {
        var date = Date(timeIntervalSince1970: 1000)
        let gate = RequestGate()
        let model = CatalogAuthViewModel(initialStep: .register, now: { date }, pause: { await gate.pause($0) })

        model.setEmail("reader@example.test")
        precondition(model.step == .register && model.path.isEmpty)
        model.setPassword("fixture-password")
        model.setRepeatedPassword("fixture-password")

        model.submit()
        model.submit()
        await wait { gate.calls == 1 }

        precondition(model.isBusy)

        gate.finish()
        await wait { model.step == .confirm }

        precondition(model.password.isEmpty && model.repeatedPassword.isEmpty)
        precondition(model.resendSeconds == 60)

        model.resend()

        precondition(gate.calls == 1)

        model.setOutcome(.error)
        model.setCode("01234")
        precondition(!model.isBusy)

        model.setCode("012345")
        precondition(model.code == "012345")
        model.submit()
        await wait { gate.calls == 2 }
        gate.finish()
        await wait { !model.isBusy }

        precondition(model.message == "kit.codeError" && !model.isCompleted)

        model.setPath([])

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
        await wait { !model.isBusy }

        precondition(model.resendSeconds == 60)

        model.setCode("012345")
        model.submit()
        await wait { gate.calls == 4 }
        gate.finish()
        await wait { model.isCompleted }

        precondition(model.code == "012345", "Keep the successful form visible until dismissal completes")
        model.beginDismissal()
        precondition(model.code == "012345")
        model.close()

        precondition(model.email.isEmpty && model.path.isEmpty && !model.isCompleted)
        precondition(model.code.isEmpty)
    }

    @MainActor
    private static func verifyRecoveryAndCloseCancellation() async {
        let gate = RequestGate()
        let model = CatalogAuthViewModel(pause: { await gate.pause($0) })

        model.setEmail("reader@example.test")
        model.navigate(.recovery)
        model.submit()
        await wait { gate.calls == 1 }
        gate.finish()
        await wait { !model.isBusy }

        precondition(model.message == "kit.recoverySent")

        model.setPath([])
        model.setPassword("secret")
        model.submit()
        await wait { gate.calls == 2 }

        model.close()
        gate.finish()
        for _ in 0..<20 { await Task.yield() }

        precondition(!model.isCompleted && !model.isBusy && model.password.isEmpty)
    }

    @MainActor
    private static func verifyBackCancellation() async {
        let gate = RequestGate()
        let model = CatalogAuthViewModel(pause: { await gate.pause($0) })

        model.setEmail("reader@example.test")
        model.navigate(.recovery)
        model.submit()
        await wait { gate.calls == 1 }

        model.setPath([])
        gate.finish()
        for _ in 0..<20 { await Task.yield() }

        precondition(model.path.isEmpty && model.password.isEmpty && !model.isBusy)
        model.close()
    }

    @MainActor
    private static func wait(_ predicate: () -> Bool) async {
        for _ in 0..<1000 {
            if predicate() { return }

            await Task.yield()
        }

        preconditionFailure("Presentation transition did not complete")
    }
}
