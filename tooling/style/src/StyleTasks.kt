package com.ndynagn.kmp.news.tooling

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import org.jetbrains.amper.plugins.Classpath
import org.jetbrains.amper.plugins.Input
import org.jetbrains.amper.plugins.TaskAction

private val kotlinExtensions = setOf("kt", "kts")
private val swiftExtensions = setOf("swift")

/** Checks Kotlin sources, tests and tooling without applying fixes. */
@TaskAction
fun checkKotlin(
    @Input(inferTaskDependency = false) checkout: Path,
    moduleName: String,
    @Input toolClasspath: Classpath,
) {
    kotlinTool(checkout, SourceSelection(checkout).audit(moduleName, kotlinExtensions), toolClasspath, false)
}

/** Checks native Swift sources, including opt-in verification code. */
@TaskAction
fun checkSwift(@Input(inferTaskDependency = false) checkout: Path, moduleName: String) {
    swiftTool(checkout, SourceSelection(checkout).audit(moduleName, swiftExtensions), false)
}

/** Runs configured Detekt light rules across every Kotlin source set. */
@TaskAction
fun checkDetekt(
    @Input(inferTaskDependency = false) checkout: Path,
    moduleName: String,
    @Input toolClasspath: Classpath,
) {
    val files = SourceSelection(checkout).audit(moduleName, kotlinExtensions)
    // Detekt light mode accepts malformed PSI; ktlint must validate syntax before analysis.
    kotlinTool(checkout, files, toolClasspath, false)
    detektTool(checkout, files)
}

/** Runs selected SwiftLint rules independently of formatting. */
@TaskAction
fun checkSwiftLint(@Input(inferTaskDependency = false) checkout: Path, moduleName: String) {
    swiftLintTool(checkout, SourceSelection(checkout).audit(moduleName, swiftExtensions))
}

/** Checks selected files or directories with both Kotlin tools, without fixes. */
@TaskAction
fun checkSelectedKotlin(
    @Input(inferTaskDependency = false) checkout: Path,
    moduleName: String,
    @Input toolClasspath: Classpath,
) {
    val files = selection(checkout, moduleName, kotlinExtensions, false)
    kotlinTool(checkout, files, toolClasspath, false)
    detektTool(checkout, files)
}

/** Checks selected files or directories with both Swift tools, without fixes. */
@TaskAction
fun checkSelectedSwift(@Input(inferTaskDependency = false) checkout: Path, moduleName: String) {
    val files = selection(checkout, moduleName, swiftExtensions, false)
    swiftTool(checkout, files, false)
    swiftLintTool(checkout, files)
}

/** Formats only explicitly selected individual Kotlin files. */
@TaskAction
fun formatKotlin(
    @Input(inferTaskDependency = false) checkout: Path,
    moduleName: String,
    @Input toolClasspath: Classpath,
) {
    kotlinTool(checkout, selection(checkout, moduleName, kotlinExtensions, true), toolClasspath, true)
}

/** Formats only explicitly selected individual Swift files. */
@TaskAction
fun formatSwift(@Input(inferTaskDependency = false) checkout: Path, moduleName: String) {
    swiftTool(checkout, selection(checkout, moduleName, swiftExtensions, true), true)
}

/** Exercises configured rules and formatter stability on disposable examples outside the checkout. */
@TaskAction
fun verifyStyleTools(
    @Input(inferTaskDependency = false) checkout: Path,
    moduleName: String,
    @Input toolClasspath: Classpath,
) {
    require(moduleName == "style") { "Tool regression checks require -m style" }
    runStyleProcess(
        checkout,
        listOf("python3", "-m", "unittest", "discover", "-s", "scripts/tests", "-p", "test_style_tools.py", "-v"),
        mapOf(
            "KTLINT_CLASSPATH" to toolClasspath.resolvedFiles.joinToString(File.pathSeparator),
            "DETEKT_JAR" to PinnedAnalyzers.detekt(checkout).toString(),
            "SWIFTLINT_BIN" to PinnedAnalyzers.swiftLint(checkout).toString(),
            "STYLE_JAVA" to javaExecutable(),
        ),
    )
}

