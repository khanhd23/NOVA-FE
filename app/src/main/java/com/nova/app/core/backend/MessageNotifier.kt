package com.nova.app.core.backend

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import com.nova.app.MainActivity
import com.nova.app.R
import com.nova.app.core.call.AppVisibility
import com.nova.app.core.i18n.withAppLanguage

/** The conversation currently on screen; its messages are neither unread nor notified. */
object ActiveChat {
    @Volatile
    var threadId: String? = null
}

/**
 * Shows "new message" notifications. A message can arrive twice (WebSocket while the app is
 * alive, FCM push otherwise), so notifications are de-duplicated by message id.
 */
object MessageNotifier {
    private const val CHANNEL_ID = "nova_chat_messages"
    private const val MAX_REMEMBERED = 200
    private val notifiedMessageIds = LinkedHashSet<String>()

    fun showFromPush(context: Context, data: Map<String, String>) {
        show(
            context = context,
            threadId = data["threadId"].orEmpty(),
            messageId = data["messageId"].orEmpty(),
            senderId = data["senderId"].orEmpty().ifBlank { data["actorUserId"].orEmpty() },
            senderName = data["senderName"].orEmpty().ifBlank { data["title"].orEmpty() },
            text = data["text"].orEmpty(),
            attachmentKind = data["attachmentKind"].orEmpty(),
        )
    }

    fun showFromRealtime(context: Context, event: BackendRealtimeEvent) {
        show(
            context = context,
            threadId = event.threadId.orEmpty(),
            messageId = event.messageId.orEmpty().ifBlank { event.payload["messageId"].orEmpty() },
            senderId = event.payload["senderId"].orEmpty().ifBlank { event.actorUserId.orEmpty() },
            senderName = event.payload["senderName"].orEmpty(),
            text = event.payload["text"].orEmpty(),
            attachmentKind = event.payload["attachmentKind"].orEmpty(),
        )
    }

    fun cancel(context: Context, threadId: String) {
        if (threadId.isBlank()) return
        manager(context).cancel(chatNotificationId(threadId))
    }

    private fun show(
        context: Context,
        threadId: String,
        messageId: String,
        senderId: String,
        senderName: String,
        text: String,
        attachmentKind: String,
    ) {
        if (threadId.isBlank()) return
        // Never notify the sender about their own message (the backend sends to all participants).
        if (senderId.isNotBlank() && senderId == BackendSessionStore.loadUserId(context)) return
        // Already looking at this conversation.
        if (AppVisibility.isForeground && ActiveChat.threadId == threadId) return
        if (messageId.isNotBlank() && !remember(messageId)) return

        val localized = context.withAppLanguage()
        val name = senderName.ifBlank { localized.getString(R.string.app_name) }
        val preview = text.ifBlank { attachmentPreview(localized, attachmentKind) }
        val notificationId = chatNotificationId(threadId)

        val openChat = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_CHAT
            putExtra(EXTRA_THREAD_ID, threadId)
            putExtra(EXTRA_PEER_USER_ID, senderId)
            putExtra(EXTRA_PARTICIPANT_NAME, name)
            putExtra(EXTRA_MESSAGE_PREVIEW, preview)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        val contentIntent = PendingIntent.getActivity(context, notificationId, openChat, flags)

        val sender = Person.Builder().setName(name).setKey(senderId.ifBlank { threadId }).build()
        val me = Person.Builder().setName(localized.getString(R.string.community_you)).build()
        val style = NotificationCompat.MessagingStyle(me)
            .addMessage(preview, System.currentTimeMillis(), sender)

        ensureChannel(context, localized)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(name)
            .setContentText(preview)
            .setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
        manager(context).notify(notificationId, notification)
    }

    private fun attachmentPreview(context: Context, kind: String): String = context.getString(
        when (kind.uppercase()) {
            "IMAGE", "PHOTO" -> R.string.chat_photo
            "VIDEO" -> R.string.chat_video
            "AUDIO", "VOICE" -> R.string.chat_voice_message
            "FILE" -> R.string.chat_file
            else -> R.string.chat_attachment
        }
    )

    /** Returns false if this message was already notified. */
    private fun remember(messageId: String): Boolean = synchronized(notifiedMessageIds) {
        if (!notifiedMessageIds.add(messageId)) return false
        if (notifiedMessageIds.size > MAX_REMEMBERED) {
            notifiedMessageIds.remove(notifiedMessageIds.first())
        }
        true
    }

    private fun ensureChannel(context: Context, localized: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        // IMPORTANCE_HIGH so new messages pop up as heads-up notifications.
        manager(context).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, localized.getString(R.string.notif_channel_messages), NotificationManager.IMPORTANCE_HIGH)
        )
    }

    private fun manager(context: Context) =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
}
