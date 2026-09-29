# Agent instruction and formatting adoption

Status: **Completed and accepted for delivery to dev** (2026-09-30).

This is the historical initial rollout report. Its former sharedUI restrictions
and routing conclusions are superseded by the
[accepted stack](standards/technology-stack.md) and
[stack instruction update](stack-architecture-adoption.md). Preserve the original
verification evidence below; it does not validate the newly selected dependencies.

Implemented on 2026-09-29 in `features/agent-guidelines`, based on `dev` at
`7db7476`. The user requested task closure and commit/push to `dev` on 2026-09-30.
Closure retains the documented verification limits: Claude's fresh-session check
requires working authentication, and existing formatting debt is intentionally
retained. Neither is reported as a passing check.

The delivery includes the subsequent user-approved rollback to `project.pbxproj`.
Actual Android Studio Sync, Kotlin Xcode diagnostics and both Apple Debug builds
passed after rollback; see [format history](native-macos-implementation.md).

## Delivered scope

- Canonical root instructions and six module scopes, each with an adjacent
  `CLAUDE.md` containing only `@AGENTS.md`. There are no symlinks or global settings
  changes. Root routing explicitly requires module/descendant discovery before edits.
- Accepted [architecture](standards/architecture.md),
  [Kotlin](standards/kotlin-style.md), [Swift](standards/swift-style.md) and
  [verification](standards/verification.md) standards, plus an inactive
  [feature template](templates/feature-agents.md). No placeholder features exist.
- EditorConfig, Spotless 8.10.3 with ktlint 1.8.0, and an Xcode-bundled Swift
  formatter profile. Formatting is explicit and is not attached to application
  builds or CI. Only the changed root Gradle script was formatted.
- Synchronized macOS folder exclusions for AGENTS.md and CLAUDE.md in the existing
  Xcode project. The later JSON rollback retained these exclusions in
  `project.pbxproj`; see [migration history](native-macos-implementation.md).
  iOS module instructions live outside its synchronized
  Swift source folder. Future instructions inside synchronized folders need exclusions.
- Research is clearly marked historical and links to accepted rules. Its original
  language is retained as provenance; all newly authored standards are English.

Application sources, public Kotlin APIs, platform targets and application
dependencies remain unchanged. Android/Desktop sharedUI and the iOS direct
Greeting call remain documented transitional exceptions.

## Verification results

| Check | Result and evidence |
| --- | --- |
| Seven instruction scopes | PASS: canonical files exist; all seven adjacent Claude imports resolve to the matching AGENTS file |
| Instruction size | Root plus each module is below the default 32 KiB Codex instruction budget |
| Fresh Codex session from sharedLogic | PASS: identified root and module files, dependency direction, forbidden shared presentation and the second-consumer rule |
| Fresh Codex session from the repository root | PASS: discovered macosApp rules and Swift standards for a lifecycle scenario; correctly described MainActor, cancellation, window ownership and domain-aware component extraction |
| Fresh Claude Code 2.1.42 session from macosApp | UNVERIFIED: CLI returned HTTP 401, `OAuth access token is invalid`, before model inference; structural import checks are not proof of runtime loading |
| Gradle configuration and changed script | PASS: `./gradlew help spotlessCheck -PstyleFiles=build.gradle.kts` |
| Negative formatter samples | PASS: Kotlin and Swift independently reject bad indentation, trailing whitespace and missing final newline; checks leave sample bytes unchanged |
| Positive samples and idempotence | PASS: explicitly formatted Kotlin/Swift samples pass strict checks; second apply leaves bytes unchanged |
| Kotlin naming compatibility | PASS: temporary UI-emitting `@Composable` PascalCase function and `Platform.ios.kt` filename are accepted |
| Semantic spacing | PASS: temporary Kotlin/Swift samples retain declaration, guard and return phase boundaries |
| Targeted isolation | PASS: formatting the selected Kotlin fixture leaves an unselected malformed fixture unchanged; Swift formatting uses explicit paths |
| Selector validation | PASS: missing and empty `styleFiles` fail instead of broadening the requested scope |
| Application task graph | PASS: Android/Desktop compile dry-run contains no formatter tasks; this is task-graph evidence, not an Android/Desktop compile result |
| Native macOS Debug | PASS: Xcode builds the affected target with the instruction exclusions; the resulting KMPNews.app contains neither AGENTS.md nor CLAUDE.md |
| Scope and whitespace | PASS: application sources match the base; `git diff --check` is clean |

