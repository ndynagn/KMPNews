package com.ndynagn.kmp.news.components.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Metadata-only news composition; mapping, loading and interactions remain caller-owned.
 * Regular cards use a 2.1:1 image area and untruncated titles; compact rows retain an 80 dp thumbnail.
 */
@Composable
fun AppNewsPreview(
    title: String,
    modifier: Modifier = Modifier,
    metadata: String? = null,
    isCompact: Boolean = false,
    image: (@Composable () -> Unit)? = null,
) {
    Card(modifier) {
        if (isCompact) {
            Row(Modifier.padding(AppSpacing.group), horizontalArrangement = Arrangement.spacedBy(AppSpacing.group)) {
                if (image != null) Box(Modifier.size(80.dp)) { image() }
                NewsText(title, metadata, Modifier.weight(1f))
            }
        } else {
            if (image != null) Box(Modifier.fillMaxWidth().aspectRatio(2.1f)) { image() }
            NewsText(title, metadata, Modifier.padding(horizontal = AppSpacing.group, vertical = AppSpacing.item))
        }
    }
}

@Composable
private fun NewsText(title: String, metadata: String?, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(AppSpacing.item)) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth(),
        )
        if (metadata != null) {
            Text(
                metadata,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Named loading or unavailable image surface, with optional caller-supplied illustration. */
@Composable
fun AppImagePlaceholder(label: String, modifier: Modifier = Modifier, illustration: (@Composable () -> Unit)? = null) {
    Box(
        modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (illustration != null) {
            illustration()
        } else {
            Text(
                label,
                Modifier.padding(AppSpacing.item),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
