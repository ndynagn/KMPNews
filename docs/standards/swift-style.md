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

Use explicit `@MainActor` observable native ViewModels, even when one target's
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

Use client resources/localization and platform tokens. Do not hardcode production
copy or invent designs when a task requires an exact supplied design. Inspect the
referenced design and validate meaningful controls, long content and text scaling.

See [verification](verification.md) for targeted formatting and platform checks.
