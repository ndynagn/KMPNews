package com.ndynagn.kmp.news.verification

import android.app.Activity
import android.content.ContextWrapper
import android.os.Bundle
import android.widget.TextView
import com.ndynagn.kmp.news.feature.auth.AndroidAuthStorage
import com.ndynagn.kmp.news.feature.favorites.FavoritesDevicePersistence
import com.ndynagn.kmp.news.feature.feed.FeedDevicePersistence
import com.ndynagn.kmp.news.feature.search.NewsEngineDeviceFixture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.io.File

/** Feature-owned opt-in entry point, installed under a separate verification application ID. */
class FeatureVerificationActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val label = TextView(this)
        setContentView(label)
        scope.launch {
            val outcome = runCatching {
                withTimeout(60_000) {
                    val context = applicationContext
                    val directory = File(context.noBackupFilesDir, "auth-process-test").apply { mkdirs() }
                    val isolatedContext = object : ContextWrapper(context) {
                        override fun getNoBackupFilesDir(): File = directory
                    }
                    val storage = AndroidAuthStorage(isolatedContext)
                    when (intent.getStringExtra("phase")) {
                        "write" -> {
                            check(storage.write("non-sensitive-process-fixture"))
                            check(storage.read().value == "non-sensitive-process-fixture")
                        }

                        "read" -> {
                            check(!storage.read().failed)
                            check(storage.read().value == "non-sensitive-process-fixture")
                            check(storage.write(null))
                            check(storage.read().value == null)
                            FeedDevicePersistence().verify(context)
                            FavoritesDevicePersistence().verify(context)
                            NewsEngineDeviceFixture(intent.getIntExtra("newsPort", 0)).verify()
                        }

                        else -> error("Explicit verification phase required")
                    }
                }
            }
            val result = outcome.fold({ "PASS" }, { "FAIL: ${it.javaClass.simpleName}: ${it.message}" })
            File(filesDir, "verification-result.txt").writeText(result)
            label.text = result
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