Temporary fixtures were removed after testing. No fixture code was added to the
application. Formatting samples validate syntax/layout only, not compilation of
hypothetical product features.

The macOS build command was:

```sh
xcodebuild -project iosApp/iosApp.xcodeproj -scheme macosApp -configuration Debug \
  -destination 'platform=macOS,arch=arm64' \
  -derivedDataPath /tmp/kmpnews-agent-guidelines-build build
```

Tested toolchain: Gradle 9.7.1, JDK 21, Kotlin 2.4.20, Xcode 27.0 (27A266a),
Swift 6.4 with the project's Swift 5 language mode. The bundled swift-format
version command reports `main`. Codex CLI was 0.151.0. Read-only Codex probes used
`--ignore-user-config --ephemeral --sandbox read-only` to avoid an incompatible
inherited model configuration; no user configuration was rewritten.

## Known full-repository formatting debt

`./gradlew spotlessCheck --continue` fails on existing starter files. Both
`spotlessKotlinCheck` and `spotlessKotlinGradleCheck` report violations. This is
an expected adoption baseline, not a passing full audit.

- Gradle scripts: `androidApp/build.gradle.kts`, `desktopApp/build.gradle.kts`,
  `settings.gradle.kts`, `sharedLogic/build.gradle.kts`, `sharedUI/build.gradle.kts`.
  Findings include indentation, trailing commas, trailing whitespace and final newlines.
- Kotlin: Android MainActivity; sharedLogic Greeting, GreetingUtil, Platform and
  Android/iOS/JVM platform files; sharedLogic Android-host/common/iOS/JVM/macOS
  template tests; sharedUI common test. Findings include final newlines, colon
  spacing and expression-body/layout normalization.
- Swift full audit fails for missing final newlines in
  `iosApp/iosApp/ContentView.swift` and `iosApp/iosApp/iOSApp.swift`.
  Existing macOS Swift files pass the configured lint.

Use the read-only commands in the verification standard for current details.
Do not run an unscoped apply to clear this baseline as part of unrelated work.
Gradle also emits a deprecation warning about compatibility with Gradle 10;
this task does not upgrade Gradle or claim to resolve that warning.

## Targeted formatter implementation

The attempted Spotless `spotlessFiles` option did not restrict this pinned
integration. Verification caught edits outside the intended file; those formatter
edits were restored before continuing. The repository now owns the documented
literal `-PstyleFiles=path1,path2` selector, applied directly to both formatter
targets. Positive/negative checks and an unchanged sentinel verified isolation.
Do not substitute the unverified option for this repository selector.

## Routing review and remaining checks

The four scenarios in the verification standard were reviewed against the actual
instruction hierarchy:

1. Shared article ordering routes to sharedLogic and the feature contract;
   policy belongs in domain, with deterministic tests and affected consumers.
2. An Android screen routes to androidApp and Kotlin guidance; it uses a native
   lifecycle-owned ViewModel and does not extend the sharedUI starter.
3. Swift task cleanup routes to the owning Apple client and Swift guidance;
   actor, window/screen owner and cancellation determine acceptance.
4. A card's second independent consumer triggers extraction to the nearest
   feature/reusable presentation/platform UI-kit scope according to its coupling.

Deferred verification follow-up: after Claude authentication is repaired, repeat the
fresh-session read-only instruction check from the root and macosApp. It must
identify root plus applicable module rules through the adjacent imports.

iOS Simulator, Android/JVM runtime, macOS launch/visual interaction, Release,
shared behavioral tests and distribution signing were not rerun in this stage.
No application behavior or Swift/Kotlin source changed; the relevant application
check here was the macOS Debug rebuild and resource exclusion inspection. These
results do not replace the previous migration stage's evidence or establish
App Store signing readiness. CI enforcement and bulk normalization remain
separate tasks.
