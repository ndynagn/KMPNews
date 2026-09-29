This is a Kotlin Multiplatform project targeting Android, iOS, Desktop (JVM), and native macOS.

* [/iosApp](./iosApp/iosApp) contains an iOS application. Even if you’re sharing your UI with Compose Multiplatform,
  you need this entry point for your iOS app. This is also where you should add SwiftUI code for your project.

* [/sharedLogic](./sharedLogic/src) is for the code that will be shared between app targets in the project.
  The most important subfolder is [commonMain](./sharedLogic/src/commonMain/kotlin). If preferred, you
  can add code to the platform-specific folders here too.

* [/sharedUI](./sharedUI/src) is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
  - [commonMain](./sharedUI/src/commonMain/kotlin) is for code that’s common for all targets.
  - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
    For example, if you want to use Apple’s CoreCrypto for the iOS part of your Kotlin app,
    the [iosMain](./sharedUI/src/iosMain/kotlin) folder would be the right place for such calls.
    Similarly, if you want to edit the Desktop (JVM) specific part, the [jvmMain](./sharedUI/src/jvmMain/kotlin)
    folder is the appropriate location.

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

The project uses `project.xcproj`, converted by Xcode 27.2 beta (27B5028f)
through Project Format > JSON. Xcode 27+ can read the format; iOS Simulator
and macOS builds were verified with stable Xcode 27.0. No global toolchain
switch is required. See [implementation results](docs/native-macos-implementation.md).
