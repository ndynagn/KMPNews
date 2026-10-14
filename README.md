# KMPNews

This project is targeting Android, iOS, Desktop (JVM), built with
the [Kotlin Toolchain](https://kotlin-toolchain.org/latest/).

- [/androidApp](./androidApp) contains the Android application.
- [/desktopApp](./desktopApp) contains the desktop (JVM) application.
- [/iosApp](./iosApp) contains the iOS application.
- [/sharedLogic](./sharedLogic) holds the generated greeting and platform implementations shared by all clients.
- [/sharedUI](./sharedUI) holds Compose presentation for Android and JVM Desktop.
- iOS presentation uses native SwiftUI and consumes `sharedLogic` through `KotlinModules`.

The project name is `KMPNews`. The Kotlin package, Android application ID and iOS bundle ID are
`com.ndynagn.kmp.news`. This is a fresh starter project; application code from the archived project has not been migrated.

The `kotlin` (macOS/Linux) and `kotlin.bat` (Windows) scripts in the project root are self-bootstrapping wrappers for
the Kotlin Toolchain: they download the pinned version 0.13.0 on first use, so no separate installation is required.
Build the whole project with `./kotlin build`.

### Running

Run each module from the command line:

- Android app: `./kotlin run -m androidApp`
- Desktop app: `./kotlin run -m desktopApp`
- iOS app: `./kotlin run -m iosApp`

### Testing

Run the project's tests with `./kotlin test`, or `./kotlin test -m <module>` for a single module.

---

Learn more
about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html), [Kotlin Toolchain](https://kotlin-toolchain.org/latest/), [Compose Multiplatform](https://kotlinlang.org/compose-multiplatform/), [Kotlin/Wasm](https://kotl.in/wasm/), [Ktor](https://ktor.io/).
