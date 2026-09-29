package com.ndynagn.kmp.news

import kotlin.test.Test
import kotlin.test.assertEquals
import platform.Foundation.NSProcessInfo

class SharedLogicMacOSTest {
    @Test
    fun greetingUsesTheMacOSPlatform() {
        val platformName = "macOS ${NSProcessInfo.processInfo.operatingSystemVersionString}"

        assertEquals(platformName, getPlatform().name)
        assertEquals("Hello, $platformName!", Greeting().greet())
    }
}
