package com.ndynagn.kmp.news.feature.auth

import com.ndynagn.kmp.news.feature.auth.domain.AuthInputIssue
import com.ndynagn.kmp.news.feature.auth.domain.AuthInputValidator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AuthInputValidatorTest {
    private val validator = AuthInputValidator()

    @Test
    fun validatesEmailWithoutInventingDomainRestrictions() {
        assertNull(validator.email(" reader+tag@example.test "))
        assertEquals(AuthInputIssue.EMAIL, validator.email("reader@@example.test"))
        assertEquals(AuthInputIssue.EMAIL, validator.email("reader @example.test"))
    }

    @Test
    fun registrationPreservesWhitespaceAndCountsUnicodeCodePoints() {
        assertNull(validator.registration("reader@example.test", " spaces ", " spaces "))
        assertEquals(
            AuthInputIssue.PASSWORD_MISMATCH,
            validator.registration("reader@example.test", " spaces ", "spaces"),
        )
        assertEquals(
            AuthInputIssue.PASSWORD_TOO_SHORT,
            validator.registration("reader@example.test", "😀😀😀😀", "😀😀😀😀"),
        )
        assertNull(validator.registration("reader@example.test", "😀😀😀😀😀😀😀😀", "😀😀😀😀😀😀😀😀"))
    }

    @Test
    fun confirmationAcceptsLeadingZeroesAndRejectsNonAsciiDigits() {
        assertNull(validator.confirmation("reader@example.test", "012345"))
        assertEquals(AuthInputIssue.CODE, validator.confirmation("reader@example.test", "12345"))
        assertEquals(AuthInputIssue.CODE, validator.confirmation("reader@example.test", "１２３４５６"))
    }
}
