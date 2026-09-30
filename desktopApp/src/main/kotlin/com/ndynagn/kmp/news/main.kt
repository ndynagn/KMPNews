// Preserve the existing Desktop entry-point filename allowed by the Kotlin standard.
@file:Suppress("ktlint:standard:filename")

package com.ndynagn.kmp.news

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "KMPNews",
    ) {
        App()
    }
}
