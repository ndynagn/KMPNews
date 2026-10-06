package com.ndynagn.kmp.news.feature.profile

import com.ndynagn.kmp.news.feature.auth.domain.AuthFailure
import com.ndynagn.kmp.news.feature.profile.data.ProfileRemoteSource
import com.ndynagn.kmp.news.feature.profile.data.ProfileResponse
import com.ndynagn.kmp.news.feature.profile.data.ProfileUserDto
import com.ndynagn.kmp.news.feature.profile.data.RemoteProfileRepository
import com.ndynagn.kmp.news.feature.profile.domain.ProfileDetails
import com.ndynagn.kmp.news.feature.profile.domain.ProfileFailure
import com.ndynagn.kmp.news.network.AccountCredentials
import com.ndynagn.kmp.news.network.AccountIdentity
import com.ndynagn.kmp.news.network.AccountSessionAccess
import com.ndynagn.kmp.news.network.CredentialsResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProfileRepositoryTest {
    private val jpeg = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte())

    @Test fun cancellationRemainsCancellationInsteadOfAServiceFailure() = runTest {
        val remote = FakeProfileRemote()
        remote.onFetch = { throw CancellationException("Screen closed") }

        assertFailsWith<CancellationException> { RemoteProfileRepository(remote, FakeProfileSession()).fetch() }

        assertEquals(listOf("fetch"), remote.calls)
    }

    @Test fun namesTrimWithoutRejectingUnicodeOrPunctuation() {
        val details = ProfileDetails("  李 Анна-Мария  ", " O’Connor ", "  ").normalized()

        assertTrue(details.isValid())
        assertEquals("李 Анна-Мария", details.firstName)
        assertEquals("O’Connor", details.lastName)
        assertNull(details.middleName)
        assertFalse(ProfileDetails(" ", "Surname", null).isValid())
    }

    @Test fun replacementCommitsBeforeDeletingOldAvatarAndRetainsCleanupAfterFailure() = runTest {
        val remote = FakeProfileRemote()
        remote.metadata = JsonObject(mapOf("avatar_path" to JsonPrimitive("reader/aaaa.jpg")))
        remote.deleteFails = true
        val repository = RemoteProfileRepository(remote, FakeProfileSession())

        val result = repository.save(ProfileDetails(" First ", " Last ", " "), jpeg, false)

        assertNull(result.failure)
        assertEquals("First", result.profile?.details?.firstName)
        assertEquals(listOf("fetch", "update", "upload", "update", "delete", "sign"), remote.calls)
        assertTrue(remote.metadata["avatar_cleanup_paths"].toString().contains("reader/aaaa.jpg"))

        val current = remote.metadata["avatar_path"]
        remote.deleteFails = false

        repository.fetch()

        assertEquals(current, remote.metadata["avatar_path"])
        assertEquals(JsonArray(emptyList()), remote.metadata["avatar_cleanup_paths"])
        assertEquals(listOf("reader/aaaa.jpg"), remote.deleted)
    }

    @Test fun uploadFailurePreservesCurrentAvatarAndNextFetchCleansAbandonedCandidate() = runTest {
        val remote = FakeProfileRemote()
        remote.metadata = JsonObject(mapOf("avatar_path" to JsonPrimitive("reader/aaaa.jpg")))
        remote.uploadFails = true
        val repository = RemoteProfileRepository(remote, FakeProfileSession())

        assertEquals(
            ProfileFailure.NETWORK,
            repository.save(ProfileDetails("First", "Last", null), jpeg, false).failure,
        )
        assertEquals(JsonPrimitive("reader/aaaa.jpg"), remote.metadata["avatar_path"])

        repository.fetch()

        assertTrue(remote.deleted.single().startsWith("reader/"))
        assertFalse(remote.deleted.contains("reader/aaaa.jpg"))
    }

    @Test fun removalClearsPathBeforeDeleteAndNeverDeletesAnotherOwnersMetadataPath() = runTest {
        val remote = FakeProfileRemote()
        remote.metadata = JsonObject(
            mapOf(
                "avatar_path" to JsonPrimitive("reader/aaaa.jpg"),
                "avatar_cleanup_paths" to JsonArray(listOf(JsonPrimitive("other/bbbb.jpg"))),
            ),
        )
        val result = RemoteProfileRepository(remote, FakeProfileSession())
            .save(ProfileDetails("First", "Last", null), null, true)

        assertNull(result.profile?.avatarUrl)
        assertNull((remote.metadata["avatar_path"] as? JsonPrimitive)?.contentOrNull)
        assertEquals(listOf("reader/aaaa.jpg"), remote.deleted)
        assertTrue(remote.calls.indexOf("update") < remote.calls.indexOf("delete"))
    }

    @Test fun unauthorizedFetchRefreshesOnceAndLateResponseCannotCrossAccounts() = runTest {
        val remote = FakeProfileRemote()
        val sessions = FakeProfileSession()
        val repository = RemoteProfileRepository(remote, sessions)
        remote.rejectOnce = true

        assertNotNull(repository.fetch().profile)
        assertEquals(listOf(null, "token"), sessions.rejectedTokens.take(2))

        remote.onFetch = { sessions.account.value = AccountIdentity("other", 2) }

        assertEquals(ProfileFailure.SESSION_EXPIRED, repository.fetch().failure)
    }

    @Test fun invalidDraftDoesNotPerformAnyNetworkRequest() = runTest {
        val remote = FakeProfileRemote()
        val repository = RemoteProfileRepository(remote, FakeProfileSession())

        assertEquals(
            ProfileFailure.INVALID_NAME,
            repository.save(ProfileDetails("", "Last", null), null, false).failure,
        )
        assertEquals(
            ProfileFailure.INVALID_PHOTO,
            repository.save(ProfileDetails("First", "Last", null), byteArrayOf(1), false).failure,
        )
        assertTrue(remote.calls.isEmpty())
    }
}

