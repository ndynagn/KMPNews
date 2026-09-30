# Accepted technology stack

Decision date: 2026-09-30. Status: architecture accepted; feed dependency integration
is tracked in the [feed validation record](../news-feed-validation.md). This document is the single technology decision
record; [architecture](architecture.md) defines behavior and boundaries, while
[verification](verification.md#dependency-and-interop-admission-gate) defines admission.
Acceptance does not mean that these dependencies have been installed or tested.

## Ownership and selected technologies

| Area | Choice and responsibility | Primary reference |
| --- | --- | --- |
| Shared business/data | sharedLogic for Android, JVM, iOS and native macOS; no presentation | [KMP sharing](https://kotlinlang.org/docs/multiplatform/multiplatform-share-on-platforms.html) |
| Kotlin dependency assembly | Koin in composition roots/DI modules; constructor injection in ordinary classes | [Koin KMP](https://insert-koin.io/docs/reference/koin-mp/kmp/) |
| Business networking | Ktor with kotlinx.serialization; OkHttp on Android/JVM, Darwin on Apple | [Engines](https://ktor.io/docs/client-engines.html), [serialization](https://ktor.io/docs/client-serialization.html) |
| Local persistence | Room KMP; shared data models/DAOs with platform database construction | [Room KMP](https://developer.android.com/kotlin/multiplatform/room) |
| Asynchronous shared APIs | Coroutines/Flow; explicit failures and cancellation semantics | [Coroutines](https://kotlinlang.org/docs/coroutines-overview.html) |
| Android/Desktop presentation | sharedUI with Compose, shared ViewModels and MVVM/MVI contracts | [Compose ViewModel](https://kotlinlang.org/docs/multiplatform/compose-viewmodel.html) |
| Compose navigation/images | Navigation3 and Coil 3; host adapters for platform behavior | [Navigation3](https://kotlinlang.org/docs/multiplatform/compose-navigation-3.html), [Coil](https://coil-kt.github.io/coil/getting_started/) |
| Apple presentation | SwiftUI, MainActor Observable ViewModels, Swift Concurrency; initializer injection | [Observation](https://developer.apple.com/documentation/swiftui/migrating-from-the-observable-object-protocol-to-the-observable-macro) |
| Apple navigation/images | Native NavigationStack/NavigationSplitView and Nuke/NukeUI | [SwiftUI navigation](https://developer.apple.com/documentation/swiftui/navigation), [Nuke](https://github.com/kean/Nuke) |
| Kotlin/Swift bridge | SKIE over the existing static SharedLogic framework/direct integration | [SKIE Flow](https://skie.touchlab.co/features/flows), [suspend functions](https://skie.touchlab.co/features/suspend) |
| Later account/favorites | Supabase Auth and Postgres; separate from the initial feed/cache | [Auth](https://supabase.com/docs/guides/auth), [Kotlin client](https://supabase.com/docs/reference/kotlin/introduction) |

The Apple clients use the same Kotlin repositories and Room persistence; they do
not need parallel URLSession/SwiftData business layers. Nuke loads presentation
images, not business API payloads. Apple presentation contracts remain Swift-owned.
Compose ViewModels are shared code with separate screen/window instances, not
shared mutable state across every client or window.

Select MVVM, MVI inside ViewModel or MVI with separate transitions using the
[presentation contracts](architecture.md#presentation-contracts). MVIKotlin is a
candidate for justified complex Kotlin presentation, not an admitted dependency.
Its use requires a separate architecture decision and compatibility checks.
No universal BaseViewModel, shared Kotlin Store for SwiftUI or toolchain upgrade
is selected by this decision.

Koin Compiler Plugin 1.2.1 is selected with Koin 4.2.2 and Kotlin 2.4.20.
Use compiler DSL auto-wiring without annotations on business classes, explicit
interface bindings and provider lambdas for platform/runtime resources. Keep
`compileSafety`, `strictSafety` and `unsafeDslChecks` enabled. The isolated
`koinApplication` is the full-graph validation entry point; do not hide unresolved
graphs behind dynamic module lists or safety suppressions. KSP remains Room-only.
Koin still resolves instances at runtime: compiler checks do not validate API keys,
resource lifecycle or business behavior. See the [1.2.1 release](https://github.com/InsertKoinIO/koin-compiler-plugin/releases/tag/1.2.1)
and [compiler setup](https://insert-koin.io/docs/setup/compiler-plugin/).

## Prototype and next functional scope

The selected provider is [NewsData.io Free](https://newsdata.io/documentation).
The first functional slice is the feed plus a local cache. Ktor calls NewsData.io
directly for the prototype; Edge Functions and a custom server are not part of it.
Keep the key out of Git, fixtures, logs, query-URL diagnostics and crash reports.
Local environment/build configuration prevents accidental source publication but
does not make a key embedded in the application secret. Revisit a server-side
mediator and quota protection before distributing the application.

Before implementing the feed, record an authoritative Free-plan API contract:
available fields, nullability, pagination/end conditions, errors, request limits
and permitted content storage/retention. Do not assume paid fields, full article
text, unrestricted caching or numeric page indexes. Decide language/filter and
refresh behavior in that feature's product contract; this document invents none.
Unavailable contract facts need a scoped blocker and exact unblock condition.

Auth and saved favorites follow later. Supabase Realtime is not an automatic
Room/Postgres synchronization engine. Offline write queues and conflict resolution
are outside the initial slice. A later favorites contract must define ownership,
access policies and synchronization behavior. Keep privileged Supabase keys out
of clients and validate RLS isolation for user-owned data at that stage. The Kotlin
Supabase client is community-maintained; verify its versions/targets and Ktor
compatibility before choosing its integration details.

## Compatibility status

Repository baseline inspected for this decision: Kotlin 2.4.20, Coroutines 1.11.0,
Compose Multiplatform 1.12.1, Gradle 9.7.1 and AGP 9.1.1. The repository version
catalog remains authoritative. Apple integration retains project.pbxproj, the
existing deployment targets and Swift 5 language mode; no format/toolchain change
is implied by this stack.

| Evidence inspected | What it establishes | What remains unverified |
| --- | --- | --- |
| Room 2.8.5 published [module metadata](https://dl.google.com/dl/android/maven2/androidx/room/room-runtime/2.8.5/room-runtime-2.8.5.module) | Android, JVM, iosArm64, iosSimulatorArm64 and macosArm64 variants exist | Room/KSP/SQLite resolution, generation, compilation and actual persistence on our targets |
| SKIE [0.10.15 release](https://github.com/touchlab/SKIE/releases/tag/0.10.15) | Release explicitly adds Kotlin 2.4.20 support | Our framework builds, Swift consumption, cancellation and failures |
| Koin/Ktor/Navigation3/Coil/Nuke documentation above | Selected integrations and documented capabilities | Exact version set, transitive graph and behavior in this repository |
| Supabase Kotlin documentation | Community client and supported integration surface | Version/engine/target compatibility and later Auth/favorites behavior |

The table above preserves the initial research baseline. The version catalog now
pins feed dependencies; subsequent build/runtime evidence is in the feed validation
record. Presentation dependencies and later Supabase work remain outside that gate.
Pin exact versions during each integration, including compiler plugins and Swift packages. Do not silently downgrade Kotlin
or swap a selected library if the compatibility gate fails; record the concrete
failure and revisit that decision while continuing independent work.

SKIE's documented Flow limitation requires special attention: custom exceptions
escaping a Flow can crash a Swift consumer. Model expected failures as values,
preserve cancellation and test the boundary. Do not assume a Swift catch block
protects an exported Flow, or mix SKIE with another bridge across features.

## Reference project and provenance

The [Effective Compose sample](https://gitlab.com/effectivepublic/android/compose-sample)
was inspected at commit `c0d4de95fac40416d6564b16c849c4bab9a81403`. Adopt its useful
feature organization, mapping and explicit screen-contract ideas. Its Kotlin 1.7 /
Compose 1.2-era dependencies are not the upgrade baseline. Repository interfaces
placed in data, Android PagingData in screen contracts and effect-stream delivery
are not requirements for this project.

The earlier [research archive](../ai-agent-workflow-proposal.md) and
[instruction adoption report](../agent-guidelines-adoption.md) retain historical
decisions and evidence. Their former prohibition on shared Compose presentation
is superseded by this accepted decision; their past checks do not validate this
new stack. See [this update's validation](../stack-architecture-adoption.md).