private fun selection(checkout: Path, moduleName: String, extensions: Set<String>, format: Boolean): List<Path> {
    require(moduleName == "style") { "Selected commands require -m style to avoid concurrent writers" }

    return SourceSelection(checkout).selected(
        checkout.resolve(".toolchain/style-files.txt"),
        extensions,
        allowDirectories = !format,
    )
}

private fun kotlinTool(checkout: Path, files: List<Path>, classpath: Classpath, format: Boolean) {
    if (files.isEmpty()) return
    val command =
        listOf(
            javaExecutable(),
            "-cp",
            classpath.resolvedFiles.joinToString(File.pathSeparator),
            "com.pinterest.ktlint.Main",
        )
    runStyleProcess(checkout, command + "--version")
    inventory(checkout, "ktlint", ".editorconfig", files)
    runStyleProcess(checkout, command + (if (format) listOf("--format") else emptyList()) + files.map(Path::toString))
}

private fun swiftTool(checkout: Path, files: List<Path>, format: Boolean) {
    if (files.isEmpty()) return
    runStyleProcess(checkout, listOf("xcrun", "swift", "--version"))
    runStyleProcess(checkout, listOf("xcodebuild", "-version"))
    inventory(checkout, "swift-format", ".swift-format", files)
    val command = listOf("xcrun", "swift-format", if (format) "format" else "lint") +
        (if (format) listOf("--in-place") else listOf("--strict")) +
        listOf("--configuration", checkout.resolve(".swift-format").toString())
    runStyleProcess(checkout, command + files.map(Path::toString))
}

private fun detektTool(checkout: Path, files: List<Path>) {
    if (files.isEmpty()) return
    inventory(checkout, "Detekt ${PinnedAnalyzers.DETEKT_VERSION} (light; no type resolution)", "detekt.yaml", files)
    require(files.none { File.pathSeparatorChar in it.toString() }) { "Detekt input paths contain a path separator" }
    val command =
        listOf(
            javaExecutable(), "-jar", PinnedAnalyzers.detekt(checkout).toString(), "--analysis-mode", "light",
            "--config", checkout.resolve("detekt.yaml").toString(), "--fail-on-severity", "Warning",
        )
    runStyleProcess(checkout, command + listOf("--input", files.joinToString(File.pathSeparator)))
}

private fun swiftLintTool(checkout: Path, files: List<Path>) {
    if (files.isEmpty()) return
    inventory(checkout, "SwiftLint ${PinnedAnalyzers.SWIFTLINT_VERSION}", ".swiftlint.yml", files)
    // Harnesses include standalone scripts and independent @main programs, not one Swift module.
    files.forEach { file ->
        runStyleProcess(checkout, listOf("xcrun", "swiftc", "-frontend", "-parse", file.toString()))
    }
    runStyleProcess(
        checkout,
        listOf(
            PinnedAnalyzers.swiftLint(checkout).toString(),
            "lint",
            "--strict",
            "--no-cache",
            "--config",
            checkout.resolve(".swiftlint.yml").toString(),
            "--use-script-input-files",
        ),
        mapOf("SCRIPT_INPUT_FILE_COUNT" to files.size.toString()) + files.mapIndexed { index, file ->
            "SCRIPT_INPUT_FILE_$index" to file.toString()
        },
    )
}

private fun inventory(checkout: Path, tool: String, config: String, files: List<Path>) {
    require(Files.isRegularFile(checkout.resolve(config))) { "Missing configuration: $config" }
    println("$tool; configuration=$config; handwritten files=${files.size}")
    files.forEach { println("  ${checkout.relativize(it)}") }
}

private fun javaExecutable(): String = Path.of(System.getProperty("java.home"), "bin", "java").toString()

internal fun runStyleProcess(checkout: Path, arguments: List<String>, environment: Map<String, String> = emptyMap()) {
    val builder = ProcessBuilder(arguments).directory(checkout.toFile()).inheritIO()
    builder.environment().putAll(environment)
    val process = builder.start()
    val result = try {
        process.waitFor()
    } catch (exception: InterruptedException) {
        process.destroyForcibly()
        Thread.currentThread().interrupt()
        throw exception
    }
    check(result == 0) { "${arguments.first()} exited with code $result" }
}
