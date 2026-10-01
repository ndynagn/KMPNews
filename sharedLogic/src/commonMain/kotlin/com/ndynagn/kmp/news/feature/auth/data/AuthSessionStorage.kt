package com.ndynagn.kmp.news.feature.auth.data

/** Opaque encrypted-storage read; absence differs from an inaccessible/corrupt store. */
data class AuthStorageRead(val value: String?, val failed: Boolean)

/**
 * Platform storage boundary injected only at composition. Implementations must be thread-safe,
 * atomic, non-logging and return failures rather than throwing across Swift/Kotlin.
 * A null write removes credentials. Called on the repository's background dispatcher.
 */
interface AuthSessionStorage {
    fun read(): AuthStorageRead
    fun write(value: String?): Boolean
}
