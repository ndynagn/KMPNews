# Stack and architecture instruction update

Date: 2026-09-30. Branch: `features/stack-architecture-rules`, based on `dev`
at `4c019ab4bf722b331768b7ac2178bfda3a24cc5f`.
Status: **Documentation stage completed and accepted for delivery to dev**.
The user authorized commit and publication on 2026-09-30. The next preparation
stage starts from this delivery in `features/news-feed-data-domain`.

## Delivered scope

- Updated root and six module AGENTS scopes for shared Compose presentation and
  native Swift presentation over shared domain/data. Adjacent CLAUDE imports are
  unchanged; no feature directories or new Apple resource files were created.
- Updated architecture, Kotlin/Swift style, verification and the feature template
  for MVVM/MVI, explicit contracts, effects, concurrency, caching and pagination.
- Added the [accepted stack](standards/technology-stack.md) with sources, prototype
  limitations and separate research-versus-build compatibility status.
- Updated README routing and marked conflicting historical research/rollout rules
  superseded, preserving earlier validation results as historical evidence.

## Validation

| Check | Result |
| --- | --- |
| Relative Markdown links and explicit anchors | PASS: Python audit of 25 Markdown files, 95 local links |
| Seven canonical scopes and adjacent CLAUDE imports | PASS: regular files, exact imports; root plus each module below 32 KiB |
| Changed Markdown whitespace and Git diff check | PASS: UTF-8, LF, final newline, no tabs/trailing whitespace; git diff --check |
| Documentation-only diff and unchanged application/build/project files | PASS: tracked diff and untracked-file inventory contain only 17 Markdown documents |
| Scope/ownership scenarios below | PASS: reviewed routes and decisions against current root/module/standard text |

The link check covers repository Markdown links outside fenced examples and
validates local targets and explicit heading fragments. External primary sources
were read during stack research; this is not an exhaustive external-link audit.
Import validation checks real regular files and matching `@AGENTS.md` contents,
not fresh agent inference. Codex/Claude fresh-session loading was not rerun.

## Routing review

| Scenario | Rules consulted and expected result |
| --- | --- |
| Shared repository change | Root, sharedLogic, architecture and shared feature contract; domain owns the interface, data owns transport/storage/mapping; shared APIs remain Swift-usable |
| Compose screen | Root, sharedUI, Kotlin and feature contract; shared screen/ViewModel/contracts, MVVM or MVI by behavior; Android/Desktop rules govern platform ownership and integration |
| Swift subscription fix | Root, owning Apple module, Swift and feature lifecycle; MainActor Observable ViewModel, initializer injection, SKIE boundary, explicit task cancellation and failure values |
| Desktop window close | Root, desktopApp, sharedUI and lifecycle contract; window/navigation owner releases ViewModels and collectors without closing other windows' app services |
| Component's second consumer | Root, owning presentation module and feature; shared Compose component at nearest sharedUI scope, or Swift component in its client; generic UI kit does not own repositories |

These are reviews of the instruction routes, not application runtime tests.
Application builds, IDE Sync, Kotlin/Swift formatter runs and new-stack runtime
tests were not run for this Markdown-only change. Existing formatter debt remains
as recorded in the [initial adoption report](agent-guidelines-adoption.md).
No new dependency integration is claimed as passing. The
[admission gate](standards/verification.md#dependency-and-interop-admission-gate)
must be exercised during the next integration task.

## Next work

Verify the selected versions and minimum integration, then establish the NewsData.io
Free feed contract before implementing domain/data and deterministic tests. Auth,
favorites synchronization and a server proxy remain outside the first feed/cache
slice. No source, public API, dependency or Xcode membership change belongs to this
documentation delivery.