private class FakeProfileSession : AccountSessionAccess {
    override val account = MutableStateFlow<AccountIdentity?>(AccountIdentity("reader", 1))
    val rejectedTokens = mutableListOf<String?>()

    override suspend fun credentials(identity: AccountIdentity, rejectedToken: String?): CredentialsResult {
        rejectedTokens.add(rejectedToken)
        return if (identity == account.value) {
            CredentialsResult.Ready(AccountCredentials(identity, "token"))
        } else {
            CredentialsResult.Failed(AuthFailure.SESSION_EXPIRED)
        }
    }

    override suspend fun commitIfCurrent(identity: AccountIdentity, commit: suspend () -> Unit): Boolean {
        if (identity != account.value) return false
        commit()
        return true
    }

    override suspend fun reject(identity: AccountIdentity, accessToken: String) {
        account.value = null
    }
}

private class FakeProfileRemote : ProfileRemoteSource {
    var metadata = JsonObject(emptyMap())
    var deleteFails = false
    var uploadFails = false
    var rejectOnce = false
    var onFetch: () -> Unit = {}
    val calls = mutableListOf<String>()
    var deleted = emptyList<String>()

    override suspend fun fetch(token: String): ProfileResponse<ProfileUserDto> {
        calls.add("fetch")
        onFetch()
        if (rejectOnce) {
            rejectOnce = false
            return ProfileResponse.Failed(ProfileFailure.SESSION_EXPIRED)
        }
        return ProfileResponse.Success(ProfileUserDto("reader", "reader@example.test", metadata))
    }

    override suspend fun update(token: String, metadata: JsonObject): ProfileResponse<ProfileUserDto> {
        calls.add("update")
        this.metadata = JsonObject(this.metadata + metadata)
        return ProfileResponse.Success(ProfileUserDto("reader", "reader@example.test", this.metadata))
    }

    override suspend fun upload(token: String, path: String, jpeg: ByteArray): ProfileResponse<Unit> {
        calls.add("upload")
        return if (uploadFails) ProfileResponse.Failed(ProfileFailure.NETWORK) else ProfileResponse.Success(Unit)
    }

    override suspend fun delete(token: String, paths: List<String>): ProfileResponse<Unit> {
        calls.add("delete")
        deleted = paths
        return if (deleteFails) ProfileResponse.Failed(ProfileFailure.NETWORK) else ProfileResponse.Success(Unit)
    }

    override suspend fun sign(token: String, path: String): ProfileResponse<String> {
        calls.add("sign")
        return ProfileResponse.Success("https://example.test/$path")
    }
}
