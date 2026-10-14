package com.ndynagn.kmp.news.verification

import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue

class CompatibilityTest {
    @Test
    fun platformLibrariesPersistObserveAndReleaseResources() = runBlocking {
        val directory = Files.createTempDirectory("kmpnews-room-")
        val serverUrl = requireNotNull(System.getenv("KMPNEWS_FIXTURE_SERVER"))
        val probe = createCompatibilityProbe()

        val result = probe.verify(directory.resolve("probe.db").toString(), serverUrl)

        assertTrue(result.startsWith("PASS:"), result)
    }
}
