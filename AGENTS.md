# KMPNews agent instructions

## Read the applicable rules first

This file governs the repository. Before editing, read the module instructions
below and any descendant `AGENTS.md` on the path to each affected file, even
when the session started at the repository root. Local instructions add scoped
requirements; do not silently weaken the architecture invariants below. Resolve
contradictions explicitly. `AGENTS.md` is canonical; adjacent `CLAUDE.md` files
only import it. Keep instructions and new engineering documentation in English.

| Area                                            | Read before editing                            | Supporting standard                                                                      |
|-------------------------------------------------|------------------------------------------------|------------------------------------------------------------------------------------------|
| Shared domain/data and platform implementations | [sharedLogic/AGENTS.md](sharedLogic/AGENTS.md) | [Architecture](docs/standards/architecture.md), [Kotlin](docs/standards/kotlin-style.md) |
| Android client                                  | [androidApp/AGENTS.md](androidApp/AGENTS.md)   | Kotlin                                                                                   |
| JVM Desktop client                              | [desktopApp/AGENTS.md](desktopApp/AGENTS.md)   | Kotlin                                                                                   |
| iOS client and the shared Apple project         | [iosApp/AGENTS.md](iosApp/AGENTS.md)           | [Swift](docs/standards/swift-style.md)                                                   |
| Native macOS client                             | [macosApp/AGENTS.md](macosApp/AGENTS.md)       | Swift; also iOS instructions for project configuration                                   |
| Shared Android/Desktop Compose presentation      | [sharedUI/AGENTS.md](sharedUI/AGENTS.md)       | Kotlin and Architecture                                                                  |
| Root Gradle/configuration                       | This file                                      | Kotlin and [Verification](docs/standards/verification.md)                                |

Read the linked language standard when changing that language, and the
architecture standard for new features or changes to boundaries/public APIs.
Read the [accepted stack](docs/standards/technology-stack.md) before dependency
or integration work. Android/Desktop feature work also requires sharedUI rules;
read the host module rules when changing platform integration or lifecycle.
Feature-specific contracts refine these rules; see the
[feature instruction template](docs/templates/feature-agents.md).

## Architecture invariants

- `sharedLogic` owns domain/data for all clients, never ViewModels, screen state
  or presentation services. `sharedUI` owns shared Compose presentation, including
  ViewModels and navigation, for Android/Desktop. iOS/macOS own Swift presentation.
- New code is feature-first inside existing modules. Code dependencies are
  `presentation -> domain <- data`; domain does not import data implementations.
- Views render state and forward actions to presentation ViewModels. Business policy
  belongs in shared logic. DTOs and storage/transport types stay in data.
- Use constructor injection; Koin assembles Kotlin dependencies in composition
  roots/DI modules, not through service lookup in ordinary classes. Add interfaces,
  use cases and modules for concrete boundaries or behavior, not empty scaffolding.
- Do not invent API fields, business rules, product features or design decisions.
  Record an unresolved contract, affected scope and exact unblock condition in
  the task or feature document; continue independent work.
- Use MVVM for simple screens and MVI for complex transitions/concurrent work,
  following the architecture standard. Record the choice in the feature contract.
- Existing direct Greeting construction/calls are starter exceptions, not examples
  for new features. Android/Desktop sharing presentation is an accepted boundary.

## Readability and reuse

- Use meaningful names, cohesive functions and composition. Avoid speculative
  abstractions, nested scope-function chains and unexplained force unwraps.
- Four spaces, UTF-8, LF, final newline, no trailing whitespace; target 120 columns
  in code. Keep related declarations together and one blank line between semantic
  phases. Do not split cohesive DSL/annotation/control-flow blocks mechanically.
- Screens and ViewModels have separate files. Small private helpers and previews
  may stay with their single owner. On a second independent consumer, move a
  component to the nearest shared scope: feature components, reusable components
  of its layer, or the appropriate UI kit. Repeated list rows are one consumer.
- Generic UI-kit components do not own feature data or ViewModels. Compose reuse
  belongs in sharedUI; keep Swift UI native to its Apple client.
- Comments explain intent, constraints and non-obvious tradeoffs. Document public
  shared contracts, including error/cancellation semantics; avoid narrating code.

## Change and verification discipline

- Inspect Git status and preserve unrelated work. Use `features/` branches from
  the agreed base; commit, push and PR creation require task authorization.
- Keep changes scoped. Do not combine a feature with unrelated migrations,
  toolchain upgrades or repository-wide formatting.
- Use the configured formatter on added/modified files; formatting is explicit,
  not part of builds. Report existing full-audit failures as debt, never as PASS.
- Verify dependency versions against every required target. A common artifact
  alone does not prove iOS/macOS/JVM/Android support.
- Run checks appropriate to the changed behavior using the verification matrix.
  UI work needs launch, scenario and visual evidence, not only compilation.
- Report executed commands/results and unverified platforms. An unavailable
  environment is not success. Do not claim App Store signing from a local build.
- Keep rules current when a real contract changes. Do not duplicate root rules
  in module/feature files or turn research notes into competing instructions.
