package com.ndynagn.kmp.news.feature.componentcatalog

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import com.ndynagn.kmp.news.AndroidAppTheme
import com.ndynagn.kmp.news.R
import com.ndynagn.kmp.news.components.designsystem.AppActionButton
import com.ndynagn.kmp.news.components.designsystem.AppActionEmphasis
import com.ndynagn.kmp.news.components.designsystem.AppCodeField
import com.ndynagn.kmp.news.components.designsystem.AppIconButton
import com.ndynagn.kmp.news.components.designsystem.AppImagePlaceholder
import com.ndynagn.kmp.news.components.designsystem.AppLoadingIndicator
import com.ndynagn.kmp.news.components.designsystem.AppNewsPreview
import com.ndynagn.kmp.news.components.designsystem.AppPasswordField
import com.ndynagn.kmp.news.components.designsystem.AppScreenState
import com.ndynagn.kmp.news.components.designsystem.AppSpacing
import com.ndynagn.kmp.news.components.designsystem.AppStatusView
import com.ndynagn.kmp.news.components.designsystem.AppTextField
import kotlinx.coroutines.launch

private data class ComponentCatalogActions(
    val select: (CatalogSection?) -> Unit,
    val setBusy: (Boolean) -> Unit,
    val setDisabled: (Boolean) -> Unit,
    val setImageUnavailable: (Boolean) -> Unit,
    val setDynamicColor: (Boolean) -> Unit,
    val openAuth: (CatalogAuthStep) -> Unit,
    val signOutSample: () -> Unit,
    val updateFields: (CatalogFieldsState) -> Unit,
    val setLoadedImage: (Boolean) -> Unit,
    val editProfile: () -> Unit,
    val updateProfileDraft: (String, String) -> Unit,
    val setProfileOutcome: (CatalogOutcome) -> Unit,
    val saveProfile: () -> Unit,
    val cancelProfileEdit: () -> Unit,
    val setFeedback: (CatalogFeedback) -> Unit,
    val setCompactFeedback: (Boolean) -> Unit,
    val setRetryFails: (Boolean) -> Unit,
    val retryFeedback: () -> Unit,
)

