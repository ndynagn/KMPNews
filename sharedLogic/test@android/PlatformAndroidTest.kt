package com.ndynagn.kmp.news

import kotlin.test.Test
import kotlin.test.assertTrue

class PlatformAndroidTest {
    @Test
    fun `platform name is reported`() {
        assertTrue(getPlatform().name.isNotBlank())
    }
}
