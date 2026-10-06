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

The project uses `project.pbxproj`, required by the Kotlin Gradle Plugin during
IDE Sync. Preserve Xcode serialization, synchronized folders and direct Kotlin
integration; follow the [shared Apple project rules](iosApp/AGENTS.md).

## Engineering instructions and style

Start with [AGENTS.md](AGENTS.md) and the linked module instructions. Claude
imports the same files through adjacent CLAUDE.md files. Accepted standards cover
[architecture](docs/standards/architecture.md),
[Kotlin](docs/standards/kotlin-style.md), [Swift](docs/standards/swift-style.md),
and [verification commands](docs/standards/verification.md).
The [accepted stack](docs/standards/technology-stack.md) records Koin/Ktor/Room,
Compose and Apple choices, MVVM/MVI ownership and the NewsData.io Free prototype
boundary. Feed domain/data and its dependency integration are described in the
[feed contract](docs/contracts/news-feed.md). Other active feature contracts are
listed in the [instruction routing table](AGENTS.md#feature-contract-routing).

Formatting is opt-in and applied only to intended files during adoption:

```sh
./gradlew spotlessCheck -PstyleFiles=build.gradle.kts
xcrun swift-format lint --strict --configuration .swift-format macosApp/ContentView.swift
```

Full audits expose existing formatting debt; report current results separately
from checks of task-specific changes. CI enforcement and bulk formatting are separate
work. Keep run reports in chat/PR/MR and temporary artifacts outside the repository;
see the [documentation lifecycle](AGENTS.md#documentation-lifecycle-and-naming).

## Feed domain/data development

The feed repository in sharedLogic supplies the Android and iOS news screens.
Use the typed `createFeedDependencies` platform factory with an app-private absolute
DB path and `FeedApiConfiguration` containing the Supabase origin and publishable key.
Android additionally receives Context.
Share one owner per database, inject its repository into clients, cancel consumers
before closing the owner. Swift receives the same Kotlin data/domain implementation.
The public project key is sent in `apikey`; it is intentionally extractable from client binaries.
The provider credential exists only in the Edge Function's server secrets.

The repository observes Room, caches at most 200 cards, refreshes on activation
when stale by one hour, and supports manual refresh. No background polling or
prefetch occurs. The host must explicitly invoke the activation policy; observing
alone never starts HTTP. Article metadata is available offline, not full source
articles or image bytes. See the contract for cache capacity and pagination behavior.

```sh
./gradlew :sharedLogic:jvmTest :sharedLogic:macosArm64Test \
  :sharedLogic:iosSimulatorArm64Test :sharedLogic:testAndroidHostTest
# Requires an Android emulator/device:
./gradlew :sharedLogic:connectedAndroidDeviceTest
# Requires a booted iOS Simulator; all responses are synthetic:
sh scripts/check-feed-interop.sh
```

The interop script temporarily enables test fixtures, runs Swift on macOS and
Simulator, and restores normal frameworks on success. If interrupted, rebuild
without `-PfeedInteropTests=true`. Never use fixture-enabled outputs for app delivery.
Live provider smoke is optional and separate from credential-free deterministic tests.

### Opt-in HTTP diagnostics

Feed factories do not log by default. For local debugging, use the overload accepting
`FeedHttpLogger`. It logs requests, statuses and headers for HTTPS calls to the configured Supabase project;
credential headers are masked and bodies are omitted. URLs and other headers remain
visible. Do not put credentials in query parameters. The composition root decides
whether logging is enabled; sharedLogic does not infer a build mode.

Kotlin composition example (JVM; Android additionally requires `context`):

```kotlin
val dependencies = if (enableLocalHttpDiagnostics) {
    createFeedDependencies(databasePath, configuration, FeedHttpLogger { message -> println(message) })
} else {
    createFeedDependencies(databasePath, configuration)
}
```

Swift composition example:

```swift
final class DebugFeedHttpLogger: FeedHttpLogger {
    nonisolated func log(message: String) {
        print(message)
    }
}

#if DEBUG
let dependencies = FeedFactory_appleKt.createFeedDependencies(
    databasePath: databasePath,
    configuration: configuration,
    httpLogger: DebugFeedHttpLogger()
)
#else
let dependencies = FeedFactory_appleKt.createFeedDependencies(
    databasePath: databasePath,
    configuration: configuration
)
#endif
```

Logger callbacks may be concurrent and are not MainActor-bound. Keep implementations
thread-safe, fast and non-throwing. The client retains the logger but does not close
it. Cancel repository consumers before closing their `FeedDependencies` owner.

## Feed server

The feed uses the Supabase `news-feed` Edge Function. Public client settings are in
`feed.properties` (Android) and `iosApp/Configuration/Feed.xcconfig` (iOS). They must
identify the same project. Never put a NewsData or privileged Supabase key in either
file. See [server setup and verification](supabase/README.md).
