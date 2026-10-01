package com.ndynagn.kmp.news.feature.auth

import com.ndynagn.kmp.news.feature.auth.data.AuthSessionStorage
import com.ndynagn.kmp.news.feature.auth.di.AuthConfiguration
import com.ndynagn.kmp.news.feature.auth.di.AuthDependencies
import com.ndynagn.kmp.news.feature.auth.di.assembleAuthDependencies
import com.ndynagn.kmp.news.feature.auth.di.configureAuthClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin

/** Creates an application-owned graph; the native host injects its Keychain storage adapter. */
fun createAuthDependencies(configuration: AuthConfiguration, storage: AuthSessionStorage): AuthDependencies =
    assembleAuthDependencies(configuration, storage, HttpClient(Darwin) { configureAuthClient() })
