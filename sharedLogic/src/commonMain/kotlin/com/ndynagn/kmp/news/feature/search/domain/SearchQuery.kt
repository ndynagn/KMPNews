package com.ndynagn.kmp.news.feature.search.domain

/** Shared query policy for clients and repositories. Counts Unicode code points, not UTF-16 units. */
object SearchQuery {
    /** Distinguishes blank input from a nonblank query that exceeds the length limit. */
    fun isBlank(input: String): Boolean = input.trim(::isQueryWhitespace).isEmpty()

    /** Returns a trimmed valid query or null for blank/overlong input; never truncates user input. */
    fun normalize(input: String): String? {
        val query = input.trim(::isQueryWhitespace)
        var count = 0
        var index = 0
        while (index < query.length) {
            val char = query[index++]
            if (char.isHighSurrogate() && index < query.length && query[index].isLowSurrogate()) index++
            count++
        }
        return query.takeIf { count in 1..100 }
    }

    // Match ECMAScript String.trim in the mediator, including BOM and excluding NEL.
    private fun isQueryWhitespace(char: Char): Boolean = when (char) {
        in '\u0009'..'\u000D', '\u0020', '\u00A0', '\u1680',
        in '\u2000'..'\u200A', '\u2028', '\u2029', '\u202F', '\u205F', '\u3000', '\uFEFF',
        -> true

        else -> false
    }
}
