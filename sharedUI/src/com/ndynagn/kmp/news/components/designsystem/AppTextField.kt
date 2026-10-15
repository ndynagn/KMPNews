package com.ndynagn.kmp.news.components.designsystem

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.ndynagn.kmp.news.resources.Res
import com.ndynagn.kmp.news.resources.kit_hide_password
import com.ndynagn.kmp.news.resources.kit_show_password
import org.jetbrains.compose.resources.stringResource

/** Single-line input; callers own validation, IME policy and any submission behavior. */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true,
    message: String? = null,
    isError: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Default,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text(label) },
        singleLine = true,
        enabled = isEnabled,
        isError = isError,
        supportingText = message?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
    )
}

/** One stable password editor; changing visibility never replaces its editing node. */
@Composable
fun AppPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true,
    message: String? = null,
    isError: Boolean = false,
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text(label) },
        singleLine = true,
        enabled = isEnabled,
        isError = isError,
        supportingText = message?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            TextButton(onClick = { visible = !visible }, enabled = isEnabled) {
                Text(stringResource(if (visible) Res.string.kit_hide_password else Res.string.kit_show_password))
            }
        },
    )
}

/** Code stays a string, preserving leading zeroes; filtering and auto-submit belong to the caller. */
@Composable
fun AppCodeField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true,
    message: String? = null,
    isError: Boolean = false,
) {
    AppTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        modifier = modifier,
        isEnabled = isEnabled,
        message = message,
        isError = isError,
        keyboardType = KeyboardType.Number,
        imeAction = ImeAction.Done,
    )
}

/** Inline form feedback. Features decide its wording and when it is shown. */
@Composable
fun AppFormMessage(message: String, modifier: Modifier = Modifier, isError: Boolean = false) {
    Text(
        message,
        modifier = modifier.semantics { if (isError) liveRegion = LiveRegionMode.Polite },
        style = MaterialTheme.typography.bodySmall,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
