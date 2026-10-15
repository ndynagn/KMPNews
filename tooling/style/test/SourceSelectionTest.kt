package com.ndynagn.kmp.news.tooling

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SourceSelectionTest {
    @Test
    fun routesEveryModuleAndKeepsSwiftVerificationInScope() = inCheckout { root ->
        val modules = mapOf(
            "androidApp" to "androidApp/src/Main.kt",
            "desktopApp" to "desktopApp/src/main.kt",
            "sharedLogic" to "sharedLogic/src/Contract.kt",
            "sharedUI" to "sharedUI/src/Screen.kt",
            "style" to "tooling/style/src/Style.kt",
            "desktop-packaging" to "tooling/desktop-packaging/src/Packaging.kt",
        )
        val files = modules.mapValues { write(root, it.value) }
        val swift = write(root, "iosApp/verification/ui-tests/ScreenTests.swift")
        val selection = SourceSelection(root)

        for ((module, path) in files) {
            assertEquals(listOf(path), selection.audit(module, setOf("kt", "kts")))
        }
        assertEquals(listOf(swift), selection.audit("iosApp", setOf("swift")))
    }

    @Test
    fun coversPlatformsTestsHarnessesAndScriptsWithoutGeneratedSources() = inCheckout { root ->
        val expected = listOf(
            "sharedLogic/src/Contract.kt",
            "sharedLogic/src@android/Driver.kt",
            "sharedLogic/src@ios/Driver.ios.kt",
            "sharedLogic/src@jvm/Driver.jvm.kt",
            "sharedLogic/test@ios/DriverTest.kt",
            "sharedLogic/test/ContractTest.kt",
            "sharedLogic/verification/Probe.kt",
            "sharedLogic/scripts/check.kts",
        ).map { write(root, it) }
        listOf("build", "generated", "vendor", "third-party", "SourcePackages", ".build").forEach {
            write(root, "sharedLogic/$it/Ignored.kt")
        }
        val selection = SourceSelection(root)

        val result = selection.audit("sharedLogic", setOf("kt", "kts"))

        assertEquals(expected.sorted(), result)
    }

    @Test
    fun resolvesDirectoriesAndPathsWithSpacesWithoutChangingFiles() = inCheckout { root ->
        val file = write(root, "iosApp/src/Folder with spaces/View.swift")
        val original = Files.readString(file)
        val manifest = write(root, ".toolchain/style-files.txt", "iosApp/src\n")

        val result = SourceSelection(root).selected(manifest, setOf("swift"), true)

        assertEquals(listOf(file), result)
        assertEquals(original, Files.readString(file))
    }

    @Test
    fun rejectsEmptyInvalidExcludedDuplicateAndOverlappingSelections() = inCheckout { root ->
        write(root, "sharedLogic/src/Contract.kt")
        write(root, "sharedLogic/generated/Generated.kt")
        val manifest = write(root, ".toolchain/style-files.txt")
        val invalid = listOf(
            "", "\n", "../outside.kt", "/tmp/outside.kt", "sharedLogic/src/*.kt",
            "sharedLogic/missing.kt", "sharedLogic/generated/Generated.kt", "sharedLogic/src/Contract.swift",
            "sharedLogic/src/Contract.kt\nsharedLogic/src/Contract.kt", "sharedLogic/src\nsharedLogic/src/Contract.kt",
        )

        for (contents in invalid) {
            Files.writeString(manifest, contents)

            assertThrows(contents, IllegalArgumentException::class.java) {
                SourceSelection(root).selected(manifest, setOf("kt"), true)
            }
        }
    }

    @Test
    fun formattingRejectsDirectoriesBeforeAnyToolStarts() = inCheckout { root ->
        write(root, "sharedLogic/src/Contract.kt")
        val manifest = write(root, ".toolchain/style-files.txt", "sharedLogic/src\n")

        assertThrows(IllegalArgumentException::class.java) {
            SourceSelection(root).selected(manifest, setOf("kt"), false)
        }
    }

    @Test
    fun rejectsSymlinkedFilesDirectoriesAndManifest() = inCheckout { root ->
        val file = write(root, "sharedLogic/src/Contract.kt")
        Files.createSymbolicLink(root.resolve("sharedLogic/src/Linked.kt"), file)
        Files.createSymbolicLink(root.resolve("sharedLogic/linked"), file.parent)
        val manifest = write(root, ".toolchain/style-files.txt")
        val selection = SourceSelection(root)

        for (name in listOf("sharedLogic/src/Linked.kt", "sharedLogic/linked/Contract.kt")) {
            Files.writeString(manifest, name)

            assertThrows(IllegalArgumentException::class.java) { selection.selected(manifest, setOf("kt"), true) }
        }
        Files.createSymbolicLink(root.resolve(".toolchain/linked.txt"), manifest)

        assertThrows(IllegalArgumentException::class.java) {
            selection.selected(root.resolve(".toolchain/linked.txt"), setOf("kt"), true)
        }
        assertEquals(listOf(file), selection.audit("sharedLogic", setOf("kt")))
    }

    private fun write(root: Path, relative: String, contents: String = "fixture\n"): Path {
        val path = root.resolve(relative)
        Files.createDirectories(path.parent)
        Files.writeString(path, contents)

        return path
    }

    private fun inCheckout(test: (Path) -> Unit) {
        val root = Files.createTempDirectory("KMPNews style ")
        try {
            test(root)
        } finally {
            Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
        }
    }
}
