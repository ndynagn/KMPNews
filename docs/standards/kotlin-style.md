# Kotlin and Gradle Kotlin DSL style

Use the repository EditorConfig and pinned Spotless/ktlint versions. Follow
[Kotlin conventions](https://kotlinlang.org/docs/coding-conventions.html) with the
project choices below. Mechanical formatting is necessary but not a substitute
for readable responsibilities and names.

## Layout and semantic spacing

- Four spaces, no tabs, LF, UTF-8, final newline, no trailing whitespace. Target
  120 columns; do not break indivisible URLs/literals into obscure constructions.
- One blank line between functions/types and independent semantic phases. Related
  properties/local declarations form a contiguous group. Separate that group
  from executable work or return. A new declaration after executable work starts
  a new phase when it represents a distinct step.
- Separate independent guards/blocks with one blank line. Keep comments attached
  to what follows, separated from the previous phase. No empty line directly
  after an opening brace, before a closing brace, or repeated blank lines.
- Do not split annotations from declarations, if/else or try/catch clauses,
  cohesive when branches, argument/collection lists, modifier chains or related
  Compose DSL content just to satisfy a mechanical blank-line interpretation.
- Multiline parameter/argument lists use one element per line and trailing commas.
  Use ordinary four-space continuation indentation, not manual column alignment.
- Use block bodies for multi-step work. Prefer expression bodies for simple
  expressions and mappings. UI-emitting Composables normally use block bodies;
  do not translate Flutter's universal callback expression-body ban into Kotlin.

```kotlin
fun visibleTitle(article: Article): String {
    val title = article.title.trim()
    val sourceName = article.sourceName

    if (title.isEmpty()) {
        return sourceName
    }

    return title
}
```

This illustrates grouping only; fallback business behavior must come from the
feature contract. A formatter can cap blank lines, but it cannot infer phases.

## Naming, files and visibility

- Types and primary files use PascalCase (`Article.kt`, `NewsRepository.kt`).
  Functions/properties/parameters use lowerCamelCase; constants use UPPER_SNAKE_CASE.
  Package segments are lowercase, without underscores/hyphens. Preserve existing
  module names and entry-point exceptions unless their migration is authorized.
- Platform top-level declarations keep suffixes such as `Platform.ios.kt`,
  `Platform.macos.kt` and `Platform.jvm.kt` to avoid JVM facade collisions.
- Use predicates (`isLoading`, `hasNextPage`, `canRetry`), descriptive collaborators
  (`newsRepository`) and role qualifiers for same-type dependencies. Avoid generic
  `repository`, `manager` or unexplained abbreviations when intent is ambiguous.
- Use `private`, not Dart-style underscores. `_state` is allowed only as the
  private mutable half of a public read-only backing-property pair.
- Domain concepts need no automatic Entity suffix. Transport types use descriptive
  `ArticleResponseDto`/`RefreshRequestDto` names. Related nested DTOs may share the
  owning contract file; independent contracts should not be bundled together.
- A screen and its ViewModel are separate. Keep private one-owner helpers and
  previews local; apply the second-consumer extraction rule from architecture.
- Minimize visibility and exported APIs. Prefer `val` and read-only collections;
  read-only interfaces alone do not make a mutable backing collection immutable.
- Use explicit imports, with no wildcard imports. Let ktlint order imports.
  Test names use descriptive lowerCamelCase so common tests stay portable.

## Compose and clarity

UI-emitting, Unit-returning Composables use PascalCase (`ArticleCard`); ordinary
value-returning helpers use lowerCamelCase. The ktlint naming exception for
`@Composable` enables the former, while review still checks return/value semantics.
Use state and callbacks, a useful caller-supplied Modifier, stable keys where
needed, and named arguments when they explain ambiguous literals.

Avoid nested let/run/apply/also chains that obscure control flow, `!!` without a
proved invariant, unexplained magic values and blanket suppression annotations.
Extract repeated semantic values to constants/tokens; literals like zero or a
single local spacing choice do not require a global constants hierarchy.
Document public domain behavior and errors; comments explain why, not each line.

Formatting is explicit and restricted to intended files during adoption. See
[verification](verification.md) for commands and existing-debt handling.

## Presentation contract naming

Use feature-prefixed files such as `FeedScreen.kt`, `FeedViewModel.kt` and
`FeedUiState.kt`. For MVI, add `FeedIntent.kt`, `FeedEffect.kt` when outputs exist,
and `FeedReducer.kt` for the pure transition logic. Prefer sealed interfaces for
closed Intent/Effect alternatives and data classes for composable UiState; use
sealed state variants only for mutually exclusive cases. These names illustrate
the pattern, not a requirement to scaffold a feature or empty types.

Expose `StateFlow<FeedUiState>` rather than MutableStateFlow; `_state` remains the
permitted private backing pair. Use explicit action methods for MVVM and a clearly
named `onIntent(intent: FeedIntent)` boundary for MVI. Keep side-effect execution
out of reducers and keep Flow collectors out of immutable UiState values.
Shared Compose contracts live in sharedUI, never sharedLogic.

Use constructor parameters with meaningful names for Koin-provided collaborators.
DI resolution syntax belongs to composition, not domain/data methods. DTOs and
Room entities use role-specific names and explicit mappings into domain models;
do not add serialization/database annotations to domain to avoid a mapper.
