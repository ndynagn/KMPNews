This is a Kotlin Multiplatform project targeting Android, iOS, Desktop (JVM), and native macOS.

- [sharedLogic](sharedLogic/src) owns shared domain/data, with portable code in
  [commonMain](sharedLogic/src/commonMain/kotlin) and platform implementations in
  their source sets.
- [sharedUI](sharedUI/src) owns shared Compose screens, ViewModels and presentation
  contracts for Android/JVM Desktop. [androidApp](androidApp) and
  [desktopApp](desktopApp) own their platform entry points and lifecycle integration.
- [iosApp](iosApp/iosApp) and [macosApp](macosApp) own native SwiftUI presentation
  and Swift ViewModels over the same shared domain/data.

### Running the apps

Use the run configurations provided by the run widget in your IDE's toolbar. You can also use these commands and options:

- Android app: `./gradlew :androidApp:assembleDebug`
- Desktop app:
  - Hot reload: `./gradlew :desktopApp:hotRun --auto`
  - Standard run: `./gradlew :desktopApp:run`
- iOS app: open the [/iosApp](./iosApp) directory in Xcode and run it from there.

### Running tests

Use the run button in your IDE's editor gutter, or run tests using Gradle tasks:

- Android tests: `./gradlew :sharedUI:testAndroidHostTest :sharedLogic:testAndroidHostTest`
- Desktop tests: `./gradlew :sharedUI:jvmTest :sharedLogic:jvmTest`
- iOS tests: `./gradlew :sharedLogic:iosSimulatorArm64Test`

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…

## Native macOS

The separate SwiftUI app in `macosApp/` supports Apple Silicon and macOS 14+.
Its Swift `@MainActor` Observable ViewModel consumes the static Kotlin
`SharedLogic` framework. JVM Desktop remains a separate client.

```sh
open iosApp/iosApp.xcodeproj
xcodebuild -project iosApp/iosApp.xcodeproj -scheme macosApp \
  -configuration Debug -destination 'platform=macOS,arch=arm64' \
  -derivedDataPath /tmp/kmpnews-macos build
open /tmp/kmpnews-macos/Build/Products/Debug/KMPNews.app
```

Select the shared `macosApp` scheme in Xcode. Use `-configuration Release` for
Release builds. The build invokes `:sharedLogic:embedAndSignAppleFrameworkForXcode`
automatically and needs the same Java/Gradle prerequisites as iOS. Local signing
is ad-hoc; registering an App ID is unnecessary. Distribution signing is not configured.

```sh
./gradlew :sharedLogic:macosArm64Test :sharedLogic:iosSimulatorArm64Test \
  :sharedLogic:jvmTest :androidApp:compileDebugKotlin
```

The project uses `project.pbxproj`. The JSON migration was reversed through
Xcode's Project Format > Property List because Kotlin Gradle Plugin 2.4.20
requires the pbxproj file during IDE Sync. Native macOS, synchronized folders
and direct Kotlin integration are retained; no diagnostic tasks are disabled.
See [implementation results](docs/native-macos-implementation.md).

## Engineering instructions and style

Start with [AGENTS.md](AGENTS.md) and the linked module instructions. Claude
imports the same files through adjacent CLAUDE.md files. Accepted standards cover
[architecture](docs/standards/architecture.md),
[Kotlin](docs/standards/kotlin-style.md), [Swift](docs/standards/swift-style.md),
and [verification commands](docs/standards/verification.md).
The [accepted stack](docs/standards/technology-stack.md) records Koin/Ktor/Room,
Compose and Apple choices, MVVM/MVI ownership and the NewsData.io Free prototype
boundary. Dependency integration and the feed/cache implementation are upcoming;
selected technologies are not all installed or compatibility-tested yet.

Formatting is opt-in and applied only to intended files during adoption:

```sh
./gradlew spotlessCheck -PstyleFiles=build.gradle.kts
xcrun swift-format lint --strict --configuration .swift-format macosApp/ContentView.swift
```

Full audits intentionally expose existing starter formatting debt; see
[adoption results](docs/agent-guidelines-adoption.md). CI enforcement and bulk
formatting are separate work.
