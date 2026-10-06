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

Read [code documentation](docs/standards/code-documentation.md) when changing contracts,
comments or exceptions; use the [review checklist](docs/standards/review-checklist.md)
before delivery.
Read the linked language standard when changing that language, and the
architecture standard for new features or changes to boundaries/public APIs.
Read the [accepted stack](docs/standards/technology-stack.md) before dependency
or integration work. Android/Desktop feature work also requires sharedUI rules;
read the host module rules when changing platform integration or lifecycle.
Feature-specific contracts refine these rules; see the
[feature instruction template](docs/templates/feature-agents.md).

## Feature contract routing

Read the relevant contract when changing its behavior, implementation or tests,
including across source sets. Scope below routes review; it does not promise UI parity.

| Contract | Owning and affected areas |
| --- | --- |
| [News feed](docs/contracts/news-feed.md) | sharedLogic, sharedUI, Android integration, iOS; other shared API consumers when affected |
| [Auth](docs/contracts/auth.md) | sharedLogic, sharedUI, Android/iOS, Supabase configuration |
| [Profile](docs/contracts/profile.md) | sharedLogic, iOS/iPadOS, private avatar storage |
| [Favorites](docs/contracts/favorites.md) | sharedLogic, iOS/iPadOS, account-owned server data |
| [Search](docs/contracts/search.md) | sharedLogic, iOS/iPadOS, mediator compatibility |
| [Article grid](docs/contracts/article-grid.md) | iOS/iPadOS News, Search and Favorites |
| [Article details](docs/contracts/article-detail.md) | iOS/iPadOS readers and section navigation |
| [iOS components and catalog](docs/contracts/ios-component-catalog.md) | iOS design system, production consumers and Debug catalog |
| [iOS error feedback](docs/contracts/ios-error-feedback.md) | iOS/iPadOS ViewModels, lifecycle and feedback modifier |

## Documentation lifecycle and naming

- Keep permanent documents only for reusable rules, feature contracts or guides
  that supplement these instructions. Update an existing owner first; create a
  document only for a distinct responsibility and link it from the applicable rules.
- Use English lower-kebab-case names: `docs/standards/<topic>.md`,
  `docs/contracts/<feature>.md`, `docs/guides/<topic>.md` and
  `docs/templates/<purpose>.md`. Create directories only when needed.
  `AGENTS.md`, `CLAUDE.md` and `README.md` retain their conventional names.
- Report check/review results and run-specific limitations in chat or the PR/MR.
  Do not create permanent audit, validation, verification, adoption or design-QA
  reports unless the user explicitly requests one. The reusable verification
  standard defines procedures; it is not a history of runs.
- Keep logs, screenshots, recordings and execution manifests in a temporary
  directory outside the repository. Keep still-used original design references
  separately as `docs/assets/<feature>-reference.<ext>`, linked by their contract.
- A requirement to record or document something does not require a new file.
  Put lasting decisions, architecture exceptions and long-term blockers with their
  exact unblock conditions in the owning contract/standard; put run results in chat/MR.
- Preserve required tests, independent review and visual evidence. Temporary
  evidence must still identify the checked state and be available for the current
  review; historical reports are not proof that the current code passes.
- Keep contracts current: remove superseded behavior and execution history, retain
  active constraints and acceptance criteria, and update links when moving files.

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
- Select MVVM, MVI inside ViewModel or MVI with separate transitions using the
  architecture standard. Record the concrete reason in the feature contract;
  reducers and MVI frameworks are not mandatory for coordinated requests.
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
- Follow the documentation standard for API contracts, explanatory comments,
  TODO/FIXME and justified exceptions. Keep documentation current with behavior.

## Applying standards during implementation

- Before editing, identify the rules applicable to the affected files. Translate
  them into concrete implementation constraints for naming, declaration organization,
  semantic spacing, responsibilities, documentation and test structure.
- Apply these constraints while writing code. Do not defer readability and
  organization to formatting or a later cleanup pass.
- When changing a declaration's responsibility or supported states, review its
  name, documentation and callers for continued accuracy.
