package com.ndynagn.kmp.news.feature.componentcatalog

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ndynagn.kmp.news.R
import com.ndynagn.kmp.news.components.designsystem.AppSpacing

@Composable
internal fun CatalogFoundations() {
    val typography = MaterialTheme.typography
    val styles = listOf(
        "displayLarge" to typography.displayLarge, "displayMedium" to typography.displayMedium,
        "displaySmall" to typography.displaySmall, "headlineLarge" to typography.headlineLarge,
        "headlineMedium" to typography.headlineMedium, "headlineSmall" to typography.headlineSmall,
        "titleLarge" to typography.titleLarge, "titleMedium" to typography.titleMedium,
        "titleSmall" to typography.titleSmall, "bodyLarge" to typography.bodyLarge,
        "bodyMedium" to typography.bodyMedium, "bodySmall" to typography.bodySmall,
        "labelLarge" to typography.labelLarge, "labelMedium" to typography.labelMedium,
        "labelSmall" to typography.labelSmall,
    )
    styles.forEach { (name, style) -> Text(name + " · " + stringResource(R.string.catalog_heading), style = style) }

    val colors = MaterialTheme.colorScheme
    val pairs = listOf(
        "primary / onPrimary" to (colors.primary to colors.onPrimary),
        "primaryContainer / onPrimaryContainer" to (colors.primaryContainer to colors.onPrimaryContainer),
        "secondary / onSecondary" to (colors.secondary to colors.onSecondary),
        "secondaryContainer / onSecondaryContainer" to (colors.secondaryContainer to colors.onSecondaryContainer),
        "tertiary / onTertiary" to (colors.tertiary to colors.onTertiary),
        "tertiaryContainer / onTertiaryContainer" to (colors.tertiaryContainer to colors.onTertiaryContainer),
        "error / onError" to (colors.error to colors.onError),
        "errorContainer / onErrorContainer" to (colors.errorContainer to colors.onErrorContainer),
        "surface / onSurface" to (colors.surface to colors.onSurface),
        "surfaceVariant / onSurfaceVariant" to (colors.surfaceVariant to colors.onSurfaceVariant),
        "inverseSurface / inverseOnSurface" to (colors.inverseSurface to colors.inverseOnSurface),
    )
    pairs.forEach { (name, pair) ->
        Text(name, Modifier.fillMaxWidth().background(pair.first).padding(AppSpacing.item), color = pair.second)
    }

    val shapes = MaterialTheme.shapes
    listOf(
        "extraSmall" to shapes.extraSmall,
        "small" to shapes.small,
        "medium" to shapes.medium,
        "large" to shapes.large,
        "extraLarge" to shapes.extraLarge,
    ).forEach { (name, shape) ->
        Text(
            name,
            Modifier.fillMaxWidth().background(colors.secondaryContainer, shape).padding(AppSpacing.group),
            color = colors.onSecondaryContainer,
        )
    }

    var expanded by rememberSaveable { mutableStateOf(false) }
    val size by animateDpAsState(if (expanded) 96.dp else 48.dp, MaterialTheme.motionScheme.defaultSpatialSpec())
    val motionLabel = stringResource(R.string.catalog_motion)
    Text(motionLabel)
    Box(Modifier.fillMaxWidth().height(112.dp)) {
        Box(
            Modifier.size(size).background(colors.primary, shapes.large).semantics {
                contentDescription = motionLabel
            }.clickable(
                onClickLabel = motionLabel,
                role = Role.Button,
            ) { expanded = !expanded },
        )
    }
}
