package com.ndynagn.kmp.news.feature.feed.di

/** Attempts every feed cleanup step, preserving the original failure and cancellation identity. */
internal fun closeFeedResources(originalFailure: Throwable? = null, vararg closeActions: () -> Unit) {
    var failure = originalFailure

    for (close in closeActions) {
        try {
            close()
        } catch (closeFailure: Throwable) {
            if (failure == null) {
                failure = closeFailure
            } else if (failure !== closeFailure) {
                failure.addSuppressed(closeFailure)
            }
        }
    }

    failure?.let { throw it }
}
