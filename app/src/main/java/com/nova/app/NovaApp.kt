package com.nova.app

import android.content.Intent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nova.app.core.backend.BackendRealtimeEvent
import com.nova.app.core.backend.BackendRealtimeEventType
import com.nova.app.core.backend.BackendRuntimeRegistry
import com.nova.app.core.di.NovaContainer
import com.nova.app.core.navigation.NovaNavHost
import com.nova.app.core.webrtc.NovaWebRtcEngine
import com.nova.app.core.webrtc.NovaWebRtcEngineRegistry
import com.nova.app.core.viewmodel.CallViewModel
import com.nova.app.core.viewmodel.FlowViewModel
import com.nova.app.ui.theme.NOVATheme

@Composable
fun NovaApp(launchIntent: Intent? = null) {
    val container = remember { NovaContainer() }
    val context = LocalContext.current.applicationContext
    val flowViewModel: FlowViewModel = viewModel(factory = container.viewModelFactory)
    val callViewModel: CallViewModel = viewModel(factory = container.viewModelFactory)
    val settings by flowViewModel.settings.collectAsStateWithLifecycle()

    LaunchedEffect(container.backendRuntime) {
        BackendRuntimeRegistry.runtime = container.backendRuntime
        container.backendRuntime.initialize(context)
    }

    LaunchedEffect(container.backendRuntime) {
        container.backendRuntime.session.collect { session ->
            container.repository.applyBackendSession(session)
            if (session != null) {
                runCatching {
                    container.repository.refreshMessages()
                }
                runCatching {
                    val realtimeConfig = container.backendRuntime.fetchRealtimeConfig()
                    NovaWebRtcEngineRegistry.engine?.applyRealtimeConfig(realtimeConfig)
                }
            }
        }
    }

    LaunchedEffect(container.backendRuntime) {
        if (NovaWebRtcEngineRegistry.engine == null) {
            NovaWebRtcEngineRegistry.engine = NovaWebRtcEngine()
        }
        NovaWebRtcEngineRegistry.engine?.initialize(context, container.backendRuntime)
        if (container.backendRuntime.currentSession() != null) {
            runCatching {
                val realtimeConfig = container.backendRuntime.fetchRealtimeConfig()
                NovaWebRtcEngineRegistry.engine?.applyRealtimeConfig(realtimeConfig)
            }
        }
    }

    LaunchedEffect(container.backendRuntime, callViewModel) {
        container.backendRuntime.events.collect { event ->
            val currentUserId = container.backendRuntime.currentSession()?.userId
            container.repository.applyRealtimeEvent(event, currentUserId)
            handleCallRealtimeEvent(event, currentUserId, callViewModel)
        }
    }

    NOVATheme(darkTheme = settings.darkMode) {
        NovaNavHost(
            container = container,
            flowViewModel = flowViewModel,
            callViewModel = callViewModel,
            settings = settings,
            launchIntent = launchIntent,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private fun handleCallRealtimeEvent(
    event: BackendRealtimeEvent,
    currentUserId: String?,
    callViewModel: CallViewModel,
) {
    val isForCurrentUser = currentUserId != null && event.targetUserId == currentUserId
    when (event.type) {
        BackendRealtimeEventType.CALL_STARTED -> {
            if (isForCurrentUser && event.actorUserId != currentUserId) {
                val participantName = event.payload["partnerName"]
                    ?: event.payload["summaryText"]
                    ?: event.title
                    ?: "User"
                when (event.payload["callType"]?.uppercase()) {
                    "VIDEO" -> callViewModel.startIncomingVideoCall(
                        participantName = participantName,
                        threadId = event.threadId.orEmpty(),
                        callId = event.callId,
                        peerUserId = event.actorUserId.orEmpty(),
                    )
                    else -> callViewModel.startIncomingVoiceCall(
                        participantName = participantName,
                        threadId = event.threadId.orEmpty(),
                        callId = event.callId,
                        peerUserId = event.actorUserId.orEmpty(),
                    )
                }
            }
        }
        BackendRealtimeEventType.CALL_SIGNAL -> {
            if (isForCurrentUser || event.actorUserId == currentUserId) {
                callViewModel.handleRealtimeSignal(event)
            }
        }
        BackendRealtimeEventType.CALL_ANSWERED -> {
            if (isForCurrentUser || event.actorUserId == currentUserId) {
                callViewModel.answerCall(syncBackend = false)
            }
        }
        BackendRealtimeEventType.CALL_ENDED -> {
            if (isForCurrentUser || event.actorUserId == currentUserId) {
                callViewModel.hangUp(mapBackendCallEndReason(event.payload["endReason"]), syncBackend = false)
            }
        }
        BackendRealtimeEventType.CALL_MINIMIZED -> {
            if (isForCurrentUser || event.actorUserId == currentUserId) {
                if (event.payload["minimized"]?.equals("true", ignoreCase = true) == true) {
                    callViewModel.minimize(syncBackend = false)
                } else {
                    callViewModel.expand(syncBackend = false)
                }
            }
        }
        else -> Unit
    }
}

private fun mapBackendCallEndReason(reason: String?): com.nova.app.core.model.CallEndReason {
    return when (reason?.uppercase()) {
        "CANCELED" -> com.nova.app.core.model.CallEndReason.Canceled
        "DECLINED" -> com.nova.app.core.model.CallEndReason.Declined
        "REJECTED" -> com.nova.app.core.model.CallEndReason.Rejected
        "MISSED" -> com.nova.app.core.model.CallEndReason.Missed
        "NO_ANSWER" -> com.nova.app.core.model.CallEndReason.NoAnswer
        "BUSY" -> com.nova.app.core.model.CallEndReason.Busy
        "DROPPED" -> com.nova.app.core.model.CallEndReason.Dropped
        "COMPLETED" -> com.nova.app.core.model.CallEndReason.Completed
        else -> com.nova.app.core.model.CallEndReason.HungUp
    }
}
