package com.ndynagn.kmp.news.feature.componentcatalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal enum class CatalogSection { FOUNDATIONS, ACTIONS, FIELDS, NAVIGATION, NEWS, STATES, PROFILE, FORMS }

internal enum class CatalogFeedback { LOADING, EMPTY, ERROR, CONTENT }

internal data class CatalogFieldsState(
    val email: String = "",
    val password: String = "",
    val code: String = "",
    val errorValue: String = "demo",
)

internal data class CatalogProfileState(
    val name: String = "Reader",
    val email: String = "reader@example.test",
    val draftName: String = "",
    val draftEmail: String = "",
    val isEditing: Boolean = false,
    val isSaving: Boolean = false,
    val outcome: CatalogOutcome = CatalogOutcome.SUCCESS,
    val message: CatalogMessage? = null,
)

internal data class ComponentCatalogState(
    val section: CatalogSection? = null,
    val isBusy: Boolean = false,
    val isDisabled: Boolean = false,
    val imageUnavailable: Boolean = false,
    val sampleSignedIn: Boolean = false,
    val authStep: CatalogAuthStep? = null,
    val useDynamicColor: Boolean = true,
    val fields: CatalogFieldsState = CatalogFieldsState(),
    val profile: CatalogProfileState = CatalogProfileState(),
    val feedback: CatalogFeedback = CatalogFeedback.LOADING,
    val isCompactFeedback: Boolean = false,
    val retryFails: Boolean = false,
    val isRetrying: Boolean = false,
    val showLoadedImage: Boolean = false,
)

/** Activity-owned catalog controls; fictional profile state never reaches the application session. */
internal class ComponentCatalogViewModel(private val pause: suspend (Long) -> Unit = { delay(it) }) : ViewModel() {
    private val mutableState = MutableStateFlow(ComponentCatalogState())
    val state = mutableState.asStateFlow()
    private var profileRequest: Job? = null
    private var feedbackRequest: Job? = null
    private var profileGeneration = 0
    private var feedbackGeneration = 0

    fun setDynamicColor(value: Boolean) {
        mutableState.value = state.value.copy(useDynamicColor = value)
    }

    fun select(section: CatalogSection?) {
        if (section != CatalogSection.PROFILE) cancelProfileEdit()

        if (section != CatalogSection.STATES) cancelFeedbackRequest()

        mutableState.value = state.value.copy(section = section)
    }

    fun setBusy(value: Boolean) {
        mutableState.value = state.value.copy(isBusy = value)
    }

    fun setDisabled(value: Boolean) {
        mutableState.value = state.value.copy(isDisabled = value)
    }

    fun setImageUnavailable(value: Boolean) {
        mutableState.value = state.value.copy(imageUnavailable = value)
    }

    fun openAuth(step: CatalogAuthStep) {
        mutableState.value = state.value.copy(authStep = step)
    }

    fun closeAuth(completed: Boolean = false) {
        mutableState.value = state.value.copy(authStep = null, sampleSignedIn = state.value.sampleSignedIn || completed)
    }

    fun signOutSample() {
        cancelProfileEdit()
        mutableState.value = state.value.copy(sampleSignedIn = false, profile = CatalogProfileState())
    }

    fun updateFields(fields: CatalogFieldsState) {
        mutableState.value = state.value.copy(fields = fields)
    }

    fun setLoadedImage(value: Boolean) {
        mutableState.value = state.value.copy(showLoadedImage = value)
    }

    fun editProfile() {
        val profile = state.value.profile

        mutableState.value = state.value.copy(
            profile = profile.copy(
                draftName = profile.name,
                draftEmail = profile.email,
                isEditing = true,
                message = null,
            ),
        )
    }

    fun updateProfileDraft(name: String, email: String) {
        val profile = state.value.profile
        if (profile.isSaving) return

        mutableState.value =
            state.value.copy(profile = profile.copy(draftName = name, draftEmail = email, message = null))
    }

    fun setProfileOutcome(outcome: CatalogOutcome) {
        if (state.value.profile.isSaving) return

        mutableState.value = state.value.copy(profile = state.value.profile.copy(outcome = outcome))
    }

    fun saveProfile() {
        val profile = state.value.profile
        if (!profile.isEditing || profile.isSaving || profile.draftName.isBlank() ||
            profile.draftEmail.isBlank()
        ) {
            return
        }

        val generation = ++profileGeneration
        mutableState.value = state.value.copy(profile = profile.copy(isSaving = true, message = null))
        profileRequest = viewModelScope.launch {
            pause(if (profile.outcome == CatalogOutcome.SLOW) 8_000 else 700)
            if (generation != profileGeneration) return@launch

            val updated = when (profile.outcome) {
                CatalogOutcome.SUCCESS, CatalogOutcome.SLOW -> profile.copy(
                    name = profile.draftName,
                    email = profile.draftEmail,
                    draftName = "",
                    draftEmail = "",
                    isEditing = false,
                    isSaving = false,
                )

                CatalogOutcome.ERROR -> profile.copy(isSaving = false, message = CatalogMessage.REQUEST_ERROR)

                CatalogOutcome.TEMPORARY_ERROR -> profile.copy(
                    isSaving = false,
                    message = CatalogMessage.TEMPORARY_ERROR,
                )
            }

            mutableState.value = state.value.copy(profile = updated)
        }
    }

    fun cancelProfileEdit() {
        profileGeneration += 1
        profileRequest?.cancel()
        profileRequest = null
        mutableState.value = state.value.copy(
            profile = state.value.profile.copy(
                draftName = "",
                draftEmail = "",
                isEditing = false,
                isSaving = false,
                message = null,
            ),
        )
    }

    fun setFeedback(feedback: CatalogFeedback) {
        cancelFeedbackRequest()
        mutableState.value = state.value.copy(feedback = feedback)
    }

    fun setCompactFeedback(value: Boolean) {
        mutableState.value = state.value.copy(isCompactFeedback = value)
    }

    fun setRetryFails(value: Boolean) {
        mutableState.value = state.value.copy(retryFails = value)
    }

    fun retryFeedback() {
        if (state.value.isRetrying) return

        val fails = state.value.retryFails
        val generation = ++feedbackGeneration
        mutableState.value = state.value.copy(feedback = CatalogFeedback.LOADING, isRetrying = true)
        feedbackRequest = viewModelScope.launch {
            pause(700)
            if (generation != feedbackGeneration) return@launch

            mutableState.value = state.value.copy(
                feedback = if (fails) CatalogFeedback.ERROR else CatalogFeedback.CONTENT,
                isRetrying = false,
            )
        }
    }

    private fun cancelFeedbackRequest() {
        feedbackGeneration += 1
        feedbackRequest?.cancel()
        feedbackRequest = null
        mutableState.value = state.value.copy(isRetrying = false)
    }

    override fun onCleared() {
        profileGeneration += 1
        feedbackGeneration += 1
        super.onCleared()
    }
}
