# Architecture and feature ownership

Status: accepted engineering rules. These rules apply to new work; documented
starter exceptions are migration debt, not reference implementations.
The [accepted stack](technology-stack.md) records technology choices, prototype
constraints and compatibility status; it is not evidence of installed dependencies.

## Structure and dependency direction

Keep existing deployment/build modules. In Kotlin, use packages under
`com.ndynagn.kmp.news.feature.<name>` with `domain` and `data` in sharedLogic;
shared Compose presentation uses `feature.<name>.presentation` in sharedUI.
Android/Desktop hosts contain platform integration and adapters. Feature names are lowercase
package segments (for example `savedarticles`, never `saved-articles`). Swift
clients use `Features/<FeatureName>`, with Views and ViewModels owned by the client.
Create only directories that have a real responsibility and implementation.

Code dependencies are `presentation -> domain <- data`. Runtime calls travel from
View to its presentation ViewModel to domain contracts, fulfilled by data implementations.
The composition root is allowed to know concrete implementations to assemble the
application. Domain never imports the composition root or data implementations.

- Domain: immutable business models, repository contracts and business operations.
- Data: transport/storage models, data sources, repository implementations and
  explicit mappers. External SDK objects do not leak through domain contracts.
- Presentation: sharedUI owns Compose Views, ViewModels, state and navigation for
  Android/Desktop; iOS/macOS each own Swift Views, ViewModels and navigation.

Cross-feature dependencies use another feature's public domain contracts, not
its data/presentation internals. Extract a common domain concept only after real
reuse appears. Shared technical services must have a concrete name and scope;
`core` is not a default dumping ground.

## Patterns and when to use them

| Pattern | Use when | Avoid |
| --- | --- | --- |
| Repository | Isolating acquisition/persistence and exposing domain operations | A new interface for every helper |
| Use case | Business policy, coordinating dependencies or reusable operations | Forwarding one repository call with no added meaning |
| Explicit mapper/adapter | Translating a real transport, storage or platform boundary | Reflection-based hidden mapping and DTO leakage |
| Constructor injection | Supplying collaborators through a composition root | Service locators and global mutable dependencies |
| MVVM with UDF | Independent reads, edits and saves | Compulsory event protocols for independent actions |
| MVI inside ViewModel | Coordinated requests, refresh, retry, pagination or item operations | An intermediate message/reducer protocol with no concrete benefit |
| MVI with separate transitions | Event history and interacting phases determine valid transitions | Selecting by screen name, button count or asynchronous work alone |
| Composition | Combining focused behavior/components | BaseViewModel/BaseRepository hierarchies without a demonstrated need |

KISS/YAGNI mean implementing the agreed contract without speculative layers.
DRY means sharing the same responsibility, not merging similar-looking code with
different platform semantics. Introduce a Gradle module only for a concrete
isolation/build/dependency requirement; package-level feature boundaries suffice
for the starter.

Koin assembles Kotlin dependencies in DI modules/composition roots. Constructor
parameters express dependencies in ordinary classes: no KoinComponent, global
`get()` or hidden service lookup in repositories, business operations or ViewModels.
DI definitions may resolve collaborators. Host/route factories are composition
boundaries, not permission to resolve services throughout the UI tree. Swift roots
obtain typed shared dependencies and pass them to Swift ViewModel initializers.
Never make a screen ViewModel an app-wide singleton.

## Presentation contracts

All variants use unidirectional data flow and controlled state mutation. This is a
project classification, not a universal definition of MVVM/MVI. Native Swift
presentation remains separate from shared Compose presentation.

### Selection procedure

1. Static UI without data work needs no ceremonial ViewModel.
2. Choose MVVM with named methods for independent reads, local edits and saves.
3. Choose MVI inside ViewModel when requests need coordination or the feature
   benefits from one explicit event contract. This is the default for functional
   screens such as feeds, search and favorites. Handlers own transitions and work.
4. Extract a pure reducer only when a concrete sequence of interacting events
   determines valid transitions and independent transition tests improve clarity.
   Record that sequence and its transition table in the feature contract.
