package com.ndynagn.kmp.news.feature.favorites.di

import com.ndynagn.kmp.news.feature.auth.di.AuthDependencies
import com.ndynagn.kmp.news.feature.favorites.data.local.openFavoritesDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

/**
 * Creates an app-owned cache with a dedicated database path, distinct from the feed database.
 * Shares the existing Auth owner; no credentials are exposed to the host or presentation.
 */
fun createFavoritesDependencies(authDependencies: AuthDependencies, databasePath: String): FavoritesDependencies =
    assembleFavoritesDependencies(authDependencies, openFavoritesDatabase(databasePath)) {
        HttpClient(OkHttp) { configureFavoritesClient() }
    }