@Composable
internal fun ComponentCatalogRoute(onClose: () -> Unit) {
    val catalog = viewModel { ComponentCatalogViewModel() }
    val owner = viewModel { CatalogAuthFlowOwner() }
    val state by catalog.state.collectAsStateWithLifecycle()
    val step = state.authStep
    AndroidAppTheme(useDynamicColor = state.useDynamicColor) {
        if (step != null) {
            val auth = viewModel(viewModelStoreOwner = owner) { CatalogAuthViewModel(step) }
            val authState by auth.state.collectAsStateWithLifecycle()
            val close = {
                auth.close()
                owner.close()
                catalog.closeAuth()
            }
            val back = { if (!auth.back()) close() }
            BackHandler(onBack = back)
            LaunchedEffect(authState.isComplete) {
                if (authState.isComplete) {
                    auth.close()
                    owner.close()
                    catalog.closeAuth(completed = true)
                }
            }
            CatalogAuthScreen(
                authState,
                CatalogAuthActions(
                    auth::setEmail,
                    auth::setPassword,
                    auth::setRepeatedPassword,
                    auth::setCode,
                    auth::setOutcome,
                    { auth.navigate(CatalogAuthStep.RECOVERY) },
                    auth::submit,
                    auth::resend,
                ),
                onBack = back,
                onClose = close,
            )
        } else {
            ComponentCatalogScreen(
                state,
                ComponentCatalogActions(
                    catalog::select,
                    catalog::setBusy,
                    catalog::setDisabled,
                    catalog::setImageUnavailable,
                    catalog::setDynamicColor,
                    catalog::openAuth,
                    catalog::signOutSample,
                    catalog::updateFields,
                    catalog::setLoadedImage,
                    catalog::editProfile,
                    catalog::updateProfileDraft,
                    catalog::setProfileOutcome,
                    catalog::saveProfile,
                    catalog::cancelProfileEdit,
                    catalog::setFeedback,
                    catalog::setCompactFeedback,
                    catalog::setRetryFails,
                    catalog::retryFeedback,
                ),
                onClose,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComponentCatalogScreen(
    state: ComponentCatalogState,
    actions: ComponentCatalogActions,
    onClose: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    val latestState = rememberUpdatedState(state)
    val latestActions = rememberUpdatedState(actions)
    val back = {
        if (state.profile.isEditing) actions.cancelProfileEdit() else actions.select(null)
    }
    BackHandler(enabled = state.profile.isEditing, onBack = back)
    val scope = rememberCoroutineScope()
    var showsDialog by rememberSaveable { mutableStateOf(false) }
    val feedback = stringResource(R.string.catalog_feedback)
    val navigationLabel = if (state.section == null) R.string.catalog_close else R.string.catalog_back
    val notify = {
        scope.launch { snackbar.showSnackbar(feedback) }
        Unit
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(state.section?.titleResource() ?: R.string.catalog_title)) },
                navigationIcon = {
                    TextButton(onClick = { if (state.section == null) onClose() else back() }) {
                        Text(stringResource(navigationLabel))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        NavDisplay(
            backStack = listOfNotNull("root", state.section),
            onBack = back,
            modifier = Modifier.padding(padding),
            entryProvider = { destination ->
                NavEntry(destination) {
                    if (destination == "root") {
                        CatalogColumn {
                            Text(stringResource(R.string.catalog_demo))
                            CatalogSection.entries.forEach { section ->
                                AppActionButton(
                                    stringResource(section.titleResource()),
                                    { actions.select(section) },
                                    Modifier.fillMaxWidth().testTag("kit.section.${section.name.lowercase()}"),
                                    emphasis = AppActionEmphasis.SECONDARY,
                                )
                            }
                        }
                    } else {
                        CatalogSectionContent(
                            destination as CatalogSection,
                            latestState.value,
                            latestActions.value,
                            notify,
                            { showsDialog = true },
                        )
                    }
                }
            },
        )
    }
    if (showsDialog) {
        AlertDialog(
            onDismissRequest = { showsDialog = false },
            title = { Text(stringResource(R.string.catalog_confirmation)) },
            text = { Text(stringResource(R.string.catalog_demo)) },
            confirmButton = {
                TextButton(onClick = {
                    showsDialog = false
                    notify()
                }) { Text(stringResource(R.string.catalog_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showsDialog = false }) { Text(stringResource(R.string.catalog_close)) }
            },
        )
    }
}

@Composable
private fun CatalogSectionContent(
    section: CatalogSection,
    state: ComponentCatalogState,
    actions: ComponentCatalogActions,
    notify: () -> Unit,
    showDialog: () -> Unit,
) {
    if (section == CatalogSection.STATES) {
        CatalogStates(state, actions)
        return
    }
    CatalogColumn {
        when (section) {
            CatalogSection.FOUNDATIONS -> {
                CatalogSwitch(
                    stringResource(R.string.catalog_dynamic_colors),
                    state.useDynamicColor,
                    actions.setDynamicColor,
                )
                Text(stringResource(R.string.catalog_foundations_hint))
                CatalogFoundations()
            }

            CatalogSection.ACTIONS -> {
                CatalogSwitch(stringResource(R.string.catalog_loading), state.isBusy, actions.setBusy)
                CatalogSwitch(stringResource(R.string.catalog_disabled), state.isDisabled, actions.setDisabled)
                AppActionEmphasis.entries.forEach { emphasis ->
                    AppActionButton(
                        stringResource(emphasis.titleResource()),
                        if (emphasis ==
                            AppActionEmphasis.DESTRUCTIVE
                        ) {
                            showDialog
                        } else {
                            notify
                        },
                        Modifier.fillMaxWidth().testTag("kit.action.${emphasis.name.lowercase()}"),
                        emphasis,
                        isBusy = state.isBusy,
                        isEnabled = !state.isDisabled,
                    )
                }
                AppIconButton(
                    stringResource(R.string.catalog_news),
                    notify,
                    isBusy = state.isBusy,
                    isEnabled = !state.isDisabled,
                ) { Icon(painterResource(R.drawable.catalog_news), contentDescription = null) }
            }

            CatalogSection.FIELDS -> CatalogFields(state.fields, actions.updateFields)

            CatalogSection.NAVIGATION -> {
                var selected by rememberSaveable { mutableStateOf(0) }
                AppActionButton(stringResource(R.string.catalog_snackbar), notify)
                AppActionButton(stringResource(R.string.catalog_dialog), showDialog)
                NavigationBar {
                    listOf(R.string.catalog_news, R.string.catalog_favorites, R.string.catalog_profile).forEachIndexed {
                            index,
                            title,
                        ->
                        NavigationBarItem(
                            selected = selected == index,
                            onClick = { selected = index },
                            icon = {
                                Icon(
                                    painterResource(
                                        listOf(
                                            R.drawable.catalog_news,
                                            R.drawable.catalog_favorites,
                                            R.drawable.catalog_profile,
                                        )[index],
                                    ),
                                    contentDescription = null,
                                )
                            },
                            label = { Text(stringResource(title)) },
                        )
                    }
                }
                when (selected) {
                    0 -> AppNewsPreview(stringResource(R.string.catalog_article), Modifier.testTag("kit.tab.news"))

                    1 -> AppStatusView(
                        stringResource(R.string.catalog_empty),
                        Modifier.testTag("kit.tab.favorites"),
                        isCompact = true,
                    )

                    else -> Text(stringResource(R.string.catalog_sample_name), Modifier.testTag("kit.tab.profile"))
                }
                Text(stringResource(R.string.catalog_navigation_hint))
            }

            CatalogSection.NEWS -> {
                CatalogSwitch(
                    stringResource(R.string.catalog_image_unavailable),
                    state.imageUnavailable,
                    actions.setImageUnavailable,
                )
                CatalogSwitch(
                    stringResource(R.string.catalog_image_loaded),
                    state.showLoadedImage,
                    actions.setLoadedImage,
                )
                for (compact in listOf(false, true)) {
                    AppNewsPreview(
                        stringResource(R.string.catalog_article),
                        Modifier.fillMaxWidth(),
                        metadata = stringResource(R.string.catalog_article_metadata),
                        isCompact = compact,
                        image = {
                            val label = if (state.imageUnavailable) {
                                R.string.catalog_image_unavailable
                            } else {
                                R.string.catalog_image_loading
                            }
                            if (state.showLoadedImage && !state.imageUnavailable) {
                                Image(
                                    painterResource(R.drawable.catalog_illustration),
                                    stringResource(R.string.catalog_illustration),
                                    Modifier.fillMaxSize().testTag("kit.news.image.$compact"),
                                    contentScale = ContentScale.Crop,
                                )
                            } else {
                                AppImagePlaceholder(stringResource(label))
                            }
                        },
                    )
                }
            }

            CatalogSection.PROFILE -> {
                Text(stringResource(R.string.catalog_demo))
                if (state.sampleSignedIn) {
                    CatalogProfileScreen(
                        state.profile,
                        actions.editProfile,
                        actions.updateProfileDraft,
                        actions.setProfileOutcome,
                        actions.saveProfile,
                        actions.cancelProfileEdit,
                    )
                    AppActionButton(
                        stringResource(R.string.catalog_logout),
                        actions.signOutSample,
                        modifier = Modifier.testTag("kit.profile.logout"),
                        emphasis = AppActionEmphasis.DESTRUCTIVE,
                    )
                } else {
                    Text(stringResource(R.string.catalog_guest))
                    Text(stringResource(R.string.catalog_profile_hint))
                    CatalogForms(actions)
                }
            }

            CatalogSection.FORMS -> {
                Text(stringResource(R.string.catalog_demo))
                CatalogForms(actions)
            }

            CatalogSection.STATES -> Unit
        }
    }
}

@Composable
private fun CatalogForms(actions: ComponentCatalogActions) {
    for (step in CatalogAuthStep.entries) {
        AppActionButton(
            stringResource(step.titleResource()),
            { actions.openAuth(step) },
            Modifier.fillMaxWidth().testTag("kit.open.${step.name.lowercase()}"),
        )
    }
}

@Composable
private fun CatalogFields(state: CatalogFieldsState, onChange: (CatalogFieldsState) -> Unit) {
    AppTextField(
        state.email,
        { onChange(state.copy(email = it)) },
        stringResource(R.string.catalog_email),
        Modifier.fillMaxWidth().testTag("kit.fields.email"),
        keyboardType = KeyboardType.Email,
    )
    AppPasswordField(
        state.password,
        { onChange(state.copy(password = it)) },
        stringResource(R.string.catalog_password),
        Modifier.fillMaxWidth().testTag("kit.fields.password"),
    )
    AppCodeField(
        state.code,
        { onChange(state.copy(code = it.filter { digit -> digit in '0'..'9' }.take(6))) },
        stringResource(R.string.catalog_code),
        Modifier.fillMaxWidth().testTag("kit.fields.code"),
    )
    AppTextField(
        state.errorValue,
        { onChange(state.copy(errorValue = it)) },
        stringResource(R.string.catalog_email),
        Modifier.fillMaxWidth().testTag("kit.fields.error"),
        message = stringResource(R.string.catalog_request_error),
        isError = true,
    )
    AppTextField(
        "reader@example.test",
        {},
        stringResource(R.string.catalog_disabled),
        Modifier.fillMaxWidth().testTag("kit.fields.disabled"),
        isEnabled = false,
    )
}

@Composable
private fun CatalogStates(state: ComponentCatalogState, actions: ComponentCatalogActions) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewportHeight = maxHeight
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = viewportHeight / 2).verticalScroll(rememberScrollState())
                    .padding(AppSpacing.screen),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.group),
            ) {
                CatalogSwitch(
                    stringResource(R.string.catalog_compact),
                    state.isCompactFeedback,
                    actions.setCompactFeedback,
                )
                CatalogSwitch(stringResource(R.string.catalog_retry_fails), state.retryFails, actions.setRetryFails)
                CatalogFeedback.entries.filter { it != CatalogFeedback.CONTENT }.forEach { feedback ->
                    AppActionButton(
                        stringResource(
                            when (feedback) {
                                CatalogFeedback.LOADING -> R.string.catalog_loading
                                CatalogFeedback.EMPTY -> R.string.catalog_empty
                                else -> R.string.catalog_error
                            },
                        ),
                        { actions.setFeedback(feedback) },
                        Modifier.testTag("kit.state.select.${feedback.name.lowercase()}"),
                        emphasis = AppActionEmphasis.TEXT,
                    )
                }
            }
            if (state.isCompactFeedback) {
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(AppSpacing.screen),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.group),
                ) {
                    AppNewsPreview(stringResource(R.string.catalog_article), isCompact = true)
                    CatalogFeedbackContent(state, actions.retryFeedback)
                }
            } else {
                AppScreenState(Modifier.weight(1f)) { CatalogFeedbackContent(state, actions.retryFeedback) }
            }
        }
    }
}

