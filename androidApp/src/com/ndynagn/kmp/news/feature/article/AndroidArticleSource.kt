package com.ndynagn.kmp.news.feature.article

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.core.net.toUri
import java.net.URI
import java.net.URISyntaxException

internal fun isArticleSourceUrl(url: String): Boolean = try {
    val uri = URI(url)

    (uri.scheme.equals("https", ignoreCase = true) || uri.scheme.equals("http", ignoreCase = true)) &&
        !uri.host.isNullOrBlank()
} catch (_: URISyntaxException) {
    false
}

/** Opens only validated web URLs; missing/blocked system handlers become localized UI feedback. */
internal fun openArticleSource(url: String, launch: (Intent) -> Unit): Boolean {
    if (!isArticleSourceUrl(url)) return false

    val intent = Intent(Intent.ACTION_VIEW, url.toUri()).addCategory(Intent.CATEGORY_BROWSABLE)

    return try {
        launch(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}
