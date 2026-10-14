package com.ndynagn.kmp.news.verification

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow

/** Swift-only opt-in verification surface with observable collector/job cancellation. */
class InteropProbe {
    private val active = MutableStateFlow(0)

    /** Number of active verification jobs/collectors; Swift reads it after joining a cancelled task. */
    val activeJobs: Int get() = active.value

    /** Emits explicit expected-failure values and completes; it never throws into Swift. */
    fun values(): Flow<String> = flow {
        emit("value")
        emit("expected-error")
    }

    /** Remains active until the Swift subscriber cancels, then releases the collector. */
    fun waitingValues(): Flow<String> = flow {
        active.value += 1
        try {
            emit("started")
            awaitCancellation()
        } finally {
            active.value -= 1
        }
    }

    /** Returns a neutral value through SKIE's suspend bridge. */
    suspend fun value(): String {
        delay(10)
        return "value"
    }

    /** Expected failure is a value; this probe does not export an unhandled Kotlin exception. */
    suspend fun expectedFailure(): String = "expected-error"

    /** Remains active until its Swift task cancels, then releases its Kotlin job. */
    suspend fun waitingValue(): String {
        active.value += 1
        try {
            awaitCancellation()
        } finally {
            active.value -= 1
        }
    }
}
