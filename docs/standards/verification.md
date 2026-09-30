# Verification and local style checks

## Tooling and adoption

Kotlin/Kotlin DSL: Spotless 8.10.3 and ktlint 1.8.0, pinned in the version catalog.
Swift: Xcode's bundled `xcrun swift-format`, tested with Xcode 27.0 (27A266a),
Swift 6.4, Swift 5 project language mode. Its version command reports `main` on
this installation, so record `xcodebuild -version` and `xcrun swift --version`
as well. No global Xcode switch or formatter installation is required.

Checks never format. Application builds do not apply formatting, and full style
checks are not attached to `check` during adoption. CI/hooks and whole-repository
normalization are separate work. Root Gradle configuration, handwritten Kotlin
under module source folders, and Gradle scripts are covered. Generated/build,
cache, vendor and third-party sources are excluded. Swift commands use explicit
handwritten source paths; do not recursively lint build products or dependencies.
Preserve Xcode's serialization of `project.pbxproj`; exclude it from generic formatters.
Changes to the Xcode project format also require a real IDE Sync, not only CLI builds.

### Full read-only audit

```sh
./gradlew spotlessCheck --continue
xcrun swift-format lint --strict --configuration .swift-format iosApp/iosApp/*.swift macosApp/*.swift
```

The Swift command covers today's starter files. For future nested features pass
their explicit source paths too. Full audits may fail on pre-existing debt; report
that separately from the check of files touched by a task. Never suppress that
failure or report the entire repository as clean.

### Targeted check and explicit formatting

Use the repository's `styleFiles` property: comma-separated literal repo-relative
paths, not regexes or shell globs. It rejects empty, missing, unsupported and
build/generated/vendor paths. Quote the property if paths contain spaces. Do not
use Spotless's `spotlessFiles` option: it did not narrow this pinned integration
in verification. A targeted check must include every changed source file.

```sh
# Select only the root script; list multiple paths separated by commas.
./gradlew spotlessCheck -PstyleFiles=build.gradle.kts
./gradlew spotlessApply -PstyleFiles=build.gradle.kts

# Example selecting a specific Swift file. format is explicitly mutating.
xcrun swift-format lint --strict --configuration .swift-format macosApp/ContentView.swift
xcrun swift-format format --in-place --configuration .swift-format macosApp/ContentView.swift
```

Select all added/modified handwritten source/script files, including untracked
ones; inspect `git diff` afterwards. Do not apply a formatter to unrelated legacy
files. Recheck after applying and ensure a second apply produces no changes.
Formatting does not enforce architectural boundaries or infer semantic spacing.

## Behavioral verification matrix

| Change | Minimum relevant evidence |
| --- | --- |
| Docs/instructions | Link/scope/import checks, representative routing review; no application tests claimed |
| Formatter/build tooling | Gradle configuration, positive/negative temporary samples, idempotence, scoped-file isolation, no application auto-format task |
| Shared domain/data | Relevant deterministic tests and affected-target compilation |
| Exported shared API / interop | Kotlin client and both Swift-client compilation; async/error/cancellation runtime scenario when changed |
| Compose or Swift ViewModel | MVVM/MVI transitions, expected errors, cancellation, effect delivery and ownership with controlled dependencies |
| Client UI | Build, launch, scenario, accessibility and visual check at relevant widths/text sizes |
| Desktop/window lifecycle | Close/reopen, independent windows when supported, focus/keyboard and task disposal |
| Xcode membership/configuration | Target/settings diff, affected build and bundle membership; signing/export verified separately |

Useful existing commands (run from the repository root):

```sh
./gradlew :sharedLogic:macosArm64Test :sharedLogic:iosSimulatorArm64Test :sharedLogic:jvmTest
./gradlew :sharedLogic:testAndroidHostTest
./gradlew :androidApp:compileDebugKotlin :desktopApp:compileKotlin
./gradlew :androidApp:assembleDebug
xcodebuild -project iosApp/iosApp.xcodeproj -scheme macosApp -configuration Debug \
  -destination 'platform=macOS,arch=arm64' -derivedDataPath /tmp/kmpnews-check build
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug \
  -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath /tmp/kmpnews-check build CODE_SIGNING_ALLOWED=NO
```

Discover available Simulator IDs with `xcrun simctl list devices available` for
launch/interaction; a generic destination supports building, not launching.
Discover new tasks with Gradle task listings rather than inventing names. Do not
run multiple Gradle/Xcode processes against shared outputs concurrently.

Starter arithmetic tests do not prove news-reader behavior. Existing feed tests
cover their recorded contracts; their success does not prove new behavior. New
behavior needs meaningful assertions, controlled clock/network and fakes. Real service smoke checks supplement deterministic tests.
If an environment is unavailable, report the limitation, not PASS.

## Dependency and interop admission gate

The [accepted stack](technology-stack.md) is a selection, not a passing build.
Before feature implementation relies on newly connected dependencies, pin and
validate the smallest integration slice with the current toolchain:

1. Resolve the complete graph for Android, JVM, iosArm64, iosSimulatorArm64 and
   macosArm64. Check compiler plugins, Room/KSP/SQLite, Ktor engine/transitive
   versions and Swift packages together; metadata alone is insufficient.
2. Compile both Compose clients and both Swift clients; compile/link the iOS
   device framework too. Perform real IDE Sync after build integration changes.
   Record exact versions, commands and environment, with unavailable checks marked.
