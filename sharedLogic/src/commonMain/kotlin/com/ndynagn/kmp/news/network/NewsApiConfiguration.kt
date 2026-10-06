package com.ndynagn.kmp.news.network

import io.ktor.http.URLProtocol
import io.ktor.http.Url

/**
 * Public client configuration for the news mediator; never accepts a NewsData or privileged Supabase key.
 *
 * [supabaseUrl] is the project's HTTPS origin. Blank or malformed configuration disables remote reads,
 * preventing remote requests. Repositories expose their corresponding configuration failure.
 */
class NewsApiConfiguration(val supabaseUrl: String, val publishableKey: String) {
    private val origin = runCatching { Url(supabaseUrl) }.getOrNull()

    val isConfigured: Boolean = origin != null &&
        origin.protocol == URLProtocol.HTTPS && origin.host.isNotBlank() &&
        origin.user == null && origin.password == null && origin.parameters.isEmpty() &&
        origin.fragment.isEmpty() && origin.encodedPath in setOf("", "/") &&
        publishableKey.startsWith("sb_publishable_") &&
        publishableKey.length > "sb_publishable_".length

    internal val functionsUrl: String = supabaseUrl.trimEnd('/') + "/functions/v1/"
    internal val host: String? = origin?.host
}
