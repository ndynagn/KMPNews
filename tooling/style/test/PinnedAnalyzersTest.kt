package com.ndynagn.kmp.news.tooling

import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PinnedAnalyzersTest {
    @Test
    fun replacingSwiftLintPreservesBytesForAnAlreadyRunningReader() {
        val root = Files.createTempDirectory("KMPNews concurrent analyzer ")
        try {
            val previous = ByteArray(65536) { 1 }
            val replacement = ByteArray(65536) { 2 }
            val archive = root.resolve("swiftlint.zip")
            val executable = root.resolve("swiftlint")
            zipExecutable(archive, previous)
            PinnedAnalyzers.installSwiftLint(archive, executable)

            Files.newInputStream(executable).use { runningReader ->
                zipExecutable(archive, replacement)
                PinnedAnalyzers.installSwiftLint(archive, executable)

                assertArrayEquals(previous, runningReader.readAllBytes())
                assertArrayEquals(replacement, Files.readAllBytes(executable))
            }
        } finally {
            Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
        }
    }

    @Test
    fun rejectsCorruptCachedAnalyzerWithoutDownloadingOrExecutingIt() {
        val root = Files.createTempDirectory("KMPNews corrupt analyzer ")
        try {
            val directory = Files.createDirectories(root.resolve(".toolchain/tools/linters"))
            Files.writeString(directory.resolve("detekt-${PinnedAnalyzers.DETEKT_VERSION}.jar"), "corrupt")

            assertThrows(IllegalArgumentException::class.java) { PinnedAnalyzers.detekt(root) }
        } finally {
            Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
        }
    }

    @Test
    fun rejectsSymlinkedCacheBeforeWritingOutsideCheckout() {
        val root = Files.createTempDirectory("KMPNews symlinked analyzer ")
        val outside = Files.createTempDirectory("KMPNews outside cache ")
        try {
            Files.createSymbolicLink(root.resolve(".toolchain"), outside)

            assertThrows(IllegalArgumentException::class.java) { PinnedAnalyzers.detekt(root) }
        } finally {
            Files.delete(root.resolve(".toolchain"))
            Files.delete(root)
            Files.delete(outside)
        }
    }
    private fun zipExecutable(archive: Path, bytes: ByteArray) {
        ZipOutputStream(Files.newOutputStream(archive)).use { zip ->
            zip.putNextEntry(ZipEntry("swiftlint"))
            zip.write(bytes)
            zip.closeEntry()
        }
    }
}
