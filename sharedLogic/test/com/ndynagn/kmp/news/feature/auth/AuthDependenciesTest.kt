package com.ndynagn.kmp.news.feature.auth

import com.ndynagn.kmp.news.feature.auth.data.AuthSessionStorage
import com.ndynagn.kmp.news.feature.auth.data.AuthStorageRead
import com.ndynagn.kmp.news.feature.auth.di.AuthConfiguration
import com.ndynagn.kmp.news.feature.auth.di.assembleAuthDependencies
import com.ndynagn.kmp.news.feature.auth.di.configureAuthClient
import com.ndynagn.kmp.news.feature.auth.domain.AuthSession
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class AuthDependenciesTest {
    @Test
    fun graphRestoresGuestWithoutHttpAndClosesItsClient() = runTest {
        val storage = object : AuthSessionStorage {
            override fun read() = AuthStorageRead(null, false)
            override fun write(value: String?) = true
        }
        val client = HttpClient(MockEngine { error("A guest restore must not request HTTP") }) {
            configureAuthClient()
        }
        val dependencies = assembleAuthDependencies(
            AuthConfiguration("https://example.test", "sb_publishable_fixture_key"),
            storage,
            client,
        )

        try {
            dependencies.authRepository.restore()

            assertIs<AuthSession.Guest>(dependencies.authRepository.session.value)
            assertNotNull(dependencies.profileRepository)
        } finally {
            dependencies.close()
        }

        assertFalse(assertNotNull(client.coroutineContext[Job]).isActive)
    }
}
