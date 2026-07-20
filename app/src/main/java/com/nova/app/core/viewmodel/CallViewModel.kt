package com.nova.app.core.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nova.app.core.backend.BackendRealtimeEvent
import com.nova.app.core.backend.BackendRealtimeEventType
import com.nova.app.core.backend.BackendRuntime
import com.nova.app.core.model.CallDirection
import com.nova.app.core.model.CallEndEvent
import com.nova.app.core.model.CallEndReason
import com.nova.app.core.model.CallSessionUiState
import com.nova.app.core.model.CallStatus
import com.nova.app.core.model.CallSummaryUiState
import com.nova.app.core.model.CallType
import com.nova.app.core.model.displayLabel
import com.nova.app.core.webrtc.NovaWebRtcEngineRegistry
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val RING_TIMEOUT_MS = 30_000L
private const val TIME_LABEL_PATTERN = "HH:mm"

class CallViewModel(
    private val backendRuntime: BackendRuntime,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CallSessionUiState())
    val uiState: StateFlow<CallSessionUiState> = _uiState.asStateFlow()

    private val _lastSummary = MutableStateFlow<CallSummaryUiState?>(null)
    val lastSummary: StateFlow<CallSummaryUiState?> = _lastSummary.asStateFlow()

    private val _endEvents = MutableSharedFlow<CallEndEvent>(extraBufferCapacity = 1)
    val endEvents: SharedFlow<CallEndEvent> = _endEvents.asSharedFlow()

    private var ringTimeoutJob: Job? = null
    private var durationJob: Job? = null
    private var callGeneration = 0L

    fun openVoiceCall(participantName: String, threadId: String = "", peerUserId: String = "") {
        startCall(
            participantName = participantName,
            threadId = threadId,
            peerUserId = peerUserId,
            callType = CallType.Voice,
            direction = CallDirection.Outgoing,
        )
    }

    fun openVideoCall(participantName: String, threadId: String = "", peerUserId: String = "") {
        startCall(
            participantName = participantName,
            threadId = threadId,
            peerUserId = peerUserId,
            callType = CallType.Video,
            direction = CallDirection.Outgoing,
        )
    }

    fun startIncomingVoiceCall(
        participantName: String,
        threadId: String = "",
        callId: String? = null,
        peerUserId: String = "",
    ) {
        startCall(
            participantName = participantName,
            threadId = threadId,
            peerUserId = peerUserId,
            callType = CallType.Voice,
            direction = CallDirection.Incoming,
            callId = callId,
        )
    }

    fun startIncomingVideoCall(
        participantName: String,
        threadId: String = "",
        callId: String? = null,
        peerUserId: String = "",
    ) {
        startCall(
            participantName = participantName,
            threadId = threadId,
            peerUserId = peerUserId,
            callType = CallType.Video,
            direction = CallDirection.Incoming,
            callId = callId,
        )
    }

    fun answerVideoCall() {
        answerCall()
    }

    fun answerCall(syncBackend: Boolean = true) {
        val current = _uiState.value
        if (!current.isActive || current.status != CallStatus.Ringing) {
            return
        }

        cancelRingTimeout()
        _uiState.update {
            it.copy(
                status = CallStatus.InCall,
                isMinimized = false,
                durationSeconds = 0,
                lastEventLabel = "Connected",
                endReason = null,
            )
        }
        viewModelScope.launch {
            NovaWebRtcEngineRegistry.engine?.answerCurrentCall()
        }
        if (syncBackend) current.callId?.let { callId ->
            viewModelScope.launch {
                backendRuntime.answerCall(callId)
            }
        }
        startDurationTicker()
    }

    fun minimize(syncBackend: Boolean = true) {
        val current = _uiState.value
        _uiState.update { state ->
            if (!state.isActive) state else state.copy(isMinimized = true)
        }
        if (syncBackend) current.callId?.let { callId ->
            viewModelScope.launch {
                backendRuntime.minimizeCall(callId, true)
            }
        }
    }

    fun expand(syncBackend: Boolean = true) {
        val current = _uiState.value
        _uiState.update { state ->
            if (!state.isActive) state else state.copy(isMinimized = false)
        }
        if (syncBackend) current.callId?.let { callId ->
            viewModelScope.launch {
                backendRuntime.minimizeCall(callId, false)
            }
        }
    }

    fun toggleMic() {
        _uiState.update { current ->
            if (!current.isActive) current else current.copy(isMicOn = !current.isMicOn)
        }
        viewModelScope.launch {
            NovaWebRtcEngineRegistry.engine?.setMicEnabled(_uiState.value.isMicOn)
        }
    }

    fun toggleVideo() {
        _uiState.update { current ->
            if (!current.isActive || !current.isVideoCall) current else current.copy(isVideoOn = !current.isVideoOn)
        }
        viewModelScope.launch {
            NovaWebRtcEngineRegistry.engine?.setVideoEnabled(_uiState.value.isVideoOn)
        }
    }

    fun ensureVideoPreview() {
        val current = _uiState.value
        if (!current.isActive || !current.isVideoCall || !current.isVideoOn) {
            return
        }
        viewModelScope.launch {
            NovaWebRtcEngineRegistry.engine?.ensureLocalVideoPreview()
        }
    }

    fun switchCamera() {
        val current = _uiState.value
        if (!current.isActive || !current.isVideoCall || !current.isVideoOn) {
            return
        }
        viewModelScope.launch {
            NovaWebRtcEngineRegistry.engine?.switchCamera()
        }
    }

    fun hangUp(syncBackend: Boolean = true) {
        endCurrentCall(resolveDefaultEndReason(_uiState.value), syncBackend)
    }

    fun hangUp(reason: CallEndReason, syncBackend: Boolean = true) {
        endCurrentCall(reason, syncBackend)
    }

    fun clearSummary() {
        _lastSummary.value = null
    }

    suspend fun resetForLogout() {
        cancelDurationTicker()
        cancelRingTimeout()
        callGeneration += 1
        NovaWebRtcEngineRegistry.engine?.endCurrentCall(sendBye = false)
        _lastSummary.value = null
        _uiState.value = CallSessionUiState()
    }

    fun handleRealtimeSignal(event: BackendRealtimeEvent) {
        if (event.type != BackendRealtimeEventType.CALL_SIGNAL) {
            return
        }
        viewModelScope.launch {
            NovaWebRtcEngineRegistry.engine?.handleRealtimeEvent(event)
        }
    }

    private fun startCall(
        participantName: String,
        threadId: String,
        peerUserId: String,
        callType: CallType,
        direction: CallDirection,
        callId: String? = null,
    ) {
        val current = _uiState.value
        if (
            current.isActive &&
            current.participantName == participantName &&
            current.callType == callType &&
            current.direction == direction &&
            current.status != CallStatus.Ended
        ) {
            _uiState.update {
                it.copy(
                    isMinimized = false,
                    threadId = threadId.ifBlank { it.threadId },
                    peerUserId = peerUserId.ifBlank { it.peerUserId },
                    callId = callId ?: it.callId,
                )
            }
            return
        }

        cancelDurationTicker()
        cancelRingTimeout()
        callGeneration += 1
        val requestGeneration = callGeneration

        val now = System.currentTimeMillis()
        _lastSummary.value = null
        _uiState.value = CallSessionUiState(
            participantName = participantName,
            threadId = threadId,
            peerUserId = peerUserId,
            callId = callId,
            callType = callType,
            direction = direction,
            status = CallStatus.Ringing,
            isActive = true,
            isMinimized = false,
            isMicOn = true,
            isVideoOn = callType == CallType.Video,
            durationSeconds = 0,
            startedAtLabel = formatTimeLabel(now),
            lastEventLabel = if (direction == CallDirection.Incoming) "Incoming call" else "Calling",
            endReason = null,
        )
        startRingTimeout()

        if (direction == CallDirection.Incoming) {
            viewModelScope.launch {
                val session = _uiState.value
                if (session.callId.isNullOrBlank() || session.threadId.isBlank() || session.peerUserId.isBlank()) {
                    return@launch
                }
                NovaWebRtcEngineRegistry.engine?.beginIncomingCall(
                    callId = session.callId.orEmpty(),
                    threadId = session.threadId,
                    peerUserId = session.peerUserId,
                    callType = session.callType,
                )
            }
        }

        if (direction == CallDirection.Outgoing && threadId.isBlank() && peerUserId.isBlank()) {
            endCurrentCall(CallEndReason.Canceled, syncBackend = false)
            return
        }

        if (direction == CallDirection.Outgoing) {
            viewModelScope.launch {
                val started = backendRuntime.startCall(threadId, callType, direction, peerUserId)
                val backendCallId = started?.callId
                if (backendCallId.isNullOrBlank()) {
                    if (requestGeneration == callGeneration) {
                        endCurrentCall(CallEndReason.Canceled, syncBackend = false)
                    }
                    return@launch
                }
                if (requestGeneration != callGeneration) {
                    backendRuntime.endCall(backendCallId, CallEndReason.Canceled)
                    return@launch
                }
                val currentState = _uiState.value
                if (
                    !currentState.isActive ||
                    currentState.status == CallStatus.Ended ||
                    currentState.callType != callType ||
                    currentState.direction != direction ||
                    currentState.threadId != threadId
                ) {
                    backendRuntime.endCall(backendCallId, CallEndReason.Canceled)
                    return@launch
                }
                _uiState.update {
                    if (
                        it.isActive &&
                        it.status == CallStatus.Ringing &&
                        it.callType == callType &&
                        it.direction == direction &&
                        it.threadId == threadId
                    ) {
                        it.copy(
                            callId = backendCallId,
                            threadId = started.threadId.ifBlank { threadId },
                            peerUserId = it.peerUserId.ifBlank { started.summary?.peerUserId.orEmpty() },
                            participantName = started.summary?.participantName?.takeIf { name -> name.isNotBlank() } ?: it.participantName,
                        )
                    } else {
                        it
                    }
                }
                val currentUi = _uiState.value
                val remoteUserId = currentUi.peerUserId.ifBlank { peerUserId }
                if (remoteUserId.isNotBlank()) {
                    NovaWebRtcEngineRegistry.engine?.beginOutgoingCall(
                        callId = backendCallId,
                        threadId = currentUi.threadId.ifBlank { threadId },
                        peerUserId = remoteUserId,
                        callType = callType,
                    )
                }
            }
        }
    }

    private fun endCurrentCall(reason: CallEndReason, syncBackend: Boolean) {
        val current = _uiState.value
        if (!current.isActive && current.status != CallStatus.Ended) {
            return
        }

        cancelDurationTicker()
        cancelRingTimeout()
        callGeneration += 1

        val endedAtMillis = System.currentTimeMillis()
        val summary = CallSummaryUiState(
            participantName = current.participantName,
            threadId = current.threadId,
            peerUserId = current.peerUserId,
            callId = current.callId,
            callType = current.callType,
            direction = current.direction,
            durationSeconds = current.durationSeconds,
            endReason = reason,
            startedAtLabel = current.startedAtLabel,
            endedAtLabel = formatTimeLabel(endedAtMillis),
            isMicOn = current.isMicOn,
            isVideoOn = current.isVideoOn,
        )
        _lastSummary.value = summary

        _uiState.value = current.copy(
            status = CallStatus.Ended,
            isActive = false,
            isMinimized = false,
            lastEventLabel = reason.displayLabel(),
            endReason = reason,
        )

        if (syncBackend) current.callId?.let { callId ->
            viewModelScope.launch {
                backendRuntime.endCall(callId, reason)
            }
        }

        viewModelScope.launch {
            NovaWebRtcEngineRegistry.engine?.endCurrentCall(sendBye = syncBackend)
        }

        _endEvents.tryEmit(
            CallEndEvent(
                summary = summary,
                replaceCurrentCallRoute = !current.isMinimized,
            )
        )

        _uiState.value = CallSessionUiState()
    }

    private fun startRingTimeout() {
        ringTimeoutJob?.cancel()
        ringTimeoutJob = viewModelScope.launch {
            delay(RING_TIMEOUT_MS)
            val current = _uiState.value
            if (!current.isActive || current.status != CallStatus.Ringing) {
                return@launch
            }

            val reason = when (current.direction) {
                CallDirection.Incoming -> CallEndReason.Missed
                CallDirection.Outgoing -> CallEndReason.NoAnswer
            }
            endCurrentCall(reason, syncBackend = true)
        }
    }

    private fun startDurationTicker() {
        cancelDurationTicker()
        durationJob = viewModelScope.launch {
            while (true) {
                delay(1_000)
                _uiState.update { current ->
                    if (current.status != CallStatus.InCall) {
                        current
                    } else {
                        current.copy(durationSeconds = current.durationSeconds + 1)
                    }
                }
            }
        }
    }

    private fun cancelRingTimeout() {
        ringTimeoutJob?.cancel()
        ringTimeoutJob = null
    }

    private fun cancelDurationTicker() {
        durationJob?.cancel()
        durationJob = null
    }

    private fun resolveDefaultEndReason(state: CallSessionUiState): CallEndReason {
        return when {
            !state.isActive && state.endReason != null -> state.endReason
            state.status == CallStatus.Ringing && state.direction == CallDirection.Incoming -> CallEndReason.Declined
            state.status == CallStatus.Ringing && state.direction == CallDirection.Outgoing -> CallEndReason.Canceled
            state.status == CallStatus.InCall -> CallEndReason.HungUp
            state.status == CallStatus.Ended && state.endReason != null -> state.endReason
            else -> CallEndReason.HungUp
        }
    }

    private fun formatTimeLabel(epochMillis: Long): String {
        val formatter = SimpleDateFormat(TIME_LABEL_PATTERN, Locale.getDefault())
        return formatter.format(Date(epochMillis))
    }

    override fun onCleared() {
        cancelRingTimeout()
        cancelDurationTicker()
        super.onCleared()
    }
}
