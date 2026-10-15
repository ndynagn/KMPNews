package com.ndynagn.kmp.news

import android.content.Intent
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.ndynagn.kmp.news.feature.componentcatalog.ComponentCatalogActivity

@Composable
internal fun ComponentCatalogAction() {
    val context = LocalContext.current
    TextButton(
        onClick = { context.startActivity(Intent(context, ComponentCatalogActivity::class.java)) },
        modifier = Modifier.testTag("kit.entry"),
    ) { Text(stringResource(R.string.catalog_title)) }
}
