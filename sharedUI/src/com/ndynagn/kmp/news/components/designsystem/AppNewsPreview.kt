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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ndynagn.kmp.news.resources.Res
import com.ndynagn.kmp.news.resources.kit_image_not_supported
import org.jetbrains.compose.resources.painterResource

/**
 * Metadata-only news composition; mapping, loading and interactions remain caller-owned.
 * Outlined cards use a 2.1:1 image area; compact rows use the base surface and an 80 dp thumbnail.
 * Titles wrap without truncation and missing metadata is omitted.
 */
@Composable
fun AppNewsPreview(
    title: String,
    modifier: Modifier = Modifier,
    metadata: String? = null,
    isCompact: Boolean = false,
    image: (@Composable () -> Unit)? = null,
) {
    if (isCompact) {
        Row(
            modifier.fillMaxWidth().padding(AppSpacing.group),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.group),
        ) {
            if (image != null) Box(Modifier.size(80.dp).clip(MaterialTheme.shapes.small)) { image() }
            NewsText(title, metadata, Modifier.weight(1f))
        }
    } else {
        OutlinedCard(
            modifier,
            colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            if (image != null) Box(Modifier.fillMaxWidth().aspectRatio(2.1f)) { image() }
            NewsText(title, metadata, Modifier.padding(AppSpacing.group))
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
        if (!metadata.isNullOrBlank()) {
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
            Column(
                Modifier.padding(AppSpacing.item),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(AppSpacing.item),
            ) {
                Icon(
                    painterResource(Res.drawable.kit_image_not_supported),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
