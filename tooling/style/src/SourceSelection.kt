package com.ndynagn.kmp.news.tooling

import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.extension

/** Selects handwritten sources independently of Git tracking and KMP compilation targets. */
internal class SourceSelection(private val checkout: Path) {
    private val root = checkout.toAbsolutePath().normalize()
    private val owners = setOf("androidApp", "desktopApp", "iosApp", "sharedLogic", "sharedUI", "tooling")
    private val excluded = setOf(
        "build", "generated", "vendor", "third-party", ".git", ".idea", ".gradle", ".kotlin", ".toolchain",
        ".agents", ".skills", "node_modules", "Pods", "Carthage", ".build", "DerivedData", "SourcePackages",
    )

    fun audit(moduleName: String, extensions: Set<String>): List<Path> {
        val directory = when (moduleName) {
            "style", "desktop-packaging" -> "tooling/$moduleName"
            else -> moduleName
        }

        return collect(resolve(directory), extensions)
    }

    /** Directory selection is read-only; formatting always requires literal individual files. */
    fun selected(manifest: Path, extensions: Set<String>, allowDirectories: Boolean): List<Path> {
        require(safe(manifest) && Files.isRegularFile(manifest)) { "Missing or unsafe selection manifest" }
        val lines = Files.readAllLines(manifest)
        require(lines.isNotEmpty() && lines.none(String::isBlank)) { "Select sources; blank lines are invalid" }
        val inputs = lines.map(::resolve)
        require(inputs.distinct().size == inputs.size) { "Duplicate selection" }

        val files = inputs.flatMap { path ->
            require(owned(path)) { "Excluded source: $path" }
            if (Files.isDirectory(path)) {
                require(allowDirectories) { "Formatting requires individual files: $path" }
                collect(path, extensions).also {
                    require(it.isNotEmpty()) { "Directory has no matching sources: $path" }
                }
            } else {
                require(handwritten(path, extensions)) { "Unsupported handwritten source: $path" }
                listOf(path)
            }
        }
        require(files.distinct().size == files.size) { "Overlapping selection" }

        return files.sorted()
    }

    private fun resolve(name: String): Path {
        require(name.isNotBlank() && name.none { it in "*?[]{}\\\n\r" }) { "Use literal relative paths: $name" }
        val relative = Path.of(name)
        require(!relative.isAbsolute && relative.none { it.toString() == ".." }) { "Invalid relative path: $name" }
        val path = root.resolve(relative).normalize()
        require(safe(path)) { "Missing or symlinked path: $name" }

        return path
    }

    private fun collect(directory: Path, extensions: Set<String>): List<Path> {
        require(owned(directory) && Files.isDirectory(directory)) { "Invalid source directory: $directory" }

        val sources = mutableListOf<Path>()
        Files.walkFileTree(
            directory,
            object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult =
                    if (owned(dir)) FileVisitResult.CONTINUE else FileVisitResult.SKIP_SUBTREE

                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (handwritten(file, extensions)) sources.add(file)

                    return FileVisitResult.CONTINUE
                }
            },
        )

        return sources.sorted()
    }

    private fun handwritten(path: Path, extensions: Set<String>): Boolean =
        owned(path) && Files.isRegularFile(path) && path.extension in extensions

    private fun owned(path: Path): Boolean {
        if (!safe(path)) return false
        val relative = root.relativize(path)

        return relative.nameCount >= 1 && relative.first().toString() in owners &&
            relative.none { it.toString() in excluded }
    }

    private fun safe(path: Path): Boolean {
        val candidate = path.toAbsolutePath().normalize()
        if (!candidate.startsWith(root) || !Files.exists(candidate)) return false
        var current = root
        for (part in root.relativize(candidate)) {
            current = current.resolve(part)
            if (Files.isSymbolicLink(current)) return false
        }

        return candidate.toRealPath().startsWith(root.toRealPath())
    }
}
