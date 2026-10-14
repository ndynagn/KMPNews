package com.ndynagn.kmp.news.verification

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

/** Captures only application context for disposable Android verification databases. */
fun createCompatibilityProbe(context: Context): CompatibilityProbe {
    val applicationContext = context.applicationContext

    return CompatibilityProbe { path ->
        Room.databaseBuilder<ProbeDatabase>(context = applicationContext, name = path)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
    }
}
