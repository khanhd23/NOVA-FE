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
import com.nova.app.core.webrtc.NovaWebRtcEngine
import com.nova.app.core.webrtc.NovaWebRtcEngineRegistry
import com.nova.app.core.webrtc.WebRtcCallState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
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
/** After the call is accepted, media must connect within this window or the call is dropped. */
private const val CONNECT_TIMEOUT_MS = 20_000L
/** How long media may stay disconnected before we give up (WebRTC restarts ICE meanwhile). */
private const val RECONNECT_TIMEOUT_MS = 25_000L
const val CALL_EVENT_CONNECTING = "Connecting"
private const val ICE_CONFIG_TIMEOUT_MS = 3_000L
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
    private var connectTimeoutJob: Job? = null
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

    /**
     * Entry point for an incoming call (realtime event or push). If another call is already
     * in progress the new one is rejected as busy, like a phone line. Returns true if accepted
     * for ringing.
     */
    fun receiveIncomingCall(
        participantName: String,
        threadId: String,
        callId: String?,
        peerUserId: String,
        callType: CallType,
    ): Boolean {
        val current = _uiState.value
        if (current.isActive && !callId.isNullOrBlank() && current.callId != callId) {
            viewModelScope.launch {
                runCatching { backendRuntime.endCall(callId, CallEndReason.Busy) }
            }
            return false
        }
        when (callType) {
            CallType.Voice -> startIncomingVoiceCall(participantName, threadId, callId, peerUserId)
            CallType.Video -> startIncomingVideoCall(participantName, threadId, callId, peerUserId)
        }
        return true
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
        val engine = NovaWebRtcEngineRegistry.engine
        _uiState.update {
            it.copy(
                status = CallStatus.InCall,
                isMinimized = false,
                durationSeconds = 0,
                lastEventLabel = CALL_EVENT_CONNECTING,
                endReason = null,
                isMediaConnected = false,
                isReconnecting = false,
            )
        }
        viewModelScope.launch {
            engine?.answerCurrentCall()
        }
        if (syncBackend) current.callId?.let { callId ->
            viewModelScope.launch {
                backendRuntime.answerCall(callId)
            }
        }
        val media = engine?.state?.value
        when {
            // No media engine (e.g. preview/demo): treat the call as connected right away.
            engine == null -> markMediaConnected()
            media?.callId == current.callId && media?.connectionState == NovaWebRtcEngine.CONNECTION_CONNECTED -> markMediaConnected()
            else -> startConnectTimeout(CONNECT_TIMEOUT_MS)
        }
    }

    /** Called with every media state update from the WebRTC engine. */
    fun onMediaStateChanged(media: WebRtcCallState) {
        val current = _uiState.value
        if (!current.isActive || current.status != CallStatus.InCall) return
        if (media.callId.isNullOrBlank() || media.callId != current.callId) return
        when (media.connectionState) {
            NovaWebRtcEngine.CONNECTION_CONNECTED -> markMediaConnected()
            NovaWebRtcEngine.CONNECTION_RECONNECTING -> {
                if (current.isMediaConnected && !current.isReconnecting) {
                    _uiState.update { it.copy(isReconnecting = true) }
                    startConnectTimeout(RECONNECT_TIMEOUT_MS)
                }
            }
            NovaWebRtcEngine.CONNECTION_FAILED -> endCurrentCall(CallEndReason.Dropped, syncBackend = true)
        }
    }

    /** The peer sent a WebRTC "bye"; end locally even if the backend event is late or lost. */
    fun onRemoteHangup(callId: String) {
        val current = _uiState.value
        if (current.isActive && current.callId == callId) {
            val reason = when {
                // Caller gave up before we answered.
                current.isRinging && current.direction == CallDirection.Incoming -> CallEndReason.Missed
                // Callee rejected our call.
                current.isRinging && current.direction == CallDirection.Outgoing -> CallEndReason.Declined
                else -> CallEndReason.HungUp
            }
            endCurrentCall(reason, syncBackend = false)
        }
    }

    private fun markMediaConnected() {
        cancelConnectTimeout()
        val wasConnected = _uiState.value.isMediaConnected
        _uiState.update {
            it.copy(isMediaConnected = true, isReconnecting = false, lastEventLabel = "Connected")
        }
        if (!wasConnected) {
            startDurationTicker()
        }
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
        if (current.direction == CallDirection.Outgoing && current.callId.isNullOrBlank()) {
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
        cancelConnectTimeout()
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
        if (current.isActive && !callId.isNullOrBlank() && current.callId == callId) {
            _uiState.update { it.copy(isMinimized = false) }
            return
        }
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
                refreshIceServers()
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
                    refreshIceServers()
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
        cancelConnectTimeout()
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
            isMicOn = false,
            isVideoOn = false,
        )
        _lastSummary.value = summary

        _uiState.value = current.copy(
            status = CallStatus.Ended,
            isActive = false,
            isMinimized = false,
            isMicOn = false,
            isVideoOn = false,
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
        // The callee waits a bit longer than the caller so the caller's NO_ANSWER normally ends
        // the call; the backend then pushes it to the callee as a missed call.
        val timeout = if (_uiState.value.direction == CallDirection.Incoming) RING_TIMEOUT_MS + 5_000L else RING_TIMEOUT_MS
        ringTimeoutJob = viewModelScope.launch {
            delay(timeout)
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

    /**
     * TURN credentials from the backend are time-limited, so fetch fresh ICE servers right
     * before each call. On failure the engine keeps the last known configuration.
     */
    private suspend fun refreshIceServers() {
        val config = withTimeoutOrNull(ICE_CONFIG_TIMEOUT_MS) {
            runCatching { backendRuntime.fetchRealtimeConfig() }.getOrNull()
        } ?: return
        NovaWebRtcEngineRegistry.engine?.applyRealtimeConfig(config)
    }

    private fun startConnectTimeout(timeoutMs: Long) {
        connectTimeoutJob?.cancel()
        val callId = _uiState.value.callId
        connectTimeoutJob = viewModelScope.launch {
            delay(timeoutMs)
            val current = _uiState.value
            if (current.isActive && current.callId == callId &&
                (!current.isMediaConnected || current.isReconnecting)
            ) {
                endCurrentCall(CallEndReason.Dropped, syncBackend = true)
            }
        }
    }

    private fun cancelConnectTimeout() {
        connectTimeoutJob?.cancel()
        connectTimeoutJob = null
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
        cancelConnectTimeout()
        cancelDurationTicker()
        // The UI was destroyed mid-call (e.g. app swiped away): end it for real so the peer and the
        // backend don't keep a ghost call around.
        val current = _uiState.value
        if (current.isActive) {
            current.callId?.let { callId ->
                val reason = resolveDefaultEndReason(current)
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    runCatching { backendRuntime.endCall(callId, reason) }
                }
            }
        }
        NovaWebRtcEngineRegistry.engine?.endCurrentCallInBackground(sendBye = current.isActive)
        super.onCleared()
    }
}
