package com.nova.app.core.backend

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.NotificationManager
import com.nova.app.core.call.CallActions
import com.nova.app.core.call.CallForegroundService
import com.nova.app.core.call.CallSystemRegistry
import com.nova.app.core.model.CallEndReason
import com.nova.app.core.webrtc.NovaWebRtcEngineRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class IncomingCallActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_DECLINE_CALL -> declineIncoming(context, intent)
            ACTION_HANG_UP_CALL -> hangUpOngoing(context, intent)
        }
    }

    private fun declineIncoming(context: Context, intent: Intent) {
        val payload = intent.toCallNotificationPayload()
        // If the app is running and already ringing for this call, let it end the call so the
        // UI, ringtone and backend stay in sync.
        if (payload != null && CallSystemRegistry.system?.currentCall?.callId == payload.callId &&
            CallActions.requestHangUp(payload.callId)
        ) {
            cancelNotification(context, payload.notificationId)
            return
        }
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val session = BackendSessionStore.loadSession(context)
                if (session != null && payload != null) {
                    runCatching {
                        NovaBackendClient().endCall(session.accessToken, payload.callId, CallEndReason.Declined)
                    }
                }
            } finally {
                if (payload != null) {
                    cancelNotification(context, payload.notificationId)
                }
                pendingResult.finish()
            }
        }
    }

    private fun hangUpOngoing(context: Context, intent: Intent) {
        val callId = intent.getStringExtra(EXTRA_CALL_ID).orEmpty()
        if (callId.isBlank() || CallActions.requestHangUp(callId)) {
            return
        }
        // UI is gone (activity destroyed) but the service kept the process alive: end directly.
        val reason = if (intent.getBooleanExtra(CallForegroundService.EXTRA_OUTGOING_RINGING, false)) {
            CallEndReason.Canceled
        } else {
            CallEndReason.HungUp
        }
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val session = BackendSessionStore.loadSession(context)
                if (session != null) {
                    runCatching { NovaBackendClient().endCall(session.accessToken, callId, reason) }
                }
                NovaWebRtcEngineRegistry.engine?.endCurrentCall(sendBye = true)
            } finally {
                CallSystemRegistry.system?.audio?.release()
                CallForegroundService.stop(context)
                pendingResult.finish()
            }
        }
    }

    private fun cancelNotification(context: Context, notificationId: Int) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.cancel(notificationId)
    }
}
