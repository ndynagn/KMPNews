package com.ndynagn.kmp.news

import platform.Foundation.NSProcessInfo
import kotlin.test.Test
import kotlin.test.assertEquals

class SharedLogicMacOSTest {
    @Test
    fun greetingUsesTheMacOSPlatform() {
        val platformName = "macOS ${NSProcessInfo.processInfo.operatingSystemVersionString}"

        assertEquals(platformName, getPlatform().name)
        assertEquals("Hello, $platformName!", Greeting().greet())
    }
}
