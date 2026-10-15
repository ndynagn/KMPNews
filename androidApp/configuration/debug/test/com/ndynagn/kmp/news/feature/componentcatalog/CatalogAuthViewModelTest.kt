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
class CatalogAuthViewModelTest {
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
    fun registrationClearsSecretsAndStartsConfirmationCooldown() = runTest(dispatcher) {
        val model = CatalogAuthViewModel(CatalogAuthStep.REGISTER, { testScheduler.currentTime })
        model.setEmail("reader@example.test")
        model.setPassword("demo-password")
        model.setRepeatedPassword("demo-password")

        model.submit()
        advanceTimeBy(700)
        runCurrent()

        assertEquals(CatalogAuthStep.CONFIRM, model.state.value.step)
        assertEquals(60, model.state.value.resendSeconds)
        assertEquals("", model.state.value.password)
        assertEquals("", model.state.value.repeatedPassword)
        model.close()
    }

    @Test
    fun sixthDigitPreservesLeadingZeroesAndSubmitsOnlyOnce() = runTest(dispatcher) {
        var requests = 0
        val model = CatalogAuthViewModel(CatalogAuthStep.CONFIRM, pause = {
            requests += 1
            delay(it)
        })

        model.setCode("001234")
        model.submit()
        runCurrent()

        assertEquals("001234", model.state.value.code)
        assertEquals(1, requests)
        advanceTimeBy(700)
        runCurrent()
        assertTrue(model.state.value.isComplete)
        assertEquals("", model.state.value.code)
        model.close()
    }

    @Test
    fun backRejectsLateResultsEvenWhenDelayIgnoresCancellation() = runTest(dispatcher) {
        val model = CatalogAuthViewModel(
            CatalogAuthStep.LOGIN,
            pause = { withContext(NonCancellable) { delay(it) } },
        )
        model.setEmail("reader@example.test")
        model.navigate(CatalogAuthStep.RECOVERY)
        model.submit()
        runCurrent()

        assertTrue(model.back())
        advanceTimeBy(700)
        runCurrent()

        assertEquals(CatalogAuthStep.LOGIN, model.state.value.step)
        assertEquals("reader@example.test", model.state.value.email)
        assertEquals(null, model.state.value.message)
        assertFalse(model.state.value.isBusy)
        model.close()
    }

    @Test
    fun temporaryCodeFailureAllowsExplicitRetryButResendFailureDoesNot() = runTest(dispatcher) {
        val model = CatalogAuthViewModel(CatalogAuthStep.CONFIRM)
        model.setOutcome(CatalogOutcome.TEMPORARY_ERROR)
        model.setCode("001234")
        advanceTimeBy(700)
        runCurrent()
        assertTrue(model.state.value.canRetryCode)

        model.resend()
        advanceTimeBy(700)
        runCurrent()

        assertFalse(model.state.value.canRetryCode)
        assertEquals(CatalogMessage.TEMPORARY_ERROR, model.state.value.message)
        model.setOutcome(CatalogOutcome.SUCCESS)
        model.setCode("001235")
        advanceTimeBy(700)
        runCurrent()
        assertTrue(model.state.value.isComplete)
        model.close()
    }

    @Test
    fun resendCooldownTracksRecipientAndSurvivesBack() = runTest(dispatcher) {
        val model = CatalogAuthViewModel(CatalogAuthStep.LOGIN, { testScheduler.currentTime })
        model.setEmail("first@example.test")
        model.navigate(CatalogAuthStep.CONFIRM)
        model.resend()
        advanceTimeBy(700)
        runCurrent()

        model.back()
        model.setEmail("second@example.test")
        model.navigate(CatalogAuthStep.CONFIRM)
        assertEquals(0, model.state.value.resendSeconds)
        advanceTimeBy(2_000)
        runCurrent()
        model.back()
        model.setEmail("first@example.test")
        model.navigate(CatalogAuthStep.CONFIRM)

        assertEquals(58, model.state.value.resendSeconds)
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(0, model.state.value.resendSeconds)
        model.close()
    }

    @Test
    fun slowConfirmationAndResendExposeOperationAndClearItOnCancelOrCompletion() = runTest(dispatcher) {
        val model = CatalogAuthViewModel(CatalogAuthStep.LOGIN)
        model.navigate(CatalogAuthStep.CONFIRM)
        model.setOutcome(CatalogOutcome.SLOW)

        model.setCode("001234")
        runCurrent()

        assertEquals(CatalogAuthOperation.SUBMIT, model.state.value.operation)
        model.back()
        assertEquals(null, model.state.value.operation)
        assertEquals("", model.state.value.code)
        model.navigate(CatalogAuthStep.CONFIRM)
        model.resend()
        runCurrent()
        assertEquals(CatalogAuthOperation.RESEND, model.state.value.operation)
        advanceTimeBy(8_000)
        runCurrent()
        assertEquals(null, model.state.value.operation)
        assertFalse(model.state.value.isComplete)
        model.close()
    }

    @Test
    fun closeClearsFlowAndRejectsPendingSuccess() = runTest(dispatcher) {
        val model = CatalogAuthViewModel(CatalogAuthStep.LOGIN)
        model.setEmail("reader@example.test")
        model.setPassword("demo-password")
        model.setOutcome(CatalogOutcome.SLOW)
        model.submit()
        runCurrent()

        model.close()
        advanceTimeBy(8_000)
        runCurrent()

        assertEquals("", model.state.value.email)
        assertEquals("", model.state.value.password)
        assertFalse(model.state.value.isComplete)
        assertFalse(model.state.value.isBusy)
    }

    @Test
    fun explicitRetryRechecksUnchangedCodeAndCooldownSuppressesResend() = runTest(dispatcher) {
        var requests = 0
        val model = CatalogAuthViewModel(CatalogAuthStep.CONFIRM, { testScheduler.currentTime }, {
            requests += 1
            delay(it)
        })
        model.resend()
        advanceTimeBy(700)
        runCurrent()
        assertEquals(60, model.state.value.resendSeconds)

        model.resend()
        runCurrent()
        assertEquals(1, requests)
        model.setOutcome(CatalogOutcome.TEMPORARY_ERROR)
        model.setCode("001234")
        advanceTimeBy(700)
        runCurrent()
        assertTrue(model.state.value.canRetryCode)
        model.setOutcome(CatalogOutcome.SUCCESS)

        model.submit()
        model.submit()
        advanceTimeBy(700)
        runCurrent()

        assertEquals(3, requests)
        assertTrue(model.state.value.isComplete)
        model.close()
        assertEquals("", model.state.value.email)
    }
}
