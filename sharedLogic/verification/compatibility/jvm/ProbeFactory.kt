package com.ndynagn.kmp.news.verification

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

/** Constructs disposable file-backed verification databases on JVM. */
fun createCompatibilityProbe(): CompatibilityProbe = CompatibilityProbe { path ->
    Room.databaseBuilder<ProbeDatabase>(name = path)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
}
