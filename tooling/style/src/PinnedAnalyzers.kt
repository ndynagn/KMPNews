package com.ndynagn.kmp.news.tooling

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.ZipFile

/** Verifies standalone analyzer bytes before running them from the ignored checkout cache. */
internal object PinnedAnalyzers {
    const val DETEKT_VERSION = "2.0.0-alpha.6"
    const val SWIFTLINT_VERSION = "0.65.1"

    @Synchronized
    fun detekt(checkout: Path): Path = artifact(
        checkout,
        "detekt-$DETEKT_VERSION.jar",
        "https://repo.maven.apache.org/maven2/dev/detekt/detekt-cli/$DETEKT_VERSION/detekt-cli-$DETEKT_VERSION-all.jar",
        "d46ca62ea4d62769b5d5c3ba94d49fa9b80ba11c7dba74ddb6df7fcc2c19c5fd",
    )

    @Synchronized
    fun swiftLint(checkout: Path): Path {
        require(System.getProperty("os.name").startsWith("Mac")) { "Pinned SwiftLint requires macOS" }
        val archive = artifact(
            checkout,
            "swiftlint-$SWIFTLINT_VERSION.zip",
            "https://github.com/realm/SwiftLint/releases/download/$SWIFTLINT_VERSION/portable_swiftlint.zip",
            "c1e429b0599cf1b516f369a2d9ec04eaf0e436f3c12b637df8851fa52ff694d0",
        )
        val executable = archive.resolveSibling("swiftlint-$SWIFTLINT_VERSION")
        require(!Files.isSymbolicLink(executable)) { "Unsafe SwiftLint executable" }
        installSwiftLint(archive, executable)

        return executable
    }

    /** Publishes a complete executable atomically; concurrent checks keep their already opened inode. */
    internal fun installSwiftLint(archive: Path, executable: Path) {
        val temporary = Files.createTempFile(executable.parent, "swiftlint-", ".tmp")
        try {
            ZipFile(archive.toFile()).use { zip ->
                zip.getInputStream(zip.getEntry("swiftlint")).use { input ->
                    Files.newOutputStream(temporary).use { input.copyTo(it) }
                }
            }
            check(temporary.toFile().setExecutable(true, true)) { "Cannot make SwiftLint executable" }
            Files.move(temporary, executable, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun artifact(checkout: Path, name: String, url: String, sha256: String): Path {
        var directory = checkout.toAbsolutePath().normalize()
        for (part in listOf(".toolchain", "tools", "linters")) {
            directory = directory.resolve(part)
            require(!Files.isSymbolicLink(directory)) { "Unsafe analyzer cache: $directory" }
            Files.createDirectories(directory)
        }
        val target = directory.resolve(name)
        require(!Files.isSymbolicLink(target)) { "Unsafe analyzer artifact" }
        if (!Files.exists(target)) {
            val download = Files.createTempFile(directory, "download-", ".tmp")
            try {
                runStyleProcess(
                    checkout,
                    listOf("curl", "--fail", "--location", "--silent", "--show-error", url, "-o", download.toString()),
                )
                require(digest(download) == sha256) { "Analyzer checksum mismatch: $name" }
                Files.move(download, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } finally {
                Files.deleteIfExists(download)
            }
        }
        require(digest(target) == sha256) { "Cached analyzer checksum mismatch: $name" }

        return target
    }

    private fun digest(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(65536)
            var count = input.read(buffer)
            while (count >= 0) {
                digest.update(buffer, 0, count)
                count = input.read(buffer)
            }
        }

        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