5. Before escalating, check whether the complexity belongs in domain policy or
   an independently owned part of the screen.

Simple profiles/settings illustrate MVVM. A paginated feed illustrates MVI inside
ViewModel. A player with seek/buffer/reconnect phases or an editor with conflicting
local/remote changes may justify separate transitions. Names such as "checkout"
do not select a pattern automatically. Button count, ViewModel length, coroutines,
cache, pagination and WebSocket usage alone are not escalation criteria.

### Contracts and vocabulary

Keep Screen, State and ViewModel in focused files. For project-owned MVI use
`Event` for incoming user actions; explicit lifecycle methods or lifecycle events
must document ownership. Add `SideEffect` only for actual one-time UI commands.
`Message` is an internal reducer input only in the separate-transition variant.
`Operation` and error types are feature-specific data, not mandatory MVI layers.
Do not add a universal BaseViewModel or an empty effect bus.

In MVVM, named methods update state. In MVI inside ViewModel, `onEvent` dispatches
to handlers that update state directly and coordinate domain calls. With separate
transitions, the coordinator performs I/O and the pure reducer computes state
from internal messages. Business policy remains in domain in all variants.

### State shape

Choose state shape independently from event handling. Use sealed Kotlin variants
or Swift enums with associated values for mutually exclusive modes. For the feed
these are Initial, Loading, Empty, Error and Content. Represent coexisting details
inside their mode: Content retains cards during refresh, append failures and
item-specific operations. Use typed statuses where they clarify valid combinations;
do not create one class for every combination of independent operations.
UI-local expansion, focus and scroll may stay in Views with explicit restoration.
Keep collectors, jobs and mutable SDK objects outside published state.

### Asynchronous work and effects

For each operation record its owner, trigger, concurrency/replacement policy,
cancellation, stale-response handling and retry. These obligations apply to all
three variants; a reducer or library does not supply the feature policy.

For each SideEffect record recipient, buffering/replay, recreation and behavior
without a subscriber. A SharedFlow or Channel alone does not guarantee exactly-once
delivery. Critical results belong in state or persisted data; navigation, sharing
and system UI execution belong to the client. Keep task/subscription lifetimes
separate from the lifetime of UI observers.

### MVIKotlin admission

