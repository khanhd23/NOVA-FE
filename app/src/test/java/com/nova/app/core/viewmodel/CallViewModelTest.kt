package com.nova.app.core.viewmodel

import com.nova.app.core.backend.BackendRuntime
import com.nova.app.core.model.CallDirection
import com.nova.app.core.model.CallEndEvent
import com.nova.app.core.model.CallEndReason
import com.nova.app.core.model.CallStatus
import com.nova.app.core.model.CallType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy

@OptIn(ExperimentalCoroutinesApi::class)
class CallViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    /** Records every backend call as "method(arg1, arg2)"; every method returns null. */
    private val backendCalls = mutableListOf<String>()
    private val backend: BackendRuntime = Proxy.newProxyInstance(
        BackendRuntime::class.java.classLoader,
        arrayOf(BackendRuntime::class.java),
    ) { _, method, args ->
        if (method.declaringClass == Any::class.java) return@newProxyInstance null
        val shownArgs = args.orEmpty().filterNot { it is kotlin.coroutines.Continuation<*> }.joinToString()
        synchronized(backendCalls) { backendCalls += "${method.name}($shownArgs)" }
        null
    } as BackendRuntime

    private lateinit var viewModel: CallViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        viewModel = CallViewModel(backend)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.ringIncoming(callId: String = "call-1", type: CallType = CallType.Voice): Boolean {
        val accepted = viewModel.receiveIncomingCall("Alex", "thread-1", callId, "user-2", type)
        runCurrent()
        return accepted
    }

    /** The call timer ticks forever; end the call so runTest can drain the scheduler. */
    private fun TestScope.endCallToStopTimer() {
        viewModel.hangUp()
        runCurrent()
    }

    private fun TestScope.collectEndEvents(): MutableList<CallEndEvent> {
        val events = mutableListOf<CallEndEvent>()
        backgroundScope.launch(dispatcher) { viewModel.endEvents.collect { events += it } }
        runCurrent()
        return events
    }

    @Test
    fun `incoming call starts ringing`() = runTest(dispatcher) {
        assertTrue(ringIncoming())

        val state = viewModel.uiState.value
        assertTrue(state.isActive)
        assertEquals(CallStatus.Ringing, state.status)
        assertEquals(CallDirection.Incoming, state.direction)
        assertEquals("call-1", state.callId)
    }

    @Test
    fun `second incoming call during a call is rejected as busy`() = runTest(dispatcher) {
        ringIncoming(callId = "call-1")

        val accepted = viewModel.receiveIncomingCall("Bob", "thread-2", "call-2", "user-3", CallType.Video)
        runCurrent()

        assertFalse(accepted)
        assertEquals("call-1", viewModel.uiState.value.callId)
        assertTrue(backendCalls.contains("endCall(call-2, ${CallEndReason.Busy})"))
    }

    @Test
    fun `same incoming call delivered twice does not restart it`() = runTest(dispatcher) {
        ringIncoming(callId = "call-1")
        viewModel.answerCall()
        runCurrent()

        assertTrue(ringIncoming(callId = "call-1"))

        assertEquals(CallStatus.InCall, viewModel.uiState.value.status)
        endCallToStopTimer()
    }

    @Test
    fun `unanswered incoming call ends as missed`() = runTest(dispatcher) {
        val events = collectEndEvents()
        ringIncoming()

        advanceTimeBy(36_000)
        runCurrent()

        assertFalse(viewModel.uiState.value.isActive)
        assertEquals(CallEndReason.Missed, events.single().summary.endReason)
        assertTrue(backendCalls.contains("endCall(call-1, ${CallEndReason.Missed})"))
    }

    @Test
    fun `declining an incoming call reports declined`() = runTest(dispatcher) {
        val events = collectEndEvents()
        ringIncoming()

        viewModel.hangUp()
        runCurrent()

        assertEquals(CallEndReason.Declined, events.single().summary.endReason)
        assertTrue(backendCalls.contains("endCall(call-1, ${CallEndReason.Declined})"))
    }

    @Test
    fun `answered call counts duration once media is connected`() = runTest(dispatcher) {
        ringIncoming()

        viewModel.answerCall()
        runCurrent()
        advanceTimeBy(3_100)
        runCurrent()

        val state = viewModel.uiState.value
        assertEquals(CallStatus.InCall, state.status)
        assertTrue(state.isMediaConnected)
        assertEquals(3, state.durationSeconds)
        assertTrue(backendCalls.contains("answerCall(call-1)"))
        endCallToStopTimer()
    }

    @Test
    fun `peer hanging up before answer counts as missed`() = runTest(dispatcher) {
        val events = collectEndEvents()
        ringIncoming()

        viewModel.onRemoteHangup("call-1")
        runCurrent()

        assertEquals(CallEndReason.Missed, events.single().summary.endReason)
    }

    @Test
    fun `outgoing call without a backend session is canceled`() = runTest(dispatcher) {
        val events = collectEndEvents()

        viewModel.openVoiceCall("Alex", threadId = "thread-1", peerUserId = "user-2")
        runCurrent()

        assertFalse(viewModel.uiState.value.isActive)
        assertEquals(CallEndReason.Canceled, events.single().summary.endReason)
    }
}
