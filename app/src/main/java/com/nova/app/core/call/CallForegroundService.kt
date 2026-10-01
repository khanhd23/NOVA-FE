package com.nova.app.core.call

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.nova.app.MainActivity
import com.nova.app.R
import com.nova.app.core.backend.ACTION_HANG_UP_CALL
import com.nova.app.core.backend.ACTION_OPEN_CALL
import com.nova.app.core.backend.BackendRuntimeRegistry
import com.nova.app.core.backend.EXTRA_CALL_ID
import com.nova.app.core.backend.EXTRA_CALL_TYPE
import com.nova.app.core.backend.EXTRA_DIRECTION
import com.nova.app.core.backend.EXTRA_PARTICIPANT_NAME
import com.nova.app.core.backend.EXTRA_PEER_USER_ID
import com.nova.app.core.backend.EXTRA_THREAD_ID
import com.nova.app.core.backend.IncomingCallActionReceiver
import com.nova.app.core.i18n.withAppLanguage
import com.nova.app.core.model.CallEndReason
import com.nova.app.core.model.CallSessionUiState
import com.nova.app.core.model.CallStatus
import com.nova.app.core.model.CallType
import com.nova.app.core.webrtc.NovaWebRtcEngineRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

const val ONGOING_CALL_CHANNEL_ID = "nova_calls_ongoing"
const val DIRECTION_ONGOING = "ONGOING"
private const val ONGOING_CALL_NOTIFICATION_ID = 40_999

/**
 * Keeps the process, microphone and camera alive while a call is in progress and the app is
 * in the background, and shows the ongoing-call notification (with hang-up and a timer).
 */
class CallForegroundService : Service() {

    private var callId: String = ""
    private var callType: CallType = CallType.Voice
    private var outgoingRinging: Boolean = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        callId = intent.getStringExtra(EXTRA_CALL_ID).orEmpty()
        callType = if (intent.getStringExtra(EXTRA_CALL_TYPE) == CallType.Video.name) CallType.Video else CallType.Voice
        outgoingRinging = intent.getBooleanExtra(EXTRA_OUTGOING_RINGING, false)

        val notification = buildNotification(intent)
        val started = runCatching {
            ServiceCompat.startForeground(this, ONGOING_CALL_NOTIFICATION_ID, notification, foregroundTypes())
        }
        if (started.isFailure) {
            // e.g. permission not granted yet or start not allowed from background:
            // the call keeps working while the app is visible, we simply retry on the next update.
            Log.w(TAG, "Unable to start call foreground service", started.exceptionOrNull())
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // The user swiped the app away: end the call properly instead of leaving the peer hanging.
        val id = callId
        if (id.isNotBlank()) {
            val reason = if (outgoingRinging) CallEndReason.Canceled else CallEndReason.HungUp
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                runCatching { BackendRuntimeRegistry.runtime?.endCall(id, reason) }
                NovaWebRtcEngineRegistry.engine?.endCurrentCall(sendBye = true)
            }
        }
        CallSystemRegistry.system?.audio?.release()
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    private fun foregroundTypes(): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return 0
        var types = 0
        if (granted(Manifest.permission.RECORD_AUDIO)) {
            types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        }
        if (callType == CallType.Video && granted(Manifest.permission.CAMERA)) {
            types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        }
        return types
    }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun buildNotification(intent: Intent): android.app.Notification {
        val localized = withAppLanguage()
        ensureChannel(localized)
        val name = intent.getStringExtra(EXTRA_PARTICIPANT_NAME).orEmpty()
            .ifBlank { localized.getString(R.string.call_default_name) }
        val connectedAt = intent.getLongExtra(EXTRA_CONNECTED_AT, 0L)
        val reconnecting = intent.getBooleanExtra(EXTRA_RECONNECTING, false)
        val text = localized.getString(
            when {
                reconnecting -> R.string.call_reconnecting
                outgoingRinging -> R.string.call_calling_ellipsis
                connectedAt == 0L -> R.string.call_connecting
                callType == CallType.Video -> R.string.call_ongoing_video
                else -> R.string.call_ongoing_voice
            }
        )

        val openIntent = Intent(this, MainActivity::class.java).apply {
            action = ACTION_OPEN_CALL
            putExtra(EXTRA_CALL_ID, callId)
            putExtra(EXTRA_THREAD_ID, intent.getStringExtra(EXTRA_THREAD_ID).orEmpty())
            putExtra(EXTRA_PEER_USER_ID, intent.getStringExtra(EXTRA_PEER_USER_ID).orEmpty())
            putExtra(EXTRA_PARTICIPANT_NAME, name)
            putExtra(EXTRA_CALL_TYPE, callType.name)
            putExtra(EXTRA_DIRECTION, DIRECTION_ONGOING)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val hangUpIntent = Intent(this, IncomingCallActionReceiver::class.java).apply {
            action = ACTION_HANG_UP_CALL
            putExtra(EXTRA_CALL_ID, callId)
            putExtra(EXTRA_OUTGOING_RINGING, outgoingRinging)
        }
        val immutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        val openPending = PendingIntent.getActivity(this, 1, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or immutable)
        val hangUpPending = PendingIntent.getBroadcast(this, 2, hangUpIntent, PendingIntent.FLAG_UPDATE_CURRENT or immutable)

        val person = Person.Builder().setName(name).setImportant(true).build()
        return NotificationCompat.Builder(this, ONGOING_CALL_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(name)
            .setContentText(text)
            .setStyle(NotificationCompat.CallStyle.forOngoingCall(person, hangUpPending).setIsVideo(callType == CallType.Video))
            .setContentIntent(openPending)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .apply {
                if (connectedAt > 0L && !reconnecting) {
                    setUsesChronometer(true)
                    setWhen(connectedAt)
                    setShowWhen(true)
                }
            }
            .build()
    }

    private fun ensureChannel(localized: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                ONGOING_CALL_CHANNEL_ID,
                localized.getString(R.string.notif_channel_ongoing_calls),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                setSound(null, null)
                enableVibration(false)
            }
        )
    }

    companion object {
        private const val TAG = "NovaCallService"
        private const val EXTRA_CONNECTED_AT = "extra_connected_at"
        private const val EXTRA_RECONNECTING = "extra_reconnecting"
        const val EXTRA_OUTGOING_RINGING = "extra_outgoing_ringing"

        fun update(context: Context, state: CallSessionUiState) {
            // A microphone-type foreground service can only start once the permission is granted;
            // the call screen requests it and the next state change retries.
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                return
            }
            val intent = Intent(context, CallForegroundService::class.java).apply {
                putExtra(EXTRA_CALL_ID, state.callId.orEmpty())
                putExtra(EXTRA_THREAD_ID, state.threadId)
                putExtra(EXTRA_PEER_USER_ID, state.peerUserId)
                putExtra(EXTRA_PARTICIPANT_NAME, state.participantName)
                putExtra(EXTRA_CALL_TYPE, state.callType.name)
                putExtra(EXTRA_OUTGOING_RINGING, state.status == CallStatus.Ringing)
                putExtra(EXTRA_RECONNECTING, state.isReconnecting)
                if (state.isMediaConnected) {
                    putExtra(EXTRA_CONNECTED_AT, System.currentTimeMillis() - state.durationSeconds * 1000L)
                }
            }
            runCatching { ContextCompat.startForegroundService(context, intent) }
                .onFailure { Log.w(TAG, "Unable to start call service", it) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CallForegroundService::class.java))
        }
    }
}
