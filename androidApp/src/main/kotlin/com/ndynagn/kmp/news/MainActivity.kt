package com.ndynagn.kmp.news

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.ndynagn.kmp.news.feature.home.presentation.MobileApp
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val newsApplication = application as NewsApplication
        setContent {
            var dependencies by remember {
                mutableStateOf(runCatching { newsApplication.feedDependencies }.getOrNull())
            }
            MobileApp(
                dependencies?.newsRepository,
                dependencies?.refreshFeedIfNeeded,
                newsApplication.feedConfiguration.isConfigured,
                authRepository = newsApplication.authDependencies.authRepository,
                onRetryFeed = { dependencies = runCatching { newsApplication.feedDependencies }.getOrNull() },
                formatDate = { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it)) },
            )
        }
    }
}
