package com.nova.app.core.backend

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.nova.app.MainActivity
import com.nova.app.R
import com.nova.app.core.call.AppVisibility
import com.nova.app.core.call.CallActions
import com.nova.app.core.call.CallSystemRegistry
import com.nova.app.core.call.IncomingCallRequest
import com.nova.app.core.i18n.withAppLanguage
import java.util.Locale

private const val MESSAGE_CHANNEL_ID = "nova_messages"
private const val CALL_CHANNEL_ID = "nova_calls"
private const val INCOMING_CALL_CHANNEL_ID = "nova_calls_incoming"
private const val INCOMING_CALL_TIMEOUT_MS = 45_000L
private const val MESSAGE_CHANNEL_NAME = "NOVA messages"
private const val CHANNEL_DESCRIPTION = "Chat and call updates"
private const val SOCIAL_CHANNEL_NAME = "NOVA social"

class NovaFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        BackendSessionStore.savePushToken(applicationContext, token)
        BackendRuntimeRegistry.runtime?.onPushTokenRefreshed(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val data = message.data
        val type = data["type"]?.uppercase(Locale.ROOT)
        when (type) {
            "CALL_STARTED" -> showCallNotification(data)
            // Answered (possibly on another device) or minimized: stop ringing; the ongoing-call
            // notification is owned by CallForegroundService while the call is alive.
            "CALL_ANSWERED", "CALL_MINIMIZED" -> cancelCallNotification(data)
            "CALL_ENDED" -> showCallEndedNotification(data)
            "MESSAGE_CREATED" -> showMessageNotification(data)
            // Every realtime event is also pushed; only new messages deserve a notification.
            // Read, deleted or recalled: clear the stale message notification. Typing: ignore.
            "MESSAGE_RECALLED", "MESSAGE_DELETED", "THREAD_DELETED", "THREAD_READ" -> cancelMessageNotification(data)
            "THREAD_TYPING" -> Unit
            "NOTIFICATION_CREATED" -> showNotificationCreated(data)
            else -> showGenericNotification(data)
        }
    }

    private fun showNotificationCreated(data: Map<String, String>) {
        when (data["kind"]?.uppercase(Locale.ROOT)) {
            "CALL" -> Unit
            "FOLLOW", "FRIEND" -> showProfileNotification(data)
            else -> showNotificationTargetNotification(data)
        }
    }

    private fun showProfileNotification(data: Map<String, String>) {
        val profileUserId = resolveProfileUserId(data)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureMessageChannel(manager)

        val openProfileIntent = createProfileActivityIntent(profileUserId)
        val title = data["title"] ?: "Profile update"
        val body = data["body"] ?: "Open profile"
        val notification = NotificationCompat.Builder(this, MESSAGE_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setColor(Color.parseColor("#8B5CF6"))
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setContentIntent(createActivityPendingIntent(openProfileIntent, socialNotificationId(data), 0))
            .build()

        manager.notify(socialNotificationId(data), notification)
    }

    private fun showNotificationTargetNotification(data: Map<String, String>) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureMessageChannel(manager)

        val openTargetIntent = createNotificationTargetIntent(data)
        val title = data["title"] ?: getString(R.string.app_name)
        val body = data["body"] ?: "Open NOVA for details"
        val notification = NotificationCompat.Builder(this, MESSAGE_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setColor(Color.parseColor("#8B5CF6"))
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setContentIntent(createActivityPendingIntent(openTargetIntent, socialNotificationId(data), 0))
            .build()

        manager.notify(socialNotificationId(data), notification)
    }

    private fun showCallNotification(data: Map<String, String>) {
        val callPayload = buildCallPayload(data)
        // The caller's own device shows the ongoing-call notification from the foreground service.
        if (!callPayload.direction.equals("INCOMING", ignoreCase = true)) return

        // App open (or already in a call): hand the call to the app. It rings in-app, or rejects
        // it as busy, instead of showing a second notification.
        val appShouldHandle = AppVisibility.isForeground || CallSystemRegistry.system?.hasActiveCall == true
        if (appShouldHandle && CallActions.deliverIncomingCall(
                IncomingCallRequest(
                    callId = callPayload.callId,
                    threadId = callPayload.threadId,
                    peerUserId = callPayload.peerUserId,
                    participantName = callPayload.participantName,
                    callType = callPayload.callType,
                )
            )
        ) {
            return
        }

        val localized = withAppLanguage()
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureIncomingCallChannel(manager, localized)

        val openCallIntent = createCallActivityIntent(ACTION_OPEN_CALL, callPayload, autoAnswer = false)
        val answerIntent = createCallActivityIntent(ACTION_ANSWER_CALL, callPayload, autoAnswer = true)
        val declineIntent = createDeclineIntent(callPayload)
        val caller = Person.Builder()
            .setName(callPayload.participantName.ifBlank { localized.getString(R.string.call_default_name) })
            .setImportant(true)
            .build()
        val text = localized.getString(
            if (callPayload.callType == com.nova.app.core.model.CallType.Video) R.string.call_incoming_video else R.string.call_incoming_voice
        )

        val notification = NotificationCompat.Builder(this, INCOMING_CALL_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(caller.name)
            .setContentText(text)
            .setStyle(
                NotificationCompat.CallStyle.forIncomingCall(
                    caller,
                    createBroadcastPendingIntent(declineIntent, callPayload.notificationId, 3),
                    createActivityPendingIntent(answerIntent, callPayload.notificationId, 2),
                ).setIsVideo(callPayload.callType == com.nova.app.core.model.CallType.Video)
            )
            .setColor(Color.parseColor("#8B5CF6"))
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setTimeoutAfter(INCOMING_CALL_TIMEOUT_MS)
            .setContentIntent(createActivityPendingIntent(openCallIntent, callPayload.notificationId, 0))
            .setFullScreenIntent(createActivityPendingIntent(openCallIntent, callPayload.notificationId, 1), true)
            .apply {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                    setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))
                    setVibrate(longArrayOf(0, 800, 1200, 800, 1200))
                }
            }
            .build()
            .apply { flags = flags or android.app.Notification.FLAG_INSISTENT }

        manager.notify(callPayload.notificationId, notification)
    }

    private fun cancelCallNotification(data: Map<String, String>) {
        val callPayload = buildCallPayload(data)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(callPayload.notificationId)
    }

    private fun showCallEndedNotification(data: Map<String, String>) {
        val callPayload = buildCallPayload(data)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Always stop the ringing notification first.
        manager.cancel(callPayload.notificationId)

        val endReason = data["endReason"]?.uppercase(Locale.ROOT)
        val duration = data["durationSeconds"]?.toIntOrNull() ?: 0
        val missed = callPayload.direction.equals("INCOMING", ignoreCase = true) &&
            duration == 0 &&
            endReason in setOf("MISSED", "NO_ANSWER", "CANCELED", "BUSY")
        if (!missed) return

        val localized = withAppLanguage()
        ensureCallChannel(manager, localized)
        val name = callPayload.participantName.ifBlank { localized.getString(R.string.call_default_name) }
        val openChatIntent = createChatActivityIntent(callPayload.threadId, callPayload.peerUserId, name, "")
        val callBackIntent = createCallActivityIntent(ACTION_CALL_BACK, callPayload, autoAnswer = false)
        val notification = NotificationCompat.Builder(this, CALL_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(localized.getString(
                if (callPayload.callType == com.nova.app.core.model.CallType.Video) R.string.call_missed_video else R.string.call_missed
            ))
            .setContentText(name)
            .setColor(Color.parseColor("#8B5CF6"))
            .setCategory(NotificationCompat.CATEGORY_MISSED_CALL)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setContentIntent(createActivityPendingIntent(openChatIntent, callPayload.notificationId, 0))
            .addAction(0, localized.getString(R.string.call_again), createActivityPendingIntent(callBackIntent, callPayload.notificationId, 4))
            .addAction(0, localized.getString(R.string.call_message), createActivityPendingIntent(openChatIntent, callPayload.notificationId, 5))
            .build()

        manager.notify(callPayload.notificationId, notification)
    }

    private fun cancelMessageNotification(data: Map<String, String>) {
        val threadId = data["threadId"].orEmpty()
        if (threadId.isBlank()) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(chatNotificationId(threadId))
    }

    private fun showMessageNotification(data: Map<String, String>) {
        val chatPayload = buildChatPayload(data)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureMessageChannel(manager)

        val openChatIntent = createChatActivityIntent(
            threadId = chatPayload.threadId,
            peerUserId = chatPayload.peerUserId,
            participantName = chatPayload.participantName,
            messagePreview = chatPayload.messagePreview,
        )
        val notification = NotificationCompat.Builder(this, MESSAGE_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(chatPayload.participantName.ifBlank { "NOVA" })
            .setContentText(chatPayload.messagePreview.ifBlank { "Open NOVA for details" })
            .setStyle(NotificationCompat.BigTextStyle().bigText(chatPayload.messagePreview.ifBlank { "Open NOVA for details" }))
            .setColor(Color.parseColor("#8B5CF6"))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setContentIntent(createActivityPendingIntent(openChatIntent, chatPayload.notificationId, 0))
            .build()

        manager.notify(chatPayload.notificationId, notification)
    }

    private fun showGenericNotification(data: Map<String, String>) {
        val title = data["title"] ?: getString(R.string.app_name)
        val body = data["body"] ?: "Open NOVA for details"
        val threadId = data["threadId"].orEmpty()
        val participantName = data["senderName"].orEmpty().ifBlank { data["partnerName"].orEmpty() }
        val openIntent = if (threadId.isNotBlank()) {
            createChatActivityIntent(
                threadId = threadId,
                peerUserId = data["actorUserId"].orEmpty(),
                participantName = participantName,
                messagePreview = body,
            )
        } else {
            Intent(this, MainActivity::class.java).apply {
                action = ACTION_OPEN_CHAT
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureMessageChannel(manager)
        val notification = NotificationCompat.Builder(this, MESSAGE_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setColor(Color.parseColor("#8B5CF6"))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(createActivityPendingIntent(openIntent, chatNotificationId(threadId.ifBlank { title }), 0))
            .build()

        manager.notify(chatNotificationId(threadId.ifBlank { title }), notification)
    }

    private fun createProfileActivityIntent(profileUserId: String): Intent {
        return Intent(this, MainActivity::class.java).apply {
            action = ACTION_OPEN_PROFILE
            putExtra(EXTRA_PROFILE_USER_ID, profileUserId)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun createNotificationTargetIntent(data: Map<String, String>): Intent {
        return Intent(this, MainActivity::class.java).apply {
            action = ACTION_OPEN_NOTIFICATION_TARGET
            putExtra(EXTRA_NOTIFICATION_TARGET, data["actionTarget"].orEmpty())
            putExtra(EXTRA_THREAD_ID, data["threadId"].orEmpty())
            putExtra(EXTRA_PEER_USER_ID, data["actorUserId"].orEmpty())
            putExtra(EXTRA_PARTICIPANT_NAME, data["actorName"].orEmpty().ifBlank { data["senderName"].orEmpty() })
            putExtra(EXTRA_MESSAGE_PREVIEW, data["body"].orEmpty())
            putExtra(EXTRA_NOTIFICATION_KIND, data["kind"].orEmpty())
            putExtra(EXTRA_PROFILE_USER_ID, resolveProfileUserId(data))
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun resolveProfileUserId(data: Map<String, String>): String {
        val actionTarget = data["actionTarget"].orEmpty()
        if (actionTarget.startsWith("profile/", ignoreCase = true)) {
            return actionTarget.removePrefix("profile/").trim()
        }
        return data["actorUserId"].orEmpty().ifBlank { data["targetUserId"].orEmpty() }
    }

    private fun socialNotificationId(data: Map<String, String>): Int {
        val source = buildString {
            append(data["notificationId"].orEmpty())
            append('|')
            append(data["actionTarget"].orEmpty())
            append('|')
            append(data["kind"].orEmpty())
        }
        return chatNotificationId(source.ifBlank { data["title"].orEmpty().ifBlank { "social" } })
    }

    private fun buildCallPayload(data: Map<String, String>): CallNotificationPayload {
        val callId = data["callId"].orEmpty()
        val threadId = data["threadId"].orEmpty()
        val peerUserId = data["peerUserId"].orEmpty()
            .ifBlank { data["callerId"].orEmpty() }
            .ifBlank { data["partnerId"].orEmpty() }
        val participantName = data["peerName"].orEmpty()
            .ifBlank { data["callerName"].orEmpty() }
            .ifBlank { data["partnerName"].orEmpty() }
            .ifBlank { data["title"].orEmpty() }
        val callType = when (data["callType"]?.uppercase(Locale.ROOT)) {
            "VIDEO" -> com.nova.app.core.model.CallType.Video
            else -> com.nova.app.core.model.CallType.Voice
        }
        val direction = data["direction"].orEmpty()
        val title = data["title"].orEmpty().ifBlank {
            when {
                direction.equals("INCOMING", ignoreCase = true) && callType == com.nova.app.core.model.CallType.Video -> "Incoming video call"
                direction.equals("INCOMING", ignoreCase = true) -> "Incoming voice call"
                callType == com.nova.app.core.model.CallType.Video -> "Video call"
                else -> "Voice call"
            }
        }
        val body = data["body"].orEmpty().ifBlank {
            when {
                direction.equals("INCOMING", ignoreCase = true) -> "$participantName is calling you"
                data["summaryText"].orEmpty().isNotBlank() -> data["summaryText"].orEmpty()
                else -> "Tap to return to the call"
            }
        }
        return CallNotificationPayload(
            callId = callId,
            threadId = threadId,
            peerUserId = peerUserId,
            participantName = participantName,
            callType = callType,
            direction = direction,
            title = title,
            body = body,
            autoAnswer = false,
            notificationId = callNotificationId(callId.ifBlank { threadId }),
        )
    }

    private fun buildChatPayload(data: Map<String, String>): ChatNotificationPayload {
        val threadId = data["threadId"].orEmpty()
        val participantName = data["senderName"].orEmpty().ifBlank { data["partnerName"].orEmpty().ifBlank { data["title"].orEmpty() } }
        val peerUserId = data["actorUserId"].orEmpty()
        val messagePreview = data["summaryText"].orEmpty().ifBlank {
            data["body"].orEmpty().ifBlank {
                data["text"].orEmpty().ifBlank { "Open NOVA for details" }
            }
        }
        return ChatNotificationPayload(
            threadId = threadId,
            peerUserId = peerUserId,
            participantName = participantName,
            messagePreview = messagePreview,
            notificationId = chatNotificationId(threadId.ifBlank { participantName.ifBlank { "message" } }),
        )
    }

    private fun createCallActivityIntent(
        action: String,
        payload: CallNotificationPayload,
        autoAnswer: Boolean,
    ): Intent {
        return Intent(this, MainActivity::class.java).apply {
            this.action = action
            putExtra(EXTRA_THREAD_ID, payload.threadId)
            putExtra(EXTRA_CALL_ID, payload.callId)
            putExtra(EXTRA_PEER_USER_ID, payload.peerUserId)
            putExtra(EXTRA_PARTICIPANT_NAME, payload.participantName)
            putExtra(EXTRA_CALL_TYPE, payload.callType.name)
            putExtra(EXTRA_DIRECTION, payload.direction)
            putExtra(EXTRA_AUTO_ANSWER, autoAnswer)
            putExtra(EXTRA_NOTIFICATION_ID, payload.notificationId)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun createChatActivityIntent(
        threadId: String,
        peerUserId: String,
        participantName: String,
        messagePreview: String,
    ): Intent {
        return Intent(this, MainActivity::class.java).apply {
            action = ACTION_OPEN_CHAT
            putExtra(EXTRA_THREAD_ID, threadId)
            putExtra(EXTRA_PEER_USER_ID, peerUserId)
            putExtra(EXTRA_PARTICIPANT_NAME, participantName)
            putExtra(EXTRA_MESSAGE_PREVIEW, messagePreview)
            putExtra(EXTRA_NOTIFICATION_ID, chatNotificationId(threadId.ifBlank { participantName.ifBlank { "message" } }))
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun createDeclineIntent(payload: CallNotificationPayload): Intent {
        return Intent(this, IncomingCallActionReceiver::class.java).apply {
            action = ACTION_DECLINE_CALL
            putExtra(EXTRA_THREAD_ID, payload.threadId)
            putExtra(EXTRA_CALL_ID, payload.callId)
            putExtra(EXTRA_PEER_USER_ID, payload.peerUserId)
            putExtra(EXTRA_PARTICIPANT_NAME, payload.participantName)
            putExtra(EXTRA_CALL_TYPE, payload.callType.name)
            putExtra(EXTRA_DIRECTION, payload.direction)
            putExtra(EXTRA_NOTIFICATION_ID, payload.notificationId)
        }
    }

    private fun createActivityPendingIntent(intent: Intent, requestCode: Int, salt: Int): PendingIntent {
        return PendingIntent.getActivity(
            this,
            requestCode + salt,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or pendingFlags(),
        )
    }

    private fun createBroadcastPendingIntent(intent: Intent, requestCode: Int, salt: Int): PendingIntent {
        return PendingIntent.getBroadcast(
            this,
            requestCode + salt,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or pendingFlags(),
        )
    }

    private fun ensureMessageChannel(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        manager.createNotificationChannel(
            NotificationChannel(MESSAGE_CHANNEL_ID, MESSAGE_CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = CHANNEL_DESCRIPTION
            }
        )
    }

    private fun ensureCallChannel(manager: NotificationManager, localized: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        manager.createNotificationChannel(
            NotificationChannel(CALL_CHANNEL_ID, localized.getString(R.string.notif_channel_missed_calls), NotificationManager.IMPORTANCE_DEFAULT).apply {
                lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
            }
        )
    }

    private fun ensureIncomingCallChannel(manager: NotificationManager, localized: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        manager.createNotificationChannel(
            NotificationChannel(INCOMING_CALL_CHANNEL_ID, localized.getString(R.string.notif_channel_incoming_calls), NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 800, 1200, 800, 1200)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
        )
    }

    private fun pendingFlags(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE
        } else {
            0
        }
    }
}
