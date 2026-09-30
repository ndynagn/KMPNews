# Shared logic

Scope: this module, including its Gradle configuration and all source sets.
Read [architecture](../docs/standards/architecture.md),
[Kotlin style](../docs/standards/kotlin-style.md) and
[verification](../docs/standards/verification.md).
Dependency choices and admission gates: [accepted stack](../docs/standards/technology-stack.md).

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
- Repository contracts live in domain; implementations, Ktor/serialization DTOs,
  Room entities/DAOs and mapping live in data. Never export HTTP/Room types or
  `PagingData` through domain contracts; pagination must be usable from Swift.
- Define cache freshness, refresh, pagination and failure behavior in the feature
  contract. A failed request is not a successful empty result. Do not infer a
  retention period or fallback from a library example.
- Shared operations must be safe to invoke from UI contexts. Give coroutines an
  explicit owner, preserve cancellation, and inject clocks/dispatchers/services
  where behavior needs control in tests. Never add `GlobalScope` work.
- Keep the static `SharedLogic` framework and direct Xcode integration. SKIE is
  the selected async bridge; verify pinned versions before integration. Export
  expected failures as explicit models; no unhandled exceptions may escape an
  exported Flow. Preserve cancellation rather than wrapping it as a failure.
- Use Koin compiler DSL for constructor registrations and explicit interface bindings.
  Keep compileSafety/strictSafety enabled; provider lambdas are for runtime/platform
  inputs. Validate the assembled graph at an isolated composition entry point,
  including a negative compilation check when changing DI verification. Do not
  add Koin annotations to business classes or suppress missing bindings.
- Keep Koin wiring outside domain/data behavior. Expose typed construction entry
  points for Swift composition roots; do not require Swift callers to locate
  dependencies through the Koin container.
- For the NewsData.io Free prototype, keep the API key out of tracked files,
  fixtures and logs, including query URLs. Direct-client access does not hide
  the key in distributed binaries; follow the stack document's prototype boundary.
- Test shared behavior with deterministic fakes/fixtures. Changes to exported API
  require compilation of Kotlin clients and both Swift clients. Platform behavior
  needs tests in the matching source set; common tests alone are insufficient.

Feed implementations and tests across source sets follow the
[feed contract](../docs/news-feed-data-domain-plan.md) and
[feed instructions](src/commonMain/kotlin/com/ndynagn/kmp/news/feature/feed/AGENTS.md).
The appleInteropTest fixtures are opt-in verification code, never normal framework APIs.

Additional review routing: apply [Kotlin naming and organization](../docs/standards/kotlin-style.md#role-names-acronyms-and-data-operations)
for this scope and [test conventions](../docs/standards/verification.md#test-and-fixture-conventions)
for its fixtures and verification code.
