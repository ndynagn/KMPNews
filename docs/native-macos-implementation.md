# Native macOS implementation and Xcode format history

Status: **Native macOS completed; JSON migration reversed** (2026-09-29).
The active configuration is `project.pbxproj`. See the rollback section below.

Implemented on `features/native-macos-xcproj`, based on main `735400a`.
Accepted for delivery on `dev` by the user after successful native macOS and
JSON migration checks. Existing signing-team and Info.plist changes and the
documentation directory are preserved in the delivered snapshot.

Original completion covered local development setup, native macOS integration
and Xcode JSON migration. A later IDE Sync failure required reversing only the
file format, preserving the native macOS implementation. The limitations remain explicit;
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

## Historical JSON migration (subsequently reversed)

On 2026-09-29 the user installed Xcode 27.2 beta (`27B5028f`) at
`/Applications/Xcode-beta.app`. The working project was converted using its
File Inspector > Project Document > Project Format > JSON, then saved.
At that stage, project.xcproj became active and Xcode removed project.pbxproj.
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

## Original implementation validation

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

The root-plus-scoped AGENTS rollout is recorded in
[instruction adoption](agent-guidelines-adoption.md).

## Property List rollback: 2026-09-29

The installed Kotlin Gradle Plugin 2.4.20 registers `convertPbxprojToJson` and
`checkXcodeProjectConfiguration` for IDE import. The producer requires an existing
`project.pbxproj`; the consumer parses its legacy object structure. Xcode builds
from project.xcproj worked, but Android Studio Sync failed on the missing input.
The original build checks did not cover this IDE import path.

The current project was converted using Xcode 27.2 beta's File Inspector >
Project Document > Project Format > Property List and saved. Xcode generated
`project.pbxproj` and removed `project.xcproj`. No old project snapshot was restored,
and neither Kotlin diagnostic task was disabled. The selected build toolchain
remains Xcode 27.0; no Kotlin, Gradle or Xcode upgrade was performed.

Before conversion, the complete current container was copied to
`/tmp/kmpnews-pbx-rollback/original.xcodeproj`. Baseline and resulting targets,
schemes and Debug settings are stored alongside it. Both target settings
dictionaries and the project listing match exactly before/after conversion.
Native macOS, synchronized source folders, signing, the framework build scripts
and AGENTS/CLAUDE membership exclusions are retained.

The two diagnostic tasks completed successfully with both tasks executed on the
first CLI run. Actual Android Studio **Sync Project with Gradle Files** completed
at 20:16 local time and displayed `BUILD SUCCESSFUL in 21s` and a finished result.
This verifies IDE import directly, rather than inferring success from a CLI build.

| Rollback check | Result |
| --- | --- |
| Xcode close and reopen | Passed; both targets visible and Property List shown in File Inspector |
| Targets, schemes and resolved Debug settings | Identical before/after for iOS Simulator and macOS |
| Kotlin Xcode diagnostics | Passed; initial execution and repeat using configuration cache, no skipped/disabled checks |
| Android Studio Sync | Passed through the actual IDE action |
| iOS Simulator Debug build | Passed with signing disabled for the simulator |
| Native macOS Debug build | Passed with the existing local signing configuration |
| Resource membership | Neither built app contains AGENTS.md or CLAUDE.md; macOS synchronized-folder exceptions retained |
| Preservation | Source and Gradle script hashes, shared schemes and workspace metadata unchanged from the pre-rollback state |

Both builds used stable Xcode 27.0 and a fresh derived-data directory:

```sh
./gradlew :sharedLogic:convertPbxprojToJson :sharedLogic:checkXcodeProjectConfiguration
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug \
  -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath /tmp/kmpnews-pbx-rollback/build build CODE_SIGNING_ALLOWED=NO
xcodebuild -project iosApp/iosApp.xcodeproj -scheme macosApp -configuration Debug \
  -destination 'platform=macOS,arch=arm64' \
  -derivedDataPath /tmp/kmpnews-pbx-rollback/build build
```

This rollback did not rerun Release, runtime interactions or distribution signing.
No product source/API changed. Existing Gradle deprecation and build-tool warnings
remain outside this format-only correction. The rollback was initially left
uncommitted; on 2026-09-30 the user authorized delivery to dev together with the
completed agent-instruction task.

Keep the Property List format until a future Kotlin plugin's JSON support is
verified with both real IDE Sync and Apple builds. Logs and comparison snapshots
for this rollback are in `/tmp/kmpnews-pbx-rollback`; temporary files are local
diagnostics, not durable CI artifacts.
