package com.ndynagn.kmp.news.feature.feed.presentation

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class FeedPaginationConnectionTest {
    @Test
    fun forwardInputIncludesAttemptsAtTheEndButIgnoresReverseAndProgrammaticScrolls() {
        var checks = 0
        val connection = FeedPaginationConnection { checks++ }

        connection.onPostScroll(Offset(0f, -10f), Offset.Zero, NestedScrollSource.SideEffect)
        connection.onPostScroll(Offset(0f, 10f), Offset.Zero, NestedScrollSource.UserInput)
        connection.onPostScroll(Offset.Zero, Offset.Zero, NestedScrollSource.UserInput)

        assertEquals(0, checks)

        connection.onPostScroll(Offset(0f, -10f), Offset.Zero, NestedScrollSource.UserInput)
        connection.onPostScroll(Offset.Zero, Offset(0f, -10f), NestedScrollSource.UserInput)

        assertEquals(2, checks)
    }

    @Test
    fun forwardFlingAllowsContinuedScrollOnlyUntilItFinishes() = runTest {
        var checks = 0
        val connection = FeedPaginationConnection { checks++ }

        connection.onPreFling(Velocity(0f, -100f))
        connection.onPostScroll(Offset(0f, -10f), Offset.Zero, NestedScrollSource.SideEffect)

        assertEquals(1, checks)

        connection.onPostFling(Velocity(0f, -100f), Velocity.Zero)
        connection.onPostScroll(Offset(0f, -10f), Offset.Zero, NestedScrollSource.SideEffect)

        assertEquals(1, checks)
    }

    @Test
    fun reverseFlingAndEligibilityChangesDoNotReplayBlockedInput() = runTest {
        var checks = 0
        var canAppend = false
        val connection = FeedPaginationConnection { if (canAppend) checks++ }

        connection.onPostScroll(Offset(0f, -10f), Offset.Zero, NestedScrollSource.UserInput)
        canAppend = true
        connection.onPreFling(Velocity(0f, 100f))
        connection.onPostScroll(Offset(0f, -10f), Offset.Zero, NestedScrollSource.SideEffect)
        connection.onPostFling(Velocity.Zero, Velocity.Zero)

        assertEquals(0, checks)

        connection.onPostScroll(Offset(0f, -10f), Offset.Zero, NestedScrollSource.UserInput)

        assertEquals(1, checks)
    }
}