- Existing nearby code is not an exemption from the applicable standards.
  Preserve unrelated code, but do not copy or extend a noncompliant pattern.
- A formatter or linter PASS establishes only the checks that tool performs.
  Never present it as evidence of full code-style or architecture compliance.

## Review before delivery

- After the last edit, review the task-specific final changes against the
  applicable standards and [review checklist](docs/standards/review-checklist.md).
  Include added and untracked files, tests, resources and configuration.
- Record the initial task state before editing. In a dirty checkout, compare
  against that state to distinguish this task's changes from existing work;
  the entire working-tree diff is not a substitute. For a branch prepared for
  PR/MR, also include committed changes relative to the agreed target base.
  Directly compare task-owned ignored files that Git diffs omit.
- Check semantic spacing, multi-step blocks, naming after responsibility changes,
  declaration organization, component ownership, test structure and documentation.
  Manually verify the existing [test conventions](docs/standards/verification.md#test-and-fixture-conventions),
  including Arrange–Act–Assert; formatter success cannot replace this review.
- Correct violations introduced or extended by this task before delivery.
  Report unrelated findings separately without broadening the change.
- Report automated checks, manual self-review and independent review separately,
  with their actual scope, resolved findings, remaining findings and limitations.
  A separate persistent review document is not required for every task.

## Independent review of completed work

### Trigger and ownership

- Independent subagent review is required before declaring a new or extended
  feature complete, delivering a substantial refactor, or delivering changes to
  cross-module contracts, component responsibilities, state coordination or lifecycle
  coordination. Assess significance by behavior and boundaries, not line counts.
- Start review after implementation and tests are formed and the primary agent
  has completed self-review, targeted formatting and available relevant checks.
  Do not launch reviewers for every intermediate edit or checkpoint commit.
- Documentation-only changes, mechanical formatting and local fixes without the
  impacts above require self-review. Use subagents for these changes only when
  explicitly requested or when substantial impact is discovered.
- Assign one independent reviewer per affected module: `sharedLogic`, `sharedUI`,
  `androidApp`, `desktopApp`, `iosApp` and `macosApp`. Include the owning module's
  tests, resources and configuration. A reviewer must not be the author of the
  implementation being reviewed.
- For unchanged consumers of a changed contract, limit review to the affected
  integration. Do not audit every client in full. Shared Compose changes belong
  to `sharedUI`; add host reviewers when host integration is affected.
- Explicitly assign root configuration and files outside modules by responsibility
  to an appropriate reviewer; add a separate reviewer if no existing owner fits.
  Keep coverage complete without duplicate reviews of the same responsibility.
- Run independent module reviews in parallel within available agent slots; queue
  remaining reviews in subsequent groups. Reviewers must not delegate further.

### Review and closure

- Give each reviewer the task goal, comparison baseline, exact change scope,
  applicable instructions and related contracts. Identify the reviewed commit or
  file snapshot, including uncommitted and new files. Review changed declarations
  and necessary caller context, not only diff lines.
- Reviewers do not edit files. The primary agent coordinates builds and tests;
  do not run competing processes against shared build outputs.
- Findings must identify a file and line, the applicable rule, an explanation and
  a proposed correction. Do not present personal preferences as required standards.
  Report the reviewed scope and limitations even when no findings remain.
- The primary agent consolidates findings, resolves conflicts using the applicable
  rules and fixes confirmed violations. Record a reasoned disposition for rejected
  findings; do not silently discard them or waive a rule violation.
- After corrections, have reviewers recheck changed areas and affected dependencies
  against the updated state. Repeat full review only when the reviewed solution
  changes substantially. The primary agent performs final self-review after the
  last edit and reruns checks relevant to the corrections.
- Before the final feature commit or opening/updating a PR/MR, verify that review
  still covers the actual commit or file state. Reuse current results for unchanged
  code; changes invalidate coverage of the affected areas and dependencies.
- If required subagents are unavailable, complete available checks and report
  independent review as incomplete. Do not declare the feature ready, make its
  final commit or open/update its PR/MR until independent review completes or the
  user explicitly grants an exception. This review policy does not authorize
  commits, pushes or PR/MR creation by itself.

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
