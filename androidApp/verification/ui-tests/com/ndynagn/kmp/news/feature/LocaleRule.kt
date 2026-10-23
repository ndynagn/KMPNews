package com.ndynagn.kmp.news.feature

import android.os.LocaleList
import java.util.Locale
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/** Pins the isolated test process locale and restores it even after a failed assertion. */
class LocaleRule(private val language: String = "ru") : TestRule {
    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            val previous = LocaleList.getDefault()
            setTestLocale(language)

            try {
                base.evaluate()
            } finally {
                LocaleList.setDefault(previous)
            }
        }
    }
}

internal fun setTestLocale(language: String) {
    val locale = Locale.forLanguageTag(language)

    if (LocaleList.getDefault()[0] != locale) LocaleList.setDefault(LocaleList(locale))
}
