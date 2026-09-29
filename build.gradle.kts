plugins {
    alias(libs.plugins.spotless)
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
}

// Literal repo-relative file selection; reject mistakes instead of broadening the scope.
val styleFiles = providers.gradleProperty("styleFiles").orNull?.split(",")?.map { it.trim() }?.also { paths ->
    require(paths.isNotEmpty() && paths.none { it.isEmpty() }) { "styleFiles must name existing source files" }
    paths.forEach { path ->
        require(
            !path.startsWith("/") && path.split("/").none {
                it in setOf("..", "build", "generated", ".gradle", "third-party", "vendor")
            },
        ) { "styleFiles must use handwritten, repo-relative source paths: $path" }
        require(file(path).isFile && (path.endsWith(".kt") || path.endsWith(".gradle.kts"))) {
            "Unsupported or missing styleFiles entry: $path"
        }
    }
}

spotless {
    // Adoption is explicit: checks never format, and application builds stay independent.
    setEnforceCheck(false)
    kotlin {
        if (styleFiles == null) {
            target("*/src/**/*.kt")
        } else {
            target(styleFiles.filter { it.endsWith(".kt") })
        }
        targetExclude("**/build/**", "**/generated/**", "**/.gradle/**", "**/third-party/**", "**/vendor/**")
        ktlint(libs.versions.ktlint.get())
    }
    kotlinGradle {
        if (styleFiles == null) {
            target("*.gradle.kts", "*/build.gradle.kts")
        } else {
            target(styleFiles.filter { it.endsWith(".gradle.kts") })
        }
        ktlint(libs.versions.ktlint.get())
    }
}
