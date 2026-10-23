package com.ndynagn.kmp.news.feature.feed.presentation

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity

/**
 * Requests a pagination check only for forward user scrolls, including their fling.
 * Layout changes and programmatic restoration never authorize another page.
 * The caller checks current eligibility; blocked scrolls are not queued.
 */
internal class FeedPaginationConnection(private val onScrollForward: () -> Unit) : NestedScrollConnection {
    private var isForwardFling = false

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        val isUserScroll = source == NestedScrollSource.UserInput || isForwardFling

        if (isUserScroll && consumed.y + available.y < 0f) onScrollForward()

        return Offset.Zero
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        isForwardFling = available.y < 0f

        return Velocity.Zero
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        isForwardFling = false

        return Velocity.Zero
    }
}
