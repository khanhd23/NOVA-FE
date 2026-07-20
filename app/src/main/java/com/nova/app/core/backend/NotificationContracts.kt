package com.nova.app.core.backend

import android.content.Intent
import com.nova.app.core.model.CallType

const val ACTION_OPEN_CHAT = "com.nova.app.action.OPEN_CHAT"
const val ACTION_OPEN_CALL = "com.nova.app.action.OPEN_CALL"
const val ACTION_OPEN_PROFILE = "com.nova.app.action.OPEN_PROFILE"
const val ACTION_OPEN_NOTIFICATION_TARGET = "com.nova.app.action.OPEN_NOTIFICATION_TARGET"
const val ACTION_ANSWER_CALL = "com.nova.app.action.ANSWER_CALL"
const val ACTION_DECLINE_CALL = "com.nova.app.action.DECLINE_CALL"

const val EXTRA_THREAD_ID = "extra_thread_id"
const val EXTRA_CALL_ID = "extra_call_id"
const val EXTRA_PEER_USER_ID = "extra_peer_user_id"
const val EXTRA_PARTICIPANT_NAME = "extra_participant_name"
const val EXTRA_CALL_TYPE = "extra_call_type"
const val EXTRA_DIRECTION = "extra_direction"
const val EXTRA_AUTO_ANSWER = "extra_auto_answer"
const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
const val EXTRA_MESSAGE_PREVIEW = "extra_message_preview"
const val EXTRA_PROFILE_USER_ID = "extra_profile_user_id"
const val EXTRA_NOTIFICATION_TARGET = "extra_notification_target"
const val EXTRA_NOTIFICATION_KIND = "extra_notification_kind"

private const val CALL_NOTIFICATION_BASE_ID = 41_000
private const val CHAT_NOTIFICATION_BASE_ID = 51_000

data class CallNotificationPayload(
    val callId: String,
    val threadId: String,
    val peerUserId: String,
    val participantName: String,
    val callType: CallType,
    val direction: String,
    val title: String = "",
    val body: String = "",
    val autoAnswer: Boolean,
    val notificationId: Int,
)

data class ChatNotificationPayload(
    val threadId: String,
    val peerUserId: String,
    val participantName: String,
    val messagePreview: String,
    val notificationId: Int,
)

fun callNotificationId(callId: String): Int {
    return CALL_NOTIFICATION_BASE_ID + (callId.hashCode() and Int.MAX_VALUE) % 20_000
}

fun chatNotificationId(threadId: String): Int {
    return CHAT_NOTIFICATION_BASE_ID + (threadId.hashCode() and Int.MAX_VALUE) % 20_000
}

fun Intent.toCallNotificationPayload(): CallNotificationPayload? {
    val callId = getStringExtra(EXTRA_CALL_ID).orEmpty()
    val threadId = getStringExtra(EXTRA_THREAD_ID).orEmpty()
    val peerUserId = getStringExtra(EXTRA_PEER_USER_ID).orEmpty()
    val participantName = getStringExtra(EXTRA_PARTICIPANT_NAME).orEmpty()
    val callType = when (getStringExtra(EXTRA_CALL_TYPE)?.uppercase()) {
        "VIDEO" -> CallType.Video
        "VOICE" -> CallType.Voice
        else -> null
    } ?: return null
    val direction = getStringExtra(EXTRA_DIRECTION).orEmpty()
    if (callId.isBlank() || threadId.isBlank()) {
        return null
    }
    return CallNotificationPayload(
        callId = callId,
        threadId = threadId,
        peerUserId = peerUserId,
        participantName = participantName,
        callType = callType,
        direction = direction,
        title = "",
        body = "",
        autoAnswer = getBooleanExtra(EXTRA_AUTO_ANSWER, false),
        notificationId = getIntExtra(EXTRA_NOTIFICATION_ID, callNotificationId(callId)),
    )
}

fun Intent.toChatNotificationPayload(): ChatNotificationPayload? {
    val threadId = getStringExtra(EXTRA_THREAD_ID).orEmpty()
    if (threadId.isBlank()) {
        return null
    }
    return ChatNotificationPayload(
        threadId = threadId,
        peerUserId = getStringExtra(EXTRA_PEER_USER_ID).orEmpty(),
        participantName = getStringExtra(EXTRA_PARTICIPANT_NAME).orEmpty(),
        messagePreview = getStringExtra(EXTRA_MESSAGE_PREVIEW).orEmpty(),
        notificationId = getIntExtra(EXTRA_NOTIFICATION_ID, chatNotificationId(threadId)),
    )
}