@Composable
private fun CatalogFeedbackContent(state: ComponentCatalogState, retry: () -> Unit) {
    val modifier = Modifier.testTag("kit.state.${state.feedback.name.lowercase()}")
    when (state.feedback) {
        CatalogFeedback.LOADING -> AppLoadingIndicator(stringResource(R.string.catalog_loading), modifier)

        CatalogFeedback.EMPTY -> AppStatusView(
            stringResource(R.string.catalog_empty),
            modifier,
            message = stringResource(R.string.catalog_empty_hint),
            isCompact = state.isCompactFeedback,
        )

        CatalogFeedback.ERROR -> AppStatusView(
            stringResource(R.string.catalog_request_error),
            modifier,
            isCompact = state.isCompactFeedback,
        ) {
            AppActionButton(
                stringResource(R.string.catalog_retry),
                retry,
                Modifier.testTag("kit.state.retry"),
                emphasis = AppActionEmphasis.TEXT,
            )
        }

        CatalogFeedback.CONTENT -> Text(stringResource(R.string.catalog_feedback), modifier)
    }
}

@Composable
private fun CatalogSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, Modifier.weight(1f))
        Switch(checked, onChange, modifier = Modifier.semantics { contentDescription = label })
    }
}

@Composable
private fun CatalogColumn(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(AppSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.group),
    ) { content() }
}

private fun CatalogSection.titleResource(): Int = when (this) {
    CatalogSection.FOUNDATIONS -> R.string.catalog_foundations
    CatalogSection.ACTIONS -> R.string.catalog_actions
    CatalogSection.FIELDS -> R.string.catalog_fields
    CatalogSection.NAVIGATION -> R.string.catalog_navigation
    CatalogSection.NEWS -> R.string.catalog_news
    CatalogSection.STATES -> R.string.catalog_states
    CatalogSection.PROFILE -> R.string.catalog_profile
    CatalogSection.FORMS -> R.string.catalog_forms
}

private fun AppActionEmphasis.titleResource(): Int = when (this) {
    AppActionEmphasis.PRIMARY -> R.string.catalog_primary
    AppActionEmphasis.SECONDARY -> R.string.catalog_secondary
    AppActionEmphasis.TEXT -> R.string.catalog_text_action
    AppActionEmphasis.DESTRUCTIVE -> R.string.catalog_logout
}
