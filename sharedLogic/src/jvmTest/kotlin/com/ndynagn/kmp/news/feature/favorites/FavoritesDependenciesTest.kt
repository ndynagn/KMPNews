package com.ndynagn.kmp.news.feature.favorites

import com.ndynagn.kmp.news.feature.auth.data.AuthSessionStorage
import com.ndynagn.kmp.news.feature.auth.data.AuthStorageRead
import com.ndynagn.kmp.news.feature.auth.di.AuthConfiguration
import com.ndynagn.kmp.news.feature.auth.di.assembleAuthDependencies
import com.ndynagn.kmp.news.feature.auth.di.configureAuthClient
import com.ndynagn.kmp.news.feature.favorites.data.local.openFavoritesDatabase
import com.ndynagn.kmp.news.feature.favorites.di.assembleFavoritesDependencies
import com.ndynagn.kmp.news.feature.favorites.di.configureFavoritesClient
import com.ndynagn.kmp.news.feature.favorites.domain.FavoritesReadResult
import com.ndynagn.kmp.news.feature.favorites.domain.FavoritesUpdateResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class FavoritesDependenciesTest {
    @Test
    fun graphBorrowsTheSameAuthSessionAndDoesNotCloseIt() = runTest {
        val directory = Files.createTempDirectory("favorites-di-").toFile()
        val auth = assembleAuthDependencies(
            AuthConfiguration("https://example.test", "sb_publishable_fixture"),
            object : AuthSessionStorage {
                var value: String? = null

                override fun read() = AuthStorageRead(value, false)

                override fun write(value: String?): Boolean {
                    this.value = value

                    return true
                }
            },
            HttpClient(
                MockEngine {
                    respond(
                        """
                        {
                            "access_token": "access",
                            "refresh_token": "refresh",
                            "expires_in": 3600,
                            "user": {"id": "reader", "email": "reader@example.test"}
                        }
                        """.trimIndent(),
                    )
                },
            ) { configureAuthClient() },
        )
        try {
            val favorites =
                assembleFavoritesDependencies(auth, openFavoritesDatabase(directory.resolve("cache.db").path)) {
                    HttpClient(
                        MockEngine { request ->
                            assertEquals("Bearer access", request.headers["Authorization"])
                            respond("[]")
                        },
                    ) { configureFavoritesClient() }
                }
            try {
                assertEquals(FavoritesReadResult.SignedOut, favorites.favoritesRepository.observeFavorites().first())
                assertNull(auth.authRepository.signIn("reader@example.test", "password").failure)
                assertEquals(FavoritesUpdateResult.Updated, favorites.favoritesRepository.refresh())
                assertIs<FavoritesReadResult.Snapshot>(favorites.favoritesRepository.observeFavorites().first())
            } finally {
                favorites.close()
            }
            assertNull(auth.authRepository.signOut().failure)
        } finally {
            auth.close()
            directory.deleteRecursively()
        }
    }
}
