package com.ndynagn.kmp.news.tooling

import org.jetbrains.amper.plugins.Classpath
import org.jetbrains.amper.plugins.Input
import org.jetbrains.amper.plugins.TaskAction
import java.io.File
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

private val excludedDirectories = setOf(
    "build", "generated", "vendor", "third-party", ".git", ".idea", ".gradle", ".kotlin", ".toolchain",
    ".agents", ".skills", "node_modules",
)
private val sourceOwners = setOf("androidApp", "desktopApp", "iosApp", "sharedLogic", "sharedUI", "tooling")

/** Audits all handwritten Kotlin source sets and tests in the selected module without fixing them. */
@TaskAction
fun checkKotlin(
    @Input(inferTaskDependency = false) checkout: Path,
    moduleName: String,
    @Input toolClasspath: Classpath,
) {
    runKotlin(checkout, auditFiles(checkout, moduleName, "kt"), toolClasspath, format = false)
}

/** Audits handwritten Swift files using the active Xcode formatter without fixing them. */
@TaskAction
fun checkSwift(@Input(inferTaskDependency = false) checkout: Path, moduleName: String) {
    runSwift(checkout, auditFiles(checkout, moduleName, "swift"), format = false)
}

/** Formats only the nonempty, validated Kotlin selection in the clone-local manifest. */
@TaskAction
fun formatKotlin(
    @Input(inferTaskDependency = false) checkout: Path,
    moduleName: String,
    @Input toolClasspath: Classpath,
) {
    require(moduleName == "style") { "Formatting requires -m style to avoid concurrent writes" }
    runKotlin(
        checkout,
        selectedFiles(checkout, checkout.resolve(".toolchain/style-files.txt"), "kt"),
        toolClasspath,
        format = true,
    )
}

/** Formats only the nonempty, validated Swift selection in the clone-local manifest. */
@TaskAction
fun formatSwift(@Input(inferTaskDependency = false) checkout: Path, moduleName: String) {
    require(moduleName == "style") { "Formatting requires -m style to avoid concurrent writes" }
    runSwift(checkout, selectedFiles(checkout, checkout.resolve(".toolchain/style-files.txt"), "swift"), format = true)
}

private fun auditFiles(checkout: Path, moduleName: String, extension: String): List<Path> {
    val relativeDirectory = when (moduleName) {
        "style", "desktop-packaging" -> "tooling/$moduleName"
        else -> moduleName
    }
    val directory = checkout.resolve(relativeDirectory)
    require(directory.normalize().startsWith(checkout.normalize())) { "Module is outside the checkout" }

    val sources = mutableListOf<Path>()
    Files.walkFileTree(
        directory,
        object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                val excluded = dir.fileName.toString() in excludedDirectories

                return if (excluded) FileVisitResult.SKIP_SUBTREE else FileVisitResult.CONTINUE
            }

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (isHandwrittenFile(checkout, file, extension)) sources.add(file)

                return FileVisitResult.CONTINUE
            }
        },
    )

    return sources.sorted()
}

private fun selectedFiles(checkout: Path, selection: Path, extension: String): List<Path> {
    require(Files.isRegularFile(selection) && !Files.isSymbolicLink(selection)) { "Missing selection manifest" }
    require(selection.toRealPath().startsWith(checkout.toRealPath())) { "Manifest escapes the checkout" }
    val lines = Files.readAllLines(selection)
    require(lines.isNotEmpty() && lines.all { it.isNotBlank() }) { "Select at least one file; blank lines are invalid" }
    val files = lines.map { name ->
        require(name.none { it in "*?[]{}\\" }) { "Glob syntax is not allowed: $name" }
        val relative = Path.of(name)
        require(!relative.isAbsolute && relative.none { it.toString() == ".." }) { "Invalid relative path: $name" }
        val file = checkout.resolve(relative).normalize()
        require(isHandwrittenFile(checkout, file, extension)) { "Unsupported handwritten source: $name" }
        file
    }
    require(files.distinct().size == files.size) { "Duplicate selection" }

    return files
}

private fun isHandwrittenFile(checkout: Path, file: Path, extension: String): Boolean {
    val root = checkout.toAbsolutePath().normalize()
    val candidate = file.toAbsolutePath().normalize()
    if (!candidate.startsWith(root) || !Files.isRegularFile(candidate)) return false
    val relative = root.relativize(candidate)
    if (relative.nameCount < 2 || relative.first().toString() !in sourceOwners) return false
    if (relative.any { it.toString() in excludedDirectories }) return false
    if (!candidate.fileName.toString().endsWith(".$extension")) return false
    var current = root
    for (part in relative) {
        current = current.resolve(part)
        if (Files.isSymbolicLink(current)) return false
    }

    return candidate.toRealPath().startsWith(root.toRealPath())
}

private fun runKotlin(checkout: Path, files: List<Path>, classpath: Classpath, format: Boolean) {
    if (files.isEmpty()) return
    val java = ProcessHandle.current().info().command().orElse("java")
    val arguments =
        listOf(java, "-cp", classpath.resolvedFiles.joinToString(File.pathSeparator), "com.pinterest.ktlint.Main") +
            (if (format) listOf("--format") else emptyList()) + files.map(Path::toString)

    runTool(checkout, arguments)
}

private fun runSwift(checkout: Path, files: List<Path>, format: Boolean) {
    if (files.isEmpty()) return
    val arguments = listOf("xcrun", "swift-format", if (format) "format" else "lint") +
        (if (format) listOf("--in-place") else listOf("--strict")) +
        listOf("--configuration", checkout.resolve(".swift-format").toString()) + files.map(Path::toString)

    runTool(checkout, arguments)
}

private fun runTool(checkout: Path, arguments: List<String>) {
    println("Checking selected sources: ${arguments.last()}")
    val process = ProcessBuilder(arguments).directory(checkout.toFile()).inheritIO().start()
    val result = try {
        process.waitFor()
    } catch (exception: InterruptedException) {
        process.destroyForcibly()
        Thread.currentThread().interrupt()
        throw exception
    }
    check(result == 0) { "${arguments.first()} exited with code $result" }
}
