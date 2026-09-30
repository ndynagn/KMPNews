# Code documentation and justified exceptions

Status: accepted project policy. Apply to new and modified code; this is not a
claim that all existing declarations have been audited. Write documentation and
comments in English. Language layout belongs to [Kotlin style](kotlin-style.md)
and [Swift style](swift-style.md); review uses the [checklist](review-checklist.md).

## Required coverage

Document public shared APIs, cross-module contracts and non-obvious behavior or
constraints. Obvious private/internal methods do not need ceremonial comments.
For Kotlin's default-public declarations, first consider whether the declaration
should actually be internal/private; do not expand an API merely to document it.

An API comment describes purpose, use and observable behavior. An implementation
comment explains intent, constraints or a non-obvious tradeoff. A complex algorithm
may also need an explanation of what it does; the rule is to avoid restating obvious
syntax, not to prohibit useful explanations.

Keep the contract on its interface or authoritative declaration. Implementations
add only implementation-specific constraints and link to the contract rather than
copying it. Such details cannot silently weaken interface guarantees. Feature docs
hold the broader business/lifecycle contract; link to them rather than reproducing
that contract at every method.

## Format and content

Use KDoc (`/** ... */`) for Kotlin and `///` for Swift. Start with a short summary
of purpose and behavior. Add only relevant details:

- Preconditions, significant inputs and the meaning of absent/nullable values.
- Units and time reference, ordering, and any meaningful result guarantees.
- Expected failures versus thrown errors, cancellation and side effects.
- Lifecycle/resource ownership, actor/thread requirements and subscription behavior.

Use symbol links where supported. Do not mechanically repeat every parameter/type
in `@param` or `@return`; use tags when a separate detailed explanation helps.
`@throws` documents actual thrown failures, not failures returned as result values.
Swift Parameters/Returns/Throws sections follow the same relevance principle.
Update documentation in the same change that changes its contract; remove stale
comments instead of leaving them to contradict code.

Illustrative Kotlin contract excerpt (surrounding feature types are omitted):

```kotlin
/**
 * Observes the locally stored feed without starting a network request.
 *
 * Storage failures are emitted as [FeedReadResult.StorageFailure], then collection
 * completes. Cancelling the collector ends its subscription.
 */
fun observeFeed(): Flow<FeedReadResult>
```

Illustrative Swift lifecycle contract excerpt:

```swift
/// Stops this window's observation task while keeping app-owned services alive.
@MainActor
func stopObserving()
```

These snippets illustrate documentation shape, not new APIs or permission to invent
business behavior. Optional fields need an explanation when their absence has a
meaning beyond what the type alone communicates.

## TODO and FIXME

Use TODO for deferred work and FIXME for a known defect. Include why it remains
and a concrete completion/removal condition. Link an existing issue when available;
creating an issue, recording an author or adding a date is not mandatory.
A critical defect is not acceptable merely because a FIXME exists.

Illustrative deferred work:

```kotlin
// TODO: Select retry timing after the provider policy is agreed; remove this note
// when that decision and deterministic retry tests are implemented.
```

Do not infer permission to implement that illustrative retry policy. Avoid bare
`TODO: improve`, commented-out implementations and notes already resolved by code.

## Exceptions

Explain an exception close to the affected declaration/configuration. Keep lint
suppressions narrow and name the exact rule; do not suppress a file/module merely
to silence unrelated failures. Temporary exceptions need an observable removal
condition. Permanent exceptions need a reason, not an invented expiry date.

Example rationale for a local naming exception:

```kotlin
// Preserve the external protocol's spelling while this adapter exposes that
// contract; remove the exception when the adapter boundary is replaced.
```

Use a real documented rule ID only if a suppression is actually necessary.
An architecture exception must be recorded explicitly in the feature contract or
applicable standard; a local comment cannot override architecture invariants.
No separate registry of every minor style exception is required.

## Sources and project choices

[KDoc](https://kotlinlang.org/docs/kotlin-doc.html) defines syntax;
[Kotlin conventions](https://kotlinlang.org/docs/coding-conventions.html#documentation-comments)
recommend concise prose rather than routine parameter/return tags.
[Swift API Design Guidelines](https://www.swift.org/documentation/api-design-guidelines/)
recommend documentation for every declaration. Our narrower mandatory coverage is
an explicit application-project choice, not a claim that Swift recommends it.
[Google code review guidance](https://google.github.io/eng-practices/review/reviewer/looking-for.html)
distinguishes implementation comments from API documentation and asks reviewers
to keep documentation current. TODO/FIXME requirements and the exception process
above are project decisions.
