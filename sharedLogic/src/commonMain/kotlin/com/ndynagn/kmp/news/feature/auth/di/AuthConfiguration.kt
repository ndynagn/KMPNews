package com.ndynagn.kmp.news.feature.auth.di

import io.ktor.http.URLProtocol
import io.ktor.http.Url

/** Public HTTPS project origin and publishable key. Invalid configuration disables all Auth HTTP calls. */
class AuthConfiguration(val projectUrl: String, val publishableKey: String) {
    private val origin = runCatching { Url(projectUrl) }.getOrNull()
    val isConfigured: Boolean = origin != null && origin.protocol == URLProtocol.HTTPS &&
        origin.host.isNotBlank() && origin.user == null && origin.password == null &&
        origin.parameters.isEmpty() && origin.fragment.isEmpty() && origin.encodedPath in setOf("", "/") &&
        publishableKey.startsWith("sb_publishable_") && publishableKey.length > 15
    internal val baseUrl = projectUrl.trimEnd('/') + "/auth/v1/"
}
