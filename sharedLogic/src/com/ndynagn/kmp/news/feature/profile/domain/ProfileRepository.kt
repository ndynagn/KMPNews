package com.ndynagn.kmp.news.feature.profile.domain

/** Personal names. Blank middle names normalize to null; names are not restricted to a writing system. */
data class ProfileDetails(val firstName: String, val lastName: String, val middleName: String?) {
    /** Trims each name and represents an empty middle name as null. */
    fun normalized(): ProfileDetails = ProfileDetails(
        firstName.trim(),
        lastName.trim(),
        middleName?.trim()?.takeIf { it.isNotEmpty() },
    )

    /** Requires nonblank first and last names without restricting their writing system or punctuation. */
    fun isValid(): Boolean = firstName.isNotBlank() && lastName.isNotBlank()
}

/** Server-owned account information. Avatar URLs expire after one hour and must not be persisted. */
data class UserProfile(
    val userId: String,
    val email: String,
    val details: ProfileDetails,
    val avatarUrl: String?,
    val hasAvatar: Boolean,
)

/** Expected profile failures, including a session changed while a request was in flight. */
enum class ProfileFailure { INVALID_NAME, INVALID_PHOTO, NETWORK, SERVICE, SESSION_EXPIRED, RATE_LIMITED }

/** A successful write may have an unavailable avatar preview; names are still authoritative. */
data class ProfileResult(val profile: UserProfile? = null, val failure: ProfileFailure? = null)

/**
 * Private account profile boundary. Calls belong to the caller; cancellation propagates.
 * Reads always refresh from the server. Writes are serialized and scoped to the starting login.
 * Each save fetches current metadata first, including retries after ambiguous network outcomes.
 * Callers own their drafts and decide how to present failed writes.
 */
interface ProfileRepository {
    /** Fetches current metadata and retries deferred deletion of obsolete avatars. */
    suspend fun fetch(): ProfileResult

    /**
     * Saves trimmed names and optionally replaces/removes the avatar. Null JPEG with false removal preserves it.
     * JPEG must be normalized by the platform and no larger than 2 MiB. Replacement wins over removal.
     */
    suspend fun save(details: ProfileDetails, avatarJpeg: ByteArray?, removeAvatar: Boolean): ProfileResult
}
