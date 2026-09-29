# Shared logic

Scope: this module, including its Gradle configuration and all source sets.
Read [architecture](../docs/standards/architecture.md),
[Kotlin style](../docs/standards/kotlin-style.md) and
[verification](../docs/standards/verification.md).

- Put portable business models/contracts/operations in `commonMain`, organized
  by feature. Data implementations depend on domain contracts, not vice versa.
- UI types, screen state, navigation and lifecycle ViewModel bases are forbidden
  here. Keep DTOs and database/network library types behind data boundaries.
- Use immutable domain models and explicit mappers. Validate declared constraints
  without inventing response defaults or rejecting legitimate server values.
- Put Android/JVM/UIKit/Foundation APIs in their matching source sets. UIKit
  stays in `iosMain`; macOS has its own implementation. Use `expect/actual` only
  for genuinely platform-dependent behavior; use injected interfaces for services.
- Keep exported Kotlin APIs small and usable from Swift. Check nullability,
  collections, names and errors in a Swift consumer. Do not expose implementation
  generics or platform-driver objects just because Kotlin callers can use them.
- Shared operations must be safe to invoke from UI contexts. Give coroutines an
  explicit owner, preserve cancellation, and inject clocks/dispatchers/services
  where behavior needs control in tests. Never add `GlobalScope` work.
- Current Apple output is the static `SharedLogic` framework with direct Xcode
  integration. Do not add SKIE, Swift export, another coroutine bridge, a DI
  framework, network client or database without a separate compatibility decision.
- Test shared behavior with deterministic fakes/fixtures. Changes to exported API
  require compilation of Kotlin clients and both Swift clients. Platform behavior
  needs tests in the matching source set; common tests alone are insufficient.
