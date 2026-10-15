package com.ndynagn.kmp.news.feature.componentcatalog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

/** Debug-only presentation playground; startup and app-owned services remain with the host. */
class ComponentCatalogActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ComponentCatalogRoute(onClose = ::finish)
        }
    }
}
