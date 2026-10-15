package com.ndynagn.kmp.news.feature.componentcatalog

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ComponentCatalogViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun profileSavePublishesOnceAndRejectsRepeatedActivation() = runTest(dispatcher) {
        var requests = 0
        val model = ComponentCatalogViewModel {
            requests += 1
            delay(it)
        }
        model.editProfile()
        model.updateProfileDraft("Demo Reader", "updated@example.test")

        model.saveProfile()
        model.saveProfile()
        runCurrent()

        assertEquals(1, requests)
        assertEquals("Reader", model.state.value.profile.name)
        assertTrue(model.state.value.profile.isSaving)
        advanceTimeBy(700)
        runCurrent()
        assertEquals("Demo Reader", model.state.value.profile.name)
        assertEquals("updated@example.test", model.state.value.profile.email)
        assertFalse(model.state.value.profile.isEditing)
    }

    @Test
    fun cancelledProfileIgnoresLateResponseAndDiscardsDraft() = runTest(dispatcher) {
        val model = ComponentCatalogViewModel { withContext(NonCancellable) { delay(it) } }
        model.select(CatalogSection.PROFILE)
        model.editProfile()
        model.updateProfileDraft("Discard", "discard@example.test")
        model.saveProfile()
        runCurrent()

        model.cancelProfileEdit()
        model.editProfile()
        advanceTimeBy(700)
        runCurrent()

        assertEquals("Reader", model.state.value.profile.name)
        assertEquals("Reader", model.state.value.profile.draftName)
        assertFalse(model.state.value.profile.isSaving)
    }

    @Test
    fun failedProfileRetainsDraftAndExplicitRetryCanSucceed() = runTest(dispatcher) {
        val model = ComponentCatalogViewModel()
        model.editProfile()
        model.updateProfileDraft("Retry Reader", "retry@example.test")
        model.setProfileOutcome(CatalogOutcome.TEMPORARY_ERROR)

        model.saveProfile()
        advanceTimeBy(700)
        runCurrent()

        assertEquals(CatalogMessage.TEMPORARY_ERROR, model.state.value.profile.message)
        assertEquals("Retry Reader", model.state.value.profile.draftName)
        assertEquals("Reader", model.state.value.profile.name)
        model.setProfileOutcome(CatalogOutcome.SUCCESS)
        model.saveProfile()
        advanceTimeBy(700)
        runCurrent()
        assertEquals("Retry Reader", model.state.value.profile.name)
    }

    @Test
    fun slowProfileAndLogoutCannotPublishObsoleteDraft() = runTest(dispatcher) {
        val model = ComponentCatalogViewModel { withContext(NonCancellable) { delay(it) } }
        model.closeAuth(completed = true)
        model.editProfile()
        model.updateProfileDraft("Old", "old@example.test")
        model.setProfileOutcome(CatalogOutcome.SLOW)
        model.saveProfile()
        advanceTimeBy(7_999)
        runCurrent()
        assertTrue(model.state.value.profile.isSaving)

        model.signOutSample()
        advanceTimeBy(1)
        runCurrent()

        assertFalse(model.state.value.sampleSignedIn)
        assertEquals("Reader", model.state.value.profile.name)
        assertFalse(model.state.value.profile.isEditing)
    }

    @Test
    fun feedbackRetryTerminatesWithSelectedResultAndRejectsDuplicate() = runTest(dispatcher) {
        var requests = 0
        val model = ComponentCatalogViewModel {
            requests += 1
            delay(it)
        }
        model.setFeedback(CatalogFeedback.ERROR)

        model.retryFeedback()
        model.retryFeedback()
        advanceTimeBy(700)
        runCurrent()

        assertEquals(1, requests)
        assertEquals(CatalogFeedback.CONTENT, model.state.value.feedback)
        assertFalse(model.state.value.isRetrying)
        model.setRetryFails(true)
        model.retryFeedback()
        advanceTimeBy(700)
        runCurrent()
        assertEquals(CatalogFeedback.ERROR, model.state.value.feedback)
        assertFalse(model.state.value.isRetrying)
    }

    @Test
    fun leavingFeedbackRejectsLateResultAndFreshOwnerClearsInput() = runTest(dispatcher) {
        val model = ComponentCatalogViewModel { withContext(NonCancellable) { delay(it) } }
        model.select(CatalogSection.STATES)
        model.updateFields(CatalogFieldsState(password = "fictional", code = "001234", errorValue = "edited"))
        model.retryFeedback()
        runCurrent()

        model.select(CatalogSection.FIELDS)
        advanceTimeBy(700)
        runCurrent()

        assertEquals(CatalogFeedback.LOADING, model.state.value.feedback)
        assertFalse(model.state.value.isRetrying)
        assertEquals("edited", model.state.value.fields.errorValue)
        val recreatedOwner = ComponentCatalogViewModel()
        assertEquals("", recreatedOwner.state.value.fields.password)
        assertEquals("", recreatedOwner.state.value.fields.code)
    }
}
