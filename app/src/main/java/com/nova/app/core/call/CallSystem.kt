package com.nova.app.core.call

import android.app.NotificationManager
import android.content.Context
import com.nova.app.core.backend.callNotificationId
import com.nova.app.core.model.CallDirection
import com.nova.app.core.model.CallSessionUiState
import com.nova.app.core.model.CallStatus
import com.nova.app.core.model.CallType
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** Whether an activity of the app is currently visible (set by MainActivity). */
object AppVisibility {
    @Volatile
    var isForeground: Boolean = false
}

data class IncomingCallRequest(
    val callId: String,
    val threadId: String,
    val peerUserId: String,
    val participantName: String,
    val callType: CallType,
)

/**
 * Process-wide bus between Android components (push service, broadcast receiver,
 * foreground service) and the in-app call view model.
 */
object CallActions {
    private val _hangUpRequests = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val hangUpRequests: SharedFlow<String> = _hangUpRequests.asSharedFlow()

    private val _incomingCalls = MutableSharedFlow<IncomingCallRequest>(extraBufferCapacity = 4)
    val incomingCalls: SharedFlow<IncomingCallRequest> = _incomingCalls.asSharedFlow()

    /** True while the app UI is alive and listening (i.e. the call view model can act). */
    val isAppListening: Boolean
        get() = _hangUpRequests.subscriptionCount.value > 0

    fun requestHangUp(callId: String): Boolean = isAppListening && _hangUpRequests.tryEmit(callId)

    fun deliverIncomingCall(request: IncomingCallRequest): Boolean =
        _incomingCalls.subscriptionCount.value > 0 && _incomingCalls.tryEmit(request)

    private val _expandRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Ask the UI to bring the active call back to its full screen (e.g. before picture-in-picture). */
    val expandRequests: SharedFlow<Unit> = _expandRequests.asSharedFlow()

    fun requestExpand() {
        _expandRequests.tryEmit(Unit)
    }
}

object CallSystemRegistry {
    @Volatile
    var system: CallSystem? = null
}

/**
 * Reacts to call state changes with the side effects a real phone call has:
 * ringtone / ringback, audio mode and routing, proximity sensor, foreground service
 * with an ongoing-call notification, and cleanup of call notifications.
 */
class CallSystem(context: Context) {
    private val appContext = context.applicationContext
    val audio = CallAudioController(appContext)

    private val _pictureInPicture = MutableStateFlow(false)
    val pictureInPicture: StateFlow<Boolean> = _pictureInPicture.asStateFlow()

    @Volatile
    var currentCall: CallSessionUiState = CallSessionUiState()
        private set

    private var lastKey: CallKey? = null

    val hasActiveCall: Boolean
        get() = currentCall.isActive

    /** A connected video call: leaving the app should shrink it to picture-in-picture. */
    val isVideoCallInProgress: Boolean
        get() = currentCall.isActive && currentCall.isVideoCall && currentCall.status == CallStatus.InCall

    fun setPictureInPicture(enabled: Boolean) {
        _pictureInPicture.value = enabled
    }

    fun onCallState(state: CallSessionUiState) {
        val previous = currentCall
        currentCall = state
        val key = CallKey.of(state)
        if (key == lastKey) return // e.g. only the duration ticked
        lastKey = key

        if (!state.isActive) {
            if (previous.isActive) {
                audio.release()
                CallForegroundService.stop(appContext)
                cancelCallNotification(previous.callId)
            }
            return
        }

        when (state.status) {
            CallStatus.Ringing -> when (state.direction) {
                CallDirection.Incoming -> {
                    if (AppVisibility.isForeground) audio.startIncomingRing()
                }
                CallDirection.Outgoing -> {
                    audio.enterCallAudio(state.isVideoCall)
                    audio.startRingback()
                    CallForegroundService.update(appContext, state)
                }
            }
            CallStatus.InCall -> {
                audio.stopRinging()
                audio.enterCallAudio(state.isVideoCall)
                audio.setProximityEnabled(!state.isVideoCall)
                cancelCallNotification(state.callId)
                CallForegroundService.update(appContext, state)
            }
            else -> Unit
        }
    }

    /** Stop the in-app ringtone, e.g. when the user starts interacting with the incoming screen. */
    fun silenceRinging() = audio.stopRinging()

    private fun cancelCallNotification(callId: String?) {
        if (callId.isNullOrBlank()) return
        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(callNotificationId(callId))
    }

    private data class CallKey(
        val callId: String?,
        val active: Boolean,
        val status: CallStatus,
        val direction: CallDirection,
        val video: Boolean,
        val mediaConnected: Boolean,
        val reconnecting: Boolean,
        val minimized: Boolean,
        val name: String,
    ) {
        companion object {
            fun of(state: CallSessionUiState) = CallKey(
                callId = state.callId,
                active = state.isActive,
                status = state.status,
                direction = state.direction,
                video = state.isVideoCall,
                mediaConnected = state.isMediaConnected,
                reconnecting = state.isReconnecting,
                minimized = state.isMinimized,
                name = state.participantName,
            )
        }
    }
}
