# Architecture and feature ownership

Status: accepted engineering rules. These rules apply to new work; documented
starter exceptions are migration debt, not reference implementations.

## Structure and dependency direction

Keep existing deployment/build modules. In Kotlin, use packages under
`com.ndynagn.kmp.news.feature.<name>` with `domain` and `data` in sharedLogic;
client presentation uses `feature.<name>.presentation`. Feature names are lowercase
package segments (for example `savedarticles`, never `saved-articles`). Swift
clients use `Features/<FeatureName>`, with Views and ViewModels owned by the client.
Create only directories that have a real responsibility and implementation.

Code dependencies are `presentation -> domain <- data`. Runtime calls travel from
View to native ViewModel to domain contracts, fulfilled by data implementations.
The composition root is allowed to know concrete implementations to assemble the
application. Domain never imports the composition root or data implementations.

- Domain: immutable business models, repository contracts and business operations.
- Data: transport/storage models, data sources, repository implementations and
  explicit mappers. External SDK objects do not leak through domain contracts.
- Presentation: platform-local ViewModels, UI state and rendering/navigation.

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
| Native MVVM with UDF | Producing screen state and handling user actions | Shared ViewModels or compulsory reducers for simple screens |
| Composition | Combining focused behavior/components | BaseViewModel/BaseRepository hierarchies without a demonstrated need |

KISS/YAGNI mean implementing the agreed contract without speculative layers.
DRY means sharing the same responsibility, not merging similar-looking code with
different platform semantics. Introduce a Gradle module only for a concrete
isolation/build/dependency requirement; package-level feature boundaries suffice
for the starter.

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
Do not prescribe a networking/database/serialization/DI/interop library here.
Choose one supported interop approach before exposing async Kotlin operations to
Swift; verify actual consumer behavior, including cancellation and errors.

Views send actions; native ViewModels publish controlled state and delegate
business policy to shared domain. UI-local toggles/focus/selection may stay local.
Navigation and transient effects remain platform-owned. Define replay, missed
subscriber and lifecycle behavior before choosing an event channel. No blanket
rule says every transient UI interaction needs a separate event bus.

## Component ownership and reuse

A screen and ViewModel are separate files. Small private helpers/previews may
stay beside their only owner. Once a component has a second independent consumer,
move it to the nearest scope that represents both consumers:

| Consumers / coupling | Destination |
| --- | --- |
| Multiple owners in one feature | Feature presentation/components |
| Multiple features, domain-independent visual primitive | Platform-local UI kit |
| Multiple screens, shared domain-aware presentation | Named reusable presentation components |
| Multiple domain/data owners | Named reusable scope in that layer |

Repeating ArticleRow for many articles inside one list is one consumer. A row
used by both search and saved-article screens has two consumers. Extract it with
explicit data/callback inputs; do not pass a whole feature ViewModel. A generic
UI kit does not fetch articles or own repository dependencies. Reuse never creates
cross-client shared UI.

## Transitional exceptions and feature instructions

Android/Desktop still depend on sharedUI. iOS ContentView directly invokes the
starter Greeting. macOS GreetingViewModel constructs a synchronous Greeting.
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
