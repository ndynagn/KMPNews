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
- Follow the role names below for domain, transport and storage types. Related
  nested DTOs may share their owning contract file; independent contracts should
  not be bundled together.
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
Follow [code documentation](code-documentation.md) for contract coverage and comments;
avoid narrating obvious syntax.

Formatting is explicit and restricted to intended files during adoption. See
[verification](verification.md) for commands and existing-debt handling.

## Presentation contract naming

Use feature-prefixed files such as `FeedScreen.kt`, `FeedViewModel.kt` and
`FeedUiState.kt`. Project-owned MVI uses `FeedEvent.kt` and `FeedSideEffect.kt`
when outputs exist. Add a reducer and internal messages only when the architecture
selection procedure justifies separate transitions. Prefer sealed interfaces for
payload-bearing Event/SideEffect alternatives; enums suffice for payload-free inputs.
Use sealed state variants for exclusive modes and immutable data for coexisting details.

Expose `StateFlow<FeedUiState>` rather than MutableStateFlow. Use explicit action
methods for MVVM and `onEvent(event: FeedEvent)` for project-owned MVI. Keep async
coordination in ViewModel; simple MVI updates state directly in its handlers.
Shared Compose contracts live in sharedUI, never sharedLogic. Preserve external
framework naming without wrappers solely for vocabulary alignment.

Use constructor parameters with meaningful names for Koin-provided collaborators.
DI resolution syntax belongs to composition, not domain/data methods. DTOs and
Room entities use role-specific names and explicit mappings into domain models;
do not add serialization/database annotations to domain to avoid a mapper.

## Role names, acronyms and data operations

Use role-bearing implementation names such as `OfflineFirstNewsRepository` and
`NewsDataClient`. Do not require `I` prefixes or `Impl` suffixes. Domain uses
`Article`, transport uses `ArticleResponseDto`, storage uses `ArticleEntity`;
these names communicate boundaries without moving data types into domain.

Use Kotlin-style spellings such as `HttpClient`, `ArticleDto` and `articleUrl`.
For acronym details follow Kotlin conventions (for example `IOStream` for a
standalone two-letter acronym). Preserve SDK/library/imported names; do not rename
foreign APIs or add wrappers solely for capitalization. Swift has its own spelling.

The following is a project vocabulary for data/domain APIs, not a universal Kotlin
or third-party API requirement:

| Verb | Meaning |
| --- | --- |
| `observe` | A stream of values; document source, lifetime and failure behavior |
| `read` | A one-shot storage read |
| `fetch` | A request to a remote source |
| `refresh` | An update of data; document source, persistence and coordination |
| `get` | Does not promise a source or cost; the contract must make these clear |
| `load` | Requires clear contextual purpose and documented behavior |

A name does not prove cancellation, caching or network semantics. Document those
using [code documentation](code-documentation.md). These conventions apply to new
and modified contracts, not an automatic rename of all existing APIs.

Simple mapping belongs in data as `toDomain()`/`toEntity()` functions. Introduce a
mapper class when dependencies or cohesive complex behavior justify it. Do not
import DTOs or storage entities into domain just to host a conversion.

Illustrative data-layer extension with surrounding model declarations omitted:

```kotlin
internal fun ArticleResponseDto.toDomain(): Article = Article(id = id, title = title)
```

Use explicit field mapping; real feature mapping must cover its actual contract,
including nullable values, validation and errors. This abbreviated example is not
a replacement for the feed's existing mapper.

## Declaration organization

Within a class use properties/init blocks, secondary constructors, methods, then
companion object. Keep initialization semantics intact. Put related methods and
overloads together, with principal operations before their supporting logic;
do not sort alphabetically or mechanically by visibility. Preserve interface
member order in implementations when it improves readability. Put nested types
near their use when appropriate.

Small, tightly related declarations may share a file with a descriptive name.
Screen/ViewModel and independently reusable component separation still applies.
Extensions belong near their semantic owner or specific consumer; do not create a
miscellaneous `Extensions.kt` or a file collecting unrelated extensions by type.
This adapts [Kotlin source organization](https://kotlinlang.org/docs/coding-conventions.html#source-file-organization).

## Compose and Android resource naming

Use feature-prefixed snake_case for resource entries and own resource filenames
where the platform permits them: `feed_empty_title`, `feed_retry`.
Use `common_` only for genuinely shared resources, not as a default prefix.
Keep framework-required names, generated identifiers and qualifier directories in
their native format. Shared Compose resources belong to sharedUI; platform-only
resources stay with their host. Resource naming is a project convention.

UI-test identifiers follow [verification](verification.md#test-and-fixture-conventions).
They are not accessibility labels and do not replace semantics/content descriptions.
