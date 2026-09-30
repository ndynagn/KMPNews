# Swift style

Use the checked-in `.swift-format` and Xcode's bundled `swift-format`. Naming
follows [Swift API Design Guidelines](https://www.swift.org/documentation/api-design-guidelines/).
The project currently compiles in Swift 5 language mode; do not introduce a
language-mode migration through formatting.

## Layout and semantic spacing

- Four spaces, LF, UTF-8, final newline, no trailing whitespace, target 120 columns.
  Keep one blank line between declarations and independent semantic phases, with
  related stored properties/local declarations grouped together.
- Separate independent guards and return/computation phases. Attach comments to
  their following block. No empty lines immediately inside braces or consecutive
  blank lines. Preserve cohesive if/else, do/catch, switch, attribute/declaration
  pairs, argument lists, modifier chains and SwiftUI result-builder content.
- A small expression/computed View property may remain concise. Use braces for
  multi-step callbacks and functions. Avoid excessive nested closures and extracting
  every one-line expression into a new method solely to satisfy a length rule.
- Let the formatter arrange imports and multiline arguments. Use collection trailing
  commas; do not force newer call-site trailing-comma syntax into existing code.

```swift
func displayTitle(for article: Article) -> String {
    let title = article.title.trimmingCharacters(in: .whitespacesAndNewlines)
    let sourceName = article.sourceName

    guard !title.isEmpty else {
        return sourceName
    }

    return title
}
```

The fallback is an illustrative contract, not a new news-reader requirement.
Semantic grouping is reviewed by a human/agent; the formatter does not infer it.

## Naming and files

- Types and primary files use PascalCase; methods/properties/enum cases use
  lowerCamelCase. Swift feature folders use `Features/<FeatureName>`; use names
  that explain their role rather than `Utils`, `Helpers` or generic `Manager`.
- Choose meaningful argument labels (`loadArticle(id:)`, `openArticle(withID:)`)
  and predicates (`isLoading`, `canRetry`). Dependencies have descriptive names
  such as `newsRepository`; qualify different roles when types repeat.
- Use access modifiers for privacy, never a private-field underscore prefix.
  Prefer `let`; expose mutations through explicit methods and `private(set)`.
- Put screen and ViewModel in separate files. A preview or tiny private one-owner
  helper View can remain beside its owner. On a second independent consumer,
  extract to feature components, reusable presentation or the platform UI kit.
- Group extensions by responsibility; do not hide unrelated models or services
  in a screen's file. Prefer composition to application-wide base classes.

## Observation, tasks and interoperability

Use explicit `@MainActor @Observable` native ViewModels, even when one target's
compiler configuration supplies default isolation. Views render and send actions;
local UI state is allowed. Define stable ownership with SwiftUI state tools and
ensure ViewModel recreation does not accidentally restart business operations.

Do not initiate data work in `body`. Owned tasks have a defined trigger, repeat
policy and cancellation path. Avoid accidental self-retaining task lifetimes;
closing a window must not leave observers running. Do not assume deinit alone
solves subscription cleanup. Document actor/thread transitions at interop edges.

Do not use force unwraps/force try to hide an error path. A proven invariant needs
a local explanation. Keep expected domain failures distinct from cancellation.
Kotlin interop semantics must be checked with a Swift consumer, not guessed from
Kotlin tests. Previews use controlled data and no live network calls.

SKIE is the selected bridge: consume Flow through AsyncSequence and suspend work
through its cancellable async interface after the compatibility gate passes.
Swift tasks remain owned by the screen/window; Observation itself does not own
or cancel them. Expected failures arrive as shared contract values; do not rely
on do/catch to recover unhandled Kotlin exceptions escaping an exported Flow.
Use typed initializer injection, not Koin lookup inside Swift Views/ViewModels.
Reuse SharedLogic data/domain rather than adding Swift network/database copies.

Use feature-prefixed State and explicit action methods for MVVM. Project-owned MVI
keeps Screen, ViewModel, State, Event and SideEffect (when needed) in focused files.
Use `onEvent` and update state inside ViewModel handlers by default. Separate a pure
reducer only when the architecture selection procedure justifies it. Use Swift
enums with associated values for exclusive modes and structs for coexisting data.
Apply the same behavior and effect-delivery contract as Compose, without importing
Kotlin presentation types or an MVI framework into native Swift presentation.

Use NavigationStack/NavigationSplitView according to the native experience and
Nuke/NukeUI for images once admitted by the [stack gate](technology-stack.md).
Do not use a networking/image library choice as permission to duplicate business
requests outside sharedLogic. Retain the current Swift language mode and deployment
targets while validating these dependencies.

Use client resources/localization and platform tokens. Do not hardcode production
copy or invent designs when a task requires an exact supplied design. Inspect the
referenced design and validate meaningful controls, long content and text scaling.

See [verification](verification.md) for targeted formatting and platform checks.

## Acronyms and declaration organization

Use Swift spellings such as `URLRequest` and `articleURL`, retaining established
SDK names. Preserve imported Kotlin declarations as exported; do not add wrappers
solely to change `articleUrl` to `articleURL`. This keeps native naming independent
from the shared API's language conventions.

Order properties, initializers, then meaningful groups of methods. Put related
methods and overloads together, with principal operations before supporting logic;
do not sort alphabetically or mechanically by visibility. Follow protocol member
order when it improves understanding. Group extensions by responsibility or
conformance, near their owner or specific consumer; use a separate file for a
substantial or independent responsibility, not an arbitrary `Extensions.swift`.

Small, tightly related declarations may share a file. Existing separate
Screen/ViewModel and independently reusable component rules still apply.
Use [code documentation](code-documentation.md) for `///` coverage, actor/lifecycle
contracts, TODO/FIXME and justified exceptions.

## Apple resource naming

Use semantic feature-prefixed localization keys such as `feed.empty.title` and
role-bearing asset names such as `FeedPlaceholder`. Use `common` only for genuinely
shared resources (for example `common.cancel`). Keep SDK/system symbol names and
generated accessors unchanged. These are project conventions, not mandatory Apple
key or asset formats. Preserve ownership by the native client.

Use localized accessibility labels for assistive technology. Add stable
`accessibilityIdentifier` values only where automation needs them, following
[verification](verification.md#test-and-fixture-conventions); never substitute
machine identifiers for spoken labels.
