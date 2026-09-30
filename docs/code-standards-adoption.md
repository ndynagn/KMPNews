# Code standards clarification: adoption record

Date: 2026-09-30. Branch: `features/news-feed-data-domain`.
Status: documentation changes implemented and structurally reviewed; no commit/push.

## Delivered

- [Documentation policy](standards/code-documentation.md): mandatory coverage,
  KDoc/Swift syntax, contract versus rationale, TODO/FIXME and narrow exceptions.
- [Kotlin](standards/kotlin-style.md) and [Swift](standards/swift-style.md): naming,
  declaration organization and platform resource conventions.
- [Verification](standards/verification.md#test-and-fixture-conventions): test
  names, doubles, fixture placement and stable automation identifiers.
- [Review checklist](standards/review-checklist.md): human review responsibilities
  alongside configured mechanical checks.
- Root/module routing links and the existing feature template updated; adjacent
  CLAUDE files continue importing their local AGENTS without duplicated rules.

Examples cover contract documentation, role names, data mapping, behavioral tests,
TODO context and an exception rationale. API/test fragments are explicitly excerpts
with surrounding declarations omitted, not new product APIs or standalone samples.
Sources and project-specific choices are distinguished, including the intentional
narrowing of Swift's recommendation to document every declaration.

## Validation

| Check | Result and scope |
| --- | --- |
| Local Markdown links and heading anchors | Passed for all documents changed in this task |
| CLAUDE imports | Every existing CLAUDE is exactly `@AGENTS.md` and has a sibling AGENTS |
| Diff boundaries | Per-file SHA-256 comparison against the start-of-task snapshot confirms documentation-only changes; earlier feed/Koin work preserved |
| Whitespace | UTF-8/LF, final newlines and no trailing whitespace checked; `git diff --check` passed |
| Examples | Reviewed against current language/style rules; no claim of standalone compilation for excerpts |
| Apple membership | Existing macOS AGENTS/CLAUDE exclusions retained; no new files added inside Apple synchronized folders |
| Architecture and scope | No new libraries, UI behavior, public APIs, formatter configuration, hooks or CI gates |

Routing was reviewed by following the links for five scenarios:

1. Shared repository: root, sharedLogic and common feature contract reach Kotlin
   operation names, data mapping and documented errors/cancellation.
2. Compose screen: sharedUI plus host rules reach resource naming, state ownership
   and test identifiers without introducing Swift presentation sharing.
3. Swift lifecycle: owning Apple rules reach actor/task documentation and existing
   window/screen cleanup requirements.
4. Resource changes: the owning module reaches the matching language standard;
   framework names remain intact and labels are distinct from test identifiers.
5. Feature tests: module rules route explicitly across source sets to the shared
   feature contract, with fixtures in test scope and language-aware naming.

This is structural routing/review evidence, not a fresh Codex/Claude session test.
Application builds and behavioral tests were not rerun for this documentation-only
change. Existing code was not mass-audited, renamed or reformatted; compliance of
all existing code is not claimed. New/modified code uses these standards, and
remaining semantic correctness is reviewed rather than inferred from a formatter.