[MVIKotlin](https://arkivanov.github.io/MVIKotlin/) is a candidate, not an installed
or required dependency. Consider it for Kotlin presentation with separate
transitions and a demonstrated need for a standard Store, logging or time travel.
Admission requires a separate recorded architecture decision, target compatibility
checks, Store ownership/disposal and integration with existing navigation.
A shared Kotlin presentation Store for SwiftUI is outside the accepted boundary.

Preserve library terminology: Intent corresponds to project Event, Label to
SideEffect, and Message to an internal transition input. Do not wrap solely to
rename these types. Store/Executor coordination calls domain contracts rather
than moving business policy into presentation. Swift uses native presentation
with the same observable behavior, not imported Kotlin presentation contracts.

[Store documentation](https://arkivanov.github.io/MVIKotlin/store/) describes
Executor async work and Reducer transitions. Labels are delivered to current
subscribers without caching; they do not make critical results durable.
[Binding lifecycle](https://arkivanov.github.io/MVIKotlin/binding_and_lifecycle/)
and [state preservation](https://arkivanov.github.io/MVIKotlin/state_preservation/)
must be designed explicitly: unbinding a View is not disposing a Store and
using the library does not automatically restore application state.

## Contracts, data and concurrency

Model external payloads explicitly. Request DTOs describe the wire contract;
domain operations use domain values. An extra request entity or NoParams type is
not mandatory. DTO/domain mapping is explicit and behaviorally tested, including
nulls, nested values and declared enum/default semantics. Do not invent response
fallbacks or reject data using undocumented constraints.

Make expected failures part of the contract and preserve cancellation. Avoid
catch-all handlers that turn cancellation into an error screen. Own every task,
subscription and resource: distinguish application, window, screen and request
scopes. Inject time and external services where deterministic tests require them.
The accepted stack selects Ktor/serialization, Room and SKIE. Admission requires
the [compatibility gate](verification.md#dependency-and-interop-admission-gate).
Shared public contracts expose domain values, never HTTP/Room types, transport
DTOs or PagingData. Define portable pagination usable by both Kotlin and Swift.
SKIE-exported Flows must not leak unhandled exceptions: represent expected
failures explicitly and preserve cancellation. Test both observation and suspend
calls in Swift; Kotlin compilation alone does not establish safe interop.

Every cached feature defines source-of-truth, freshness/retention, refresh/append
behavior and the result of a failed read or write. Never disguise failed network
work as successful empty data. If cached content is retained on failure, preserve
the failure/freshness information required by the contract. Decide identity,
deduplication and pagination from the provider/product contract, not sample code.

Views send actions; presentation ViewModels publish controlled state and delegate
business policy to shared domain. UI-local toggles/focus/selection may stay local.
Navigation and transient effects belong to shared Compose or native Swift
presentation. Define replay, missed-subscriber and lifecycle behavior before
choosing an event channel. No blanket
rule says every transient UI interaction needs a separate event bus.

## Component ownership and reuse

A screen and ViewModel are separate files. Small private helpers/previews may
stay beside their only owner. Once a component has a second independent consumer,
move it to the nearest scope that represents both consumers:

| Consumers / coupling | Destination |
| --- | --- |
| Multiple owners in one feature | Feature presentation/components |
| Multiple features, domain-independent visual primitive | sharedUI kit for Compose; client UI kit for Swift |
| Multiple screens, shared domain-aware presentation | Named reusable presentation components |
| Multiple domain/data owners | Named reusable scope in that layer |

Repeating ArticleRow for many articles inside one list is one consumer. A row
used by both search and saved-article screens has two consumers. Extract it with
explicit data/callback inputs; do not pass a whole feature ViewModel. A generic
UI kit does not fetch articles or own repository dependencies. Android/Desktop
reuse Compose presentation in sharedUI; Apple clients retain native Swift UI.

## Transitional exceptions and feature instructions

Android/Desktop intentionally depend on sharedUI. Its starter App constructs
Greeting directly; iOS ContentView invokes Greeting, and macOS GreetingViewModel
constructs a synchronous Greeting.
These minimal starter paths remain unchanged until a separate migration; new
feature code follows the boundaries and injection rules above.

Feature AGENTS files are created only with a real feature whose contract needs
additional rules. Use the [template](../templates/feature-agents.md), remove its
placeholders, and keep one shared contract referenced by platform-specific files.
Add a sibling CLAUDE import. Do not duplicate module/root policy or introduce
empty features. For Kotlin platform implementations/tests outside the commonMain
feature subtree, explicitly link the common feature contract from the task/local
instructions: filesystem inheritance does not cross source sets.

## Rationale and source adaptation

[Touchlab](https://touchlab.co/using-ai-to-check-your-kmp-readiness) supports checking
facts with scripts and investigating unknown dependencies separately. Check all
required targets, not only the existence of common metadata.
[Nami](https://tectontide.com/en/blog/building-nami-ios-app-with-ai/) supports the
build/run/inspect feedback cycle, not mandatory tools or fixed agent roles.

The supplied flutter_one_touch_customer AGENTS inspired explicit DTO boundaries,
readability, lifecycle ownership and reuse. Do not transfer BLoC, Equatable,
Freezed, initState-only triggers, Dart private underscores or unconditional use
cases. Our code dependency direction explicitly keeps domain independent of data.
Android's [domain-layer guide](https://developer.android.com/topic/architecture/domain-layer)
also treats use cases as useful where behavior/complexity warrants them, rather
than requiring pass-through layers for every call.

The [Compose sample](https://gitlab.com/effectivepublic/android/compose-sample)
informs feature organization, explicit mapping and screen contracts. Its older
dependencies are not our version baseline. Keep repository contracts in domain,
do not expose Android PagingData to Swift, and do not copy effect delivery or base
class patterns without a feature-specific reason.
