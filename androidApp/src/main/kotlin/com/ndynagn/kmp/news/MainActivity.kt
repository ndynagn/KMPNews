package com.ndynagn.kmp.news

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ndynagn.kmp.news.feature.home.presentation.MobileApp
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val dependencies = (application as NewsApplication).feedDependencies
        setContent {
            MobileApp(
                dependencies.newsRepository,
                dependencies.refreshFeedIfNeeded,
                (application as NewsApplication).feedConfiguration.isConfigured,
                formatDate = { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it)) },
            )
        }
    }
}
