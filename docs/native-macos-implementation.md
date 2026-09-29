# Native macOS implementation and JSON migration status

Status: **Completed** (2026-09-29).

Implemented on `features/native-macos-xcproj`, based on main `735400a`.
Accepted for delivery on `dev` by the user after successful native macOS and
JSON migration checks. Existing signing-team and Info.plist changes and the
documentation directory are preserved in the delivered snapshot.

Completion covers local development setup, native macOS integration and the
Xcode JSON migration. The validation limitations below remain explicit;
distribution signing and the later AGENTS rollout are separate work.

## Implemented

- Separate `macosApp` target and checked-in shared scheme in the existing
  `iosApp/iosApp.xcodeproj` container. Root `macosApp/` synchronized folder is
  assigned only to that target; iOS folder and its Info.plist exception remain.
- Apple Silicon (`arm64`), macOS 14.0, product `KMPNews`, bundle identifier
  `com.ndynagn.kmp.news.macos`. Independent macOS SDK/deployment settings.
  iOS-only project defaults were moved to the iOS target without changing
  their resolved values.
- SwiftUI entry point, screen, and `@MainActor @Observable` Swift ViewModel.
  Each window owns its ViewModel. The initial screen displays `Greeting` from
  Kotlin, with a local show/hide action. No news features or shared UI added.
- `macosArm64`, static `SharedLogic`, Foundation `actual getPlatform()` and
  a macOS platform/greeting test. UIKit stays in `iosMain`. Public Kotlin API,
  Android and JVM implementations are unchanged.
- Direct integration calls `embedAndSignAppleFrameworkForXcode` before Swift
  compilation, preserving the IDE override guard. Framework search/link settings
  are scoped to macOS. Ad-hoc local signing, empty development team for macOS;
  existing iOS team `R568U3JDV4` is retained.

## JSON migration: completed

On 2026-09-29 the user installed Xcode 27.2 beta (`27B5028f`) at
`/Applications/Xcode-beta.app`. The working project was converted using its
File Inspector > Project Document > Project Format > JSON, then saved.
**project.xcproj is now active; project.pbxproj was removed by Xcode.**
The existing project container, configurations, target identities, synchronized
folder membership and Kotlin build phases are retained. The macOS shared scheme
is retained. Xcode corrected the stale iOS product reference display path to
`KMPNews.app`; resolved build settings did not change.

Before converting, a disposable copy confirmed the CLI format token is
`-convert-project 'Xcode Project'` in this Xcode build (not `JSON`). The working
project was converted through the UI, following
[Apple's official migration guide](https://developer.apple.com/documentation/xcode/updating-your-xcode-project-configuration-file-format).

Global selected Xcode remains 27.0 (`27A266a`). Both targets' complete resolved
Debug build-settings dictionaries are identical before/after conversion under
the same stable toolchain and SDK. Stable Xcode 27.0 reads the JSON configuration.
The pre-conversion pbxproj and comparison data are in
`/tmp/kmpnews-json-migration/`.

## Validation

| Check | Result |
| --- | --- |
| Baseline iOS Simulator Debug build/install/launch | Passed; original starter screen captured |
| Final iOS Simulator Debug build/install/launch | Passed; same starter screen captured |
| iOS resolved settings comparison, same simulator destination | Product name, bundle ID, team, deployment target, SDK, framework search paths, linker flags, Info.plist, Swift version, device families and version numbers match baseline |
| Project open in Xcode after edits | Passed; both source folders visible |
| macOS Debug and Release builds | Passed, final sources/configuration |
| macOS runtime | Debug and Release launched; Kotlin greeting shown; no startup crash observed |
| macOS window lifecycle | Closed window and reopened via app activation; fresh ViewModel and greeting action work |
| macOS Release codesign verification | Passed `codesign --verify --deep --strict`; local ad-hoc only |
| shared macosArm64Test, iosSimulatorArm64Test, jvmTest | Passed |
| Android compileDebugKotlin | Passed |
| Post-conversion runtime | iOS Simulator install/launch and initial screen passed; macOS Release launched and displayed Kotlin greeting; ad-hoc signature verified |
| JSON conversion | Passed, official Xcode 27.2 beta UI; JSON format visible in inspector |
| Builds from project.xcproj | iOS Simulator Debug and macOS Debug/Release verified using stable Xcode 27.0 |

Commands are in README. Gradle reports existing deprecated features for Gradle
10; the requested task set completed successfully. During configuration editing,
the temporary XcodeProj 8.27.7 helper initially omitted synchronized xcconfig
anchor fields. Those original fields were restored with the structured Swift
writer, then iOS and both macOS builds were rerun successfully. No helper
package or dependency was added to this repository.

Limitations: iOS interaction after pressing the starter button was not checked;
Simulator launch and the initial screen were checked. Runtime checks used the
installed macOS 27 host, not a macOS 14 machine. App Store signing, provisioning,
archive/export, Intel support, real-device iOS
are not covered by these local results.

Temporary baseline/settings/logs/screenshots are under
`/tmp/kmpnews-native-macos-baseline`, `/tmp/kmpnews-native-macos-build*`, and
`/tmp/kmpnews-native-macos-*.log`; these are local diagnostics, not durable CI
artifacts. The source/configuration and this acceptance record are retained in the repository.

The accepted root-plus-scoped AGENTS structure is recorded in section 10 of
[the workflow proposal](ai-agent-workflow-proposal.md), for later implementation.
