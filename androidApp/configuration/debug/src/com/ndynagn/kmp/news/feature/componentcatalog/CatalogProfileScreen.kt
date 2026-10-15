package com.ndynagn.kmp.news.feature.componentcatalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.ndynagn.kmp.news.R
import com.ndynagn.kmp.news.components.designsystem.AppActionButton
import com.ndynagn.kmp.news.components.designsystem.AppFormMessage
import com.ndynagn.kmp.news.components.designsystem.AppSpacing
import com.ndynagn.kmp.news.components.designsystem.AppTextField

@Composable
internal fun CatalogProfileScreen(
    state: CatalogProfileState,
    onEdit: () -> Unit,
    onDraftChange: (String, String) -> Unit,
    onOutcome: (CatalogOutcome) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AppSpacing.group)) {
        if (state.isEditing) {
            AppTextField(
                state.draftName,
                { onDraftChange(it, state.draftEmail) },
                stringResource(R.string.catalog_name),
                Modifier.fillMaxWidth().testTag("kit.profile.name"),
                isEnabled = !state.isSaving,
            )
            AppTextField(
                state.draftEmail,
                { onDraftChange(state.draftName, it) },
                stringResource(R.string.catalog_email),
                Modifier.fillMaxWidth().testTag("kit.profile.email"),
                isEnabled = !state.isSaving,
            )
            Text(stringResource(R.string.catalog_outcome))
            CatalogOutcome.entries.forEach { outcome ->
                FilterChip(
                    selected = state.outcome == outcome,
                    onClick = { onOutcome(outcome) },
                    enabled = !state.isSaving,
                    modifier = Modifier.testTag("kit.profile.outcome.${outcome.name.lowercase()}"),
                    label = { Text(stringResource(outcome.titleResource())) },
                )
            }
            state.message?.let {
                AppFormMessage(
                    stringResource(
                        if (it == CatalogMessage.TEMPORARY_ERROR) {
                            R.string.catalog_temporary_error
                        } else {
                            R.string.catalog_request_error
                        },
                    ),
                    isError = true,
                )
            }
            AppActionButton(
                stringResource(
                    if (state.message ==
                        CatalogMessage.TEMPORARY_ERROR
                    ) {
                        R.string.catalog_retry
                    } else {
                        R.string.catalog_save
                    },
                ),
                onSave,
                Modifier.fillMaxWidth().testTag("kit.profile.save"),
                isBusy = state.isSaving,
                isEnabled = state.draftName.isNotBlank() && state.draftEmail.isNotBlank(),
            )
            AppActionButton(stringResource(R.string.catalog_cancel), onCancel, Modifier.testTag("kit.profile.cancel"))
        } else {
            Text(state.name, Modifier.testTag("kit.profile.savedName"), style = MaterialTheme.typography.headlineSmall)
            Text(state.email, Modifier.testTag("kit.profile.savedEmail"))
            AppActionButton(stringResource(R.string.catalog_edit), onEdit, Modifier.testTag("kit.profile.edit"))
        }
    }
}
