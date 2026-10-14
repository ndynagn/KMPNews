package com.ndynagn.kmp.news

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import com.ndynagn.kmp.news.verification.createCompatibilityProbe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File

/** Activity installed only in a disposable compatibility checkout under a separate application ID. */
class CompatibilityActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val status = TextView(this)
        setContentView(status)

        scope.launch {
            val result = try {
                createCompatibilityProbe(applicationContext).verify(
                    File(filesDir, "probe-${System.nanoTime()}.db").absolutePath,
                    intent.getStringExtra("serverUrl") ?: error("Missing local fixture server"),
                )
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                "FAIL: ${exception.stackTraceToString()}"
            }

            File(filesDir, "compatibility.txt").writeText(result)
            status.text = result
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
