package com.ndynagn.kmp.news.feature.profile.data

import com.ndynagn.kmp.news.feature.auth.domain.AuthFailure
import com.ndynagn.kmp.news.feature.profile.domain.ProfileDetails
import com.ndynagn.kmp.news.feature.profile.domain.ProfileFailure
import com.ndynagn.kmp.news.feature.profile.domain.ProfileRepository
import com.ndynagn.kmp.news.feature.profile.domain.ProfileResult
import com.ndynagn.kmp.news.feature.profile.domain.UserProfile
import com.ndynagn.kmp.news.network.AccountIdentity
import com.ndynagn.kmp.news.network.AccountSessionAccess
import com.ndynagn.kmp.news.network.CredentialsResult
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.uuid.Uuid

internal class RemoteProfileRepository(
    private val remote: ProfileRemoteSource,
    private val sessions: AccountSessionAccess,
) : ProfileRepository {
    private val lock = Mutex()

    override suspend fun fetch(): ProfileResult = operation { account ->
        when (val response = authorized(account) { remote.fetch(it) }) {
            is ProfileResponse.Failed -> ProfileResult(failure = response.failure)

            is ProfileResponse.Success -> {
                cleanup(account, response.value)
                result(account, response.value)
            }
        }
    }

    override suspend fun save(details: ProfileDetails, avatarJpeg: ByteArray?, removeAvatar: Boolean): ProfileResult {
        if (!details.isValid()) return ProfileResult(failure = ProfileFailure.INVALID_NAME)

        if (avatarJpeg != null && (
                avatarJpeg.size !in 3..2_097_152 ||
                    avatarJpeg[0] != 0xff.toByte() || avatarJpeg[1] != 0xd8.toByte()
                )
        ) {
            return ProfileResult(failure = ProfileFailure.INVALID_PHOTO)
        }

        return operation { account ->
            val fetched = authorized(account) { remote.fetch(it) }

            if (fetched is ProfileResponse.Failed) return@operation ProfileResult(failure = fetched.failure)

            val user = (fetched as ProfileResponse.Success).value

            if (user.id != account.userId) return@operation ProfileResult(failure = ProfileFailure.SESSION_EXPIRED)

            val metadata = mutableMapOf<String, JsonElement>()
            val oldPath = validPath(account, user.metadata.text("avatar_path"))
            val pending = pendingPaths(account, user.metadata).toMutableSet()
            var newPath = oldPath

            if (avatarJpeg != null) {
                newPath = "${account.userId}/${Uuid.random()}.jpg"

                // Record an abandoned upload before creating its object. The next fetch can clean it after cancellation.
                pending.add(newPath)

                val staged = authorized(account) {
                    remote.update(
                        it,
                        JsonObject(
                            mapOf(
                                "avatar_cleanup_paths" to JsonArray(pending.map(::JsonPrimitive)),
                            ),
                        ),
                    )
                }

                if (staged is ProfileResponse.Failed) return@operation ProfileResult(failure = staged.failure)

                val uploaded = authorized(account) { remote.upload(it, newPath, avatarJpeg) }

                if (uploaded is ProfileResponse.Failed) return@operation ProfileResult(failure = uploaded.failure)
            } else if (removeAvatar) {
                newPath = null
            }

            if (oldPath != newPath && oldPath != null) pending.add(oldPath)

            pending.remove(newPath)

            val normalized = details.normalized()

            metadata["first_name"] = JsonPrimitive(normalized.firstName)
            metadata["last_name"] = JsonPrimitive(normalized.lastName)
            metadata["middle_name"] = normalized.middleName?.let(::JsonPrimitive) ?: JsonNull

            if (avatarJpeg != null || removeAvatar) {
                metadata["avatar_path"] = newPath?.let(::JsonPrimitive) ?: JsonNull
                metadata["avatar_cleanup_paths"] = JsonArray(pending.map(::JsonPrimitive))
            }

            when (val saved = authorized(account) { remote.update(it, JsonObject(metadata)) }) {
                is ProfileResponse.Failed -> ProfileResult(failure = saved.failure)

                is ProfileResponse.Success -> {
                    cleanup(account, saved.value)
                    result(account, saved.value)
                }
            }
        }
    }

    private suspend fun operation(block: suspend (AccountIdentity) -> ProfileResult): ProfileResult {
        val account = sessions.account.value ?: return ProfileResult(failure = ProfileFailure.SESSION_EXPIRED)

        return lock.withLock {
            if (sessions.account.value != account) {
                return@withLock ProfileResult(failure = ProfileFailure.SESSION_EXPIRED)
            }

            val value = block(account)

            currentCoroutineContext().ensureActive()

            if (sessions.account.value == account) value else ProfileResult(failure = ProfileFailure.SESSION_EXPIRED)
        }
    }

    private suspend fun result(account: AccountIdentity, user: ProfileUserDto): ProfileResult {
        if (user.id != account.userId) return ProfileResult(failure = ProfileFailure.SESSION_EXPIRED)

        val path = validPath(account, user.metadata.text("avatar_path"))
        val url = path?.let {
            (authorized(account) { token -> remote.sign(token, it) } as? ProfileResponse.Success)?.value
        }

        return ProfileResult(
            UserProfile(
                user.id,
                user.email,
                ProfileDetails(
                    user.metadata.text("first_name").orEmpty(),
                    user.metadata.text("last_name").orEmpty(),
                    user.metadata.text("middle_name"),
                ),
                url,
                path != null,
            ),
        )
    }

    private suspend fun cleanup(account: AccountIdentity, user: ProfileUserDto) {
        if (user.id != account.userId) return

        val paths = pendingPaths(account, user.metadata).filter { it != user.metadata.text("avatar_path") }

        if (paths.isEmpty()) return

        if (authorized(account) { remote.delete(it, paths) } is ProfileResponse.Success) {
            authorized(account) {
                remote.update(it, JsonObject(mapOf("avatar_cleanup_paths" to JsonArray(emptyList()))))
            }
        }
    }

    private fun pendingPaths(account: AccountIdentity, metadata: JsonObject): List<String> =
        (metadata["avatar_cleanup_paths"] as? JsonArray).orEmpty().mapNotNull {
            validPath(account, (it as? JsonPrimitive)?.contentOrNull)
        }

    private fun validPath(account: AccountIdentity, path: String?): String? = path?.takeIf {
        it.startsWith("${account.userId}/") && it.removePrefix("${account.userId}/")
            .matches(Regex("[a-fA-F0-9-]+\\.jpg"))
    }

    private fun JsonObject.text(key: String): String? = (get(key) as? JsonPrimitive)?.contentOrNull

    private suspend fun <T> authorized(
        account: AccountIdentity,
        call: suspend (String) -> ProfileResponse<T>,
    ): ProfileResponse<T> {
        var rejected: String? = null

        repeat(2) { attempt ->
            val credentials = when (val value = sessions.credentials(account, rejected)) {
                is CredentialsResult.Ready -> value.credentials

                is CredentialsResult.Failed -> return ProfileResponse.Failed(
                    when (value.failure) {
                        AuthFailure.NETWORK -> ProfileFailure.NETWORK
                        AuthFailure.SESSION_EXPIRED -> ProfileFailure.SESSION_EXPIRED
                        else -> ProfileFailure.SERVICE
                    },
                )
            }

            val result = call(credentials.accessToken)

            currentCoroutineContext().ensureActive()

            if (sessions.account.value != account) return ProfileResponse.Failed(ProfileFailure.SESSION_EXPIRED)

            if (result !is ProfileResponse.Failed || result.failure != ProfileFailure.SESSION_EXPIRED) return result

            if (attempt == 1) sessions.reject(account, credentials.accessToken)

            rejected = credentials.accessToken
        }

        return ProfileResponse.Failed(ProfileFailure.SESSION_EXPIRED)
    }
}
