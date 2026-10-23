package com.ndynagn.kmp.news.components.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.ndynagn.kmp.news.resources.Res
import com.ndynagn.kmp.news.resources.kit_loading
import org.jetbrains.compose.resources.stringResource

/** Semantic action hierarchy, independent of feature and navigation ownership. */
enum class AppActionEmphasis { PRIMARY, SECONDARY, TEXT, DESTRUCTIVE }

/** Action with a retained title footprint while busy; busy also prevents repeat activation. */
@Composable
fun AppActionButton(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasis: AppActionEmphasis = AppActionEmphasis.PRIMARY,
    isBusy: Boolean = false,
    isEnabled: Boolean = true,
) {
    val loading = stringResource(Res.string.kit_loading)
    val buttonModifier = modifier.heightIn(min = 48.dp).semantics {
        text = AnnotatedString(title)
        if (isBusy) stateDescription = loading
    }
    val label: @Composable () -> Unit = {
        Box(contentAlignment = Alignment.Center) {
            Text(title, Modifier.alpha(if (isBusy) 0f else 1f).clearAndSetSemantics {})
            if (isBusy) {
                Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp).clearAndSetSemantics {}, strokeWidth = 2.dp)
                }
            }
        }
    }

    when (emphasis) {
        AppActionEmphasis.PRIMARY -> Button(
            onClick = onClick,
            shapes = ButtonDefaults.shapes(),
            modifier = buttonModifier,
            enabled = isEnabled && !isBusy,
        ) { label() }

        AppActionEmphasis.SECONDARY -> FilledTonalButton(
            onClick = onClick,
            shapes = ButtonDefaults.shapes(),
            modifier = buttonModifier,
            enabled = isEnabled && !isBusy,
        ) { label() }

        AppActionEmphasis.TEXT -> TextButton(
            onClick = onClick,
            shapes = ButtonDefaults.shapes(),
            modifier = buttonModifier,
            enabled = isEnabled && !isBusy,
        ) { label() }

        AppActionEmphasis.DESTRUCTIVE -> FilledTonalButton(
            onClick = onClick,
            shapes = ButtonDefaults.shapes(),
            modifier = buttonModifier,
            enabled = isEnabled && !isBusy,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
        ) { label() }
    }
}

/** Named icon action with a 48 dp minimum touch target and stable busy geometry. */
@Composable
fun AppIconButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isBusy: Boolean = false,
    isEnabled: Boolean = true,
    icon: @Composable () -> Unit,
) {
    val loading = stringResource(Res.string.kit_loading)
    IconButton(
        onClick = onClick,
        enabled = isEnabled && !isBusy,
        modifier = modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).semantics {
            contentDescription = label
            if (isBusy) stateDescription = loading
        },
    ) {
        if (isBusy) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) else icon()
    }
}