3. Verify Koin construction using controlled dependencies, Ktor serialization and
   failure mapping with fixtures, and real Room create/write/read/reopen behavior
   on Android, JVM, iOS Simulator and native macOS. Add schema/migration checks
   when there is a real schema; a fake DAO does not verify the SQLite driver.
4. In both Swift clients, consume typed shared models and a Flow; exercise suspend
   success, expected failure and cancellation. Cancel a Swift task and verify the
   Kotlin job/collector terminates. Verify resubscription and absence of observers
   after screen/window disposal. Test explicit Flow failure values without allowing
   an exception to escape into Swift.
5. For presentation dependency integration, verify Navigation3 back-stack ownership
   and image loading/failure/cancellation on affected clients. Desktop close/reopen
   and independent windows must not leak tasks or dispose app-scoped services.

Run shared deterministic tests plus the relevant native/runtime checks. Use
provider fixtures independent of credentials; an optional real API smoke does
not replace those tests. Keep template arithmetic test success separate. A failed
gate blocks reliance on that integration, not unrelated documentation or domain
work. Do not change toolchains to make a gate pass without a separate decision.

## Instruction loading and review scenarios

Start fresh sessions from the repository and a relevant module. Ask the agent to
identify loaded instruction paths and applicable constraints without editing code.
Codex must read scoped files before cross-module work even if launched at root.
Claude loads the adjacent `@AGENTS.md` imports without copied rule text. Do not
modify user-wide settings to validate this repository.

| Scenario | Expected routing and decision |
| --- | --- |
| Change shared article ordering | Root + sharedLogic + relevant feature contract; shared domain owns the rule; deterministic tests; check consumers if public API changes |
| Add a shared Compose screen | Root + sharedUI + Kotlin + feature contract; shared ViewModel and chosen MVVM/MVI; Android/Desktop rules for host integration; both client builds and UI evidence |
| Fix a Swift task leak | Root + owning Apple client + Swift + feature lifecycle; cancellation/owner validation; no assumption that deinit alone is enough |
| Close a Desktop window | Root + desktopApp + sharedUI + feature lifecycle; dispose window/navigation ViewModel ownership and collectors, keep other windows and app services alive |
| Reuse a card on a second independent screen | Root + presentation owner + feature rules; nearest feature/component/UI-kit scope; sharedUI for Compose, client-local Swift components |

Keep source-level product tests separate from these instruction-routing checks.
See [initial adoption results](../agent-guidelines-adoption.md) for historical
evidence and formatting debt, and [stack update results](../stack-architecture-adoption.md)
for the current routing review. Structural import validation is not proof of a
fresh Codex/Claude runtime session; report those checks separately.

## Test and fixture conventions

Use descriptive lowerCamelCase test names that explain behavior, for example
`refreshPreservesCacheWhenNetworkFails`. Keep Kotlin tests portable across current
targets; naming examples in platform documentation do not override this choice.
For Swift retain test-discovery syntax required by the selected framework (such
as an XCTest `test` prefix); this policy does not introduce a testing framework.

Organize tests as Arrange–Act–Assert with semantic spacing. Literal section
comments are optional. A reader should see the important inputs, action and
expected behavior without reconstructing an elaborate fixture abstraction.
Test observable contracts; verify interactions when they themselves are material,
such as no network request during local observation. Do not mirror private logic.

Use Fake for a simplified working implementation, Stub for configured responses,
and Mock for configured interaction expectations/verification. Framework-specific
names remain unchanged. Prefer an appropriate existing test facility and choose
the double for the scenario; these rules do not mandate a mocking library.

Keep fixtures with the feature's tests in the applicable source set. Extract shared
test helpers only after actual reuse; do not move test-only fixtures into production
sources merely to share them. The existing opt-in Apple interop harness remains a
verification boundary, not permission to export fixtures in ordinary frameworks.

Illustrative repository-test body (setup/helpers and model definitions omitted):

```kotlin
@Test
fun refreshPreservesCacheWhenNetworkFails() = runTest {
    feedStore.write(cachedFeed)
    remoteSource.nextResult = PageResult.Failure(FeedFailure.NETWORK)

    val result = newsRepository.refresh()

    assertEquals(FeedUpdateResult.Failed(FeedFailure.NETWORK), result)
    assertEquals(cachedFeed, feedStore.read())
}
```

This is a behavioral example, not a prescribed test-helper API. A fixture should
keep the cached content relevant to the assertion visible in the actual test.

### UI identifiers

Add Compose `testTag` or Swift `accessibilityIdentifier` where automation or
ambiguous elements need it, not to every View preemptively. Use stable semantic
names such as `feed.retryButton` and `feed.article.<id>`. The latter requires a
stable, non-sensitive identifier; do not embed secrets, personal data or changing
list positions. Keep identifiers independent from localized visible/accessibility
labels. These formats are project decisions.

### Sources and enforcement

[Android test doubles](https://developer.android.com/training/testing/fundamentals/test-doubles)
explains substitution techniques; Google’s
[Test Behavior, Not Implementation](https://testing.googleblog.com/2013/08/testing-on-toilet-test-behavior-not.html)
supports checking observable behavior. Our name format, Arrange–Act–Assert layout
and fixture placement are explicit project choices.

Use the [review checklist](review-checklist.md) for naming, documentation and
architectural review. Current formatters enforce their configured mechanical rules;
they do not prove semantic names, documentation completeness or architecture.
No new linter, hook, CI gate or whole-repository cleanup is introduced by these
standards. Apply them to added/modified code and report existing debt separately.
