package com.ndynagn.kmp.news.feature.auth

import android.content.Context
import com.ndynagn.kmp.news.feature.auth.di.AuthConfiguration
import com.ndynagn.kmp.news.feature.auth.di.AuthDependencies
import com.ndynagn.kmp.news.feature.auth.di.assembleAuthDependencies
import com.ndynagn.kmp.news.feature.auth.di.configureAuthClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

/** Creates an application-owned graph using Android Keystore and a dedicated HTTP client. */
fun createAuthDependencies(context: Context, configuration: AuthConfiguration): AuthDependencies =
    assembleAuthDependencies(configuration, AndroidAuthStorage(context), HttpClient(OkHttp) { configureAuthClient() })
