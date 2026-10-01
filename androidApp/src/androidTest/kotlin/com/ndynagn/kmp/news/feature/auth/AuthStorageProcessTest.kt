package com.ndynagn.kmp.news.feature.auth

import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Invoke each method in a separate instrumentation process, write first and read second. */
class AuthStorageProcessTest {
    @Test
    fun writeFixtureBeforeProcessExit() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("authStoragePhase") == "write")
        assertTrue(storage().write("non-sensitive-process-fixture"))
    }

    @Test
    fun readFixtureAfterProcessRestartAndDelete() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("authStoragePhase") == "read")
        val storage = storage()
        try {
            val read = storage.read()
            assertFalse(read.failed)
            assertEquals("non-sensitive-process-fixture", read.value)
        } finally {
            assertTrue(storage.write(null))
        }
        assertEquals(null, storage.read().value)
    }

    private fun storage(): AndroidAuthStorage {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.noBackupFilesDir, "auth-process-test").apply { mkdirs() }
        val testContext = object : ContextWrapper(context) {
            override fun getNoBackupFilesDir(): File = directory
        }
        return AndroidAuthStorage(testContext)
    }
}
