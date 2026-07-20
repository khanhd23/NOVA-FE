package com.nova.app.core.backend

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.NotificationManager
import com.nova.app.core.model.CallEndReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class IncomingCallActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DECLINE_CALL) {
            return
        }

        val payload = intent.toCallNotificationPayload()
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
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                if (payload != null) {
                    notificationManager?.cancel(payload.notificationId)
                }
                pendingResult.finish()
            }
        }
    }
}
