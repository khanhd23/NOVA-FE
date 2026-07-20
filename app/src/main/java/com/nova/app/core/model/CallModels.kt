package com.nova.app.core.model

import androidx.compose.runtime.Immutable

enum class CallType {
    Voice,
    Video,
}

enum class CallDirection {
    Incoming,
    Outgoing,
}

enum class CallStatus {
    Idle,
    Ringing,
    InCall,
    Ended,
}

enum class CallEndReason {
    HungUp,
    Canceled,
    Declined,
    Rejected,
    Missed,
    NoAnswer,
    Busy,
    Dropped,
    Completed,
}

@Immutable
data class CallSessionUiState(
    val participantName: String = "",
    val threadId: String = "",
    val peerUserId: String = "",
    val callId: String? = null,
    val callType: CallType = CallType.Voice,
    val direction: CallDirection = CallDirection.Outgoing,
    val status: CallStatus = CallStatus.Idle,
    val isActive: Boolean = false,
    val isMinimized: Boolean = false,
    val isMicOn: Boolean = true,
    val isVideoOn: Boolean = true,
    val durationSeconds: Int = 0,
    val startedAtLabel: String = "",
    val lastEventLabel: String = "",
    val endReason: CallEndReason? = null,
) {
    val isVideoCall: Boolean
        get() = callType == CallType.Video

    val isAnswered: Boolean
        get() = status == CallStatus.InCall

    val isRinging: Boolean
        get() = status == CallStatus.Ringing

    val isEnded: Boolean
        get() = status == CallStatus.Ended
}

@Immutable
data class CallSummaryUiState(
    val participantName: String = "",
    val threadId: String = "",
    val peerUserId: String = "",
    val callId: String? = null,
    val callType: CallType = CallType.Voice,
    val direction: CallDirection = CallDirection.Outgoing,
    val durationSeconds: Int = 0,
    val endReason: CallEndReason = CallEndReason.HungUp,
    val startedAtLabel: String = "",
    val endedAtLabel: String = "",
    val isMicOn: Boolean = true,
    val isVideoOn: Boolean = true,
)

@Immutable
data class CallEndEvent(
    val summary: CallSummaryUiState,
    val replaceCurrentCallRoute: Boolean,
)

fun CallEndReason.displayLabel(): String = when (this) {
    CallEndReason.HungUp -> "Ended"
    CallEndReason.Canceled -> "Canceled"
    CallEndReason.Declined -> "Declined"
    CallEndReason.Rejected -> "Rejected"
    CallEndReason.Missed -> "Missed"
    CallEndReason.NoAnswer -> "No answer"
    CallEndReason.Busy -> "Busy"
    CallEndReason.Dropped -> "Dropped"
    CallEndReason.Completed -> "Completed"
}

fun CallDirection.displayLabel(): String = when (this) {
    CallDirection.Incoming -> "Incoming"
    CallDirection.Outgoing -> "Outgoing"
}

fun CallStatus.displayLabel(): String = when (this) {
    CallStatus.Idle -> "Idle"
    CallStatus.Ringing -> "Ringing"
    CallStatus.InCall -> "In call"
    CallStatus.Ended -> "Ended"
}
