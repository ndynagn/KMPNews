package com.ndynagn.kmp.news.feature.feed.di

import com.ndynagn.kmp.news.feature.feed.data.local.openFeedDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin

/**
 * Creates one app-owned graph for an absolute writable database path and an public Supabase configuration.
 *
 * Share the result across consumers of this database. Cancel their tasks before closing
 * [FeedDependencies] once. Acquired resources are cleaned up if initialization fails;
 * cleanup failures are suppressed on the original exception, including cancellation.
 * See [feed contract](../../../../../../../../../../../docs/contracts/news-feed.md), Public API and resource ownership.
 */
fun createFeedDependencies(databasePath: String, configuration: FeedApiConfiguration): FeedDependencies =
    createFeedDependenciesWithClient(openFeedDatabase(databasePath), configuration) {
        HttpClient(Darwin) { configureFeedHttpClient(configuration) }
    }

/**
 * Creates an app-owned graph with explicitly enabled, masked HTTP header diagnostics.
 *
 * Resource ownership and cancellation follow the overload without a logger. Supply a
 * [FeedHttpLogger] only from a debug composition root; it is retained but never closed.
 */
fun createFeedDependencies(
    databasePath: String,
    configuration: FeedApiConfiguration,
    httpLogger: FeedHttpLogger,
): FeedDependencies = createFeedDependenciesWithClient(openFeedDatabase(databasePath), configuration) {
    HttpClient(Darwin) { configureFeedHttpClient(configuration, httpLogger) }
}
