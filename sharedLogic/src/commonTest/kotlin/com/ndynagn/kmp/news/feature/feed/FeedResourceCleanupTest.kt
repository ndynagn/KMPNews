package com.ndynagn.kmp.news.feature.feed

import com.ndynagn.kmp.news.feature.feed.di.closeFeedResources
import kotlinx.coroutines.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class FeedResourceCleanupTest {
    @Test
    fun firstAndMiddleFailuresDoNotSkipRemainingCleanup() {
        val calls = mutableListOf<String>()
        val first = IllegalStateException("container")
        val second = IllegalArgumentException("client")

        val thrown = assertFailsWith<IllegalStateException> {
            closeFeedResources(
                null,
                {
                    calls += "container"
                    throw first
                },
                {
                    calls += "client"
                    throw second
                },
                { calls += "database" },
            )
        }

        assertSame(first, thrown)
        assertEquals(listOf(second), thrown.suppressedExceptions)
        assertEquals(listOf("container", "client", "database"), calls)
    }

    @Test
    fun middleFailureStillClosesDatabase() {
        val calls = mutableListOf<String>()
        val failure = IllegalStateException("client")

        val thrown = assertFailsWith<IllegalStateException> {
            closeFeedResources(null, { calls += "container" }, {
                calls += "client"
                throw failure
            }, {
                calls += "database"
            })
        }

        assertSame(failure, thrown)
        assertEquals(listOf("container", "client", "database"), calls)
    }

    @Test
    fun cancellationRemainsPrimaryEvenWhenCleanupFails() {
        val cancellation = CancellationException("cancelled")
        val failure = IllegalStateException("cleanup")
        var closes = 0

        val thrown = assertFailsWith<CancellationException> {
            closeFeedResources(cancellation, {
                closes++
                throw failure
            }, {
                closes++
                throw cancellation
            })
        }

        assertSame(cancellation, thrown)
        assertEquals(listOf(failure), thrown.suppressedExceptions)
        assertEquals(2, closes)
    }
}
