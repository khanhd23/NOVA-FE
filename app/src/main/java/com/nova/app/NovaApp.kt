package com.nova.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
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
import com.nova.app.core.call.CallActions
import com.nova.app.core.call.CallSystem
import com.nova.app.core.call.CallSystemRegistry
import com.nova.app.core.di.NovaContainer
import com.nova.app.core.i18n.findActivity
import com.nova.app.core.model.CallType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.nova.app.core.i18n.ProvideAppLanguage
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
    val activity = LocalContext.current.findActivity()
    val callSystem = remember {
        CallSystemRegistry.system ?: CallSystem(context).also { CallSystemRegistry.system = it }
    }
    val flowViewModel: FlowViewModel = viewModel(factory = container.viewModelFactory)
    val callViewModel: CallViewModel = viewModel(factory = container.viewModelFactory)
    val settings by flowViewModel.settings.collectAsStateWithLifecycle()

    LaunchedEffect(container.backendRuntime) {
        BackendRuntimeRegistry.runtime = container.backendRuntime
        container.backendRuntime.initialize(context)
    }

    LaunchedEffect(container.backendRuntime) {
        var loadedForUserId: String? = null
        container.backendRuntime.session.collect { session ->
            container.repository.applyBackendSession(session)
            // Only reload data when the signed-in user changes, not on every token refresh.
            val userId = session?.userId
            if (userId == loadedForUserId) return@collect
            loadedForUserId = userId
            if (session != null) {
                // The profile view model may have started before the saved session was loaded;
                // reload the real profile (ID, VIP, diamonds) now that we can authenticate.
                runCatching {
                    container.repository.refreshProfile()
                }
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

    // Android 13+: without this, incoming-call and message notifications are silently blocked.
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Ringtone, audio routing, foreground service and notifications follow the call state.
    LaunchedEffect(callViewModel) {
        callViewModel.uiState.collect { state ->
            callSystem.onCallState(state)
            if (!state.isActive && callSystem.pictureInPicture.value) {
                activity?.moveTaskToBack(false)
            }
        }
    }

    // Media connection state from WebRTC drives "Connecting / Reconnecting" and the call timer.
    LaunchedEffect(callViewModel) {
        var engine = NovaWebRtcEngineRegistry.engine
        while (engine == null) {
            delay(100)
            engine = NovaWebRtcEngineRegistry.engine
        }
        launch { engine.state.collect(callViewModel::onMediaStateChanged) }
        engine.remoteHangups.collect(callViewModel::onRemoteHangup)
    }

    // Actions coming from notifications / push while the UI is alive.
    LaunchedEffect(callViewModel) {
        launch {
            CallActions.hangUpRequests.collect { callId ->
                if (callViewModel.uiState.value.callId == callId) {
                    callViewModel.hangUp()
                }
            }
        }
        CallActions.incomingCalls.collect { request ->
            callViewModel.receiveIncomingCall(
                participantName = request.participantName,
                threadId = request.threadId,
                callId = request.callId,
                peerUserId = request.peerUserId,
                callType = request.callType,
            )
        }
    }

    LaunchedEffect(container.backendRuntime, callViewModel) {
        container.backendRuntime.events.collect { event ->
            val currentUserId = container.backendRuntime.currentSession()?.userId
            container.repository.applyRealtimeEvent(event, currentUserId)
            // Notify from the live socket too, so messages still pop up when FCM is unavailable.
            if (event.type == BackendRealtimeEventType.MESSAGE_CREATED) {
                com.nova.app.core.backend.MessageNotifier.showFromRealtime(context, event)
            }
            handleCallRealtimeEvent(event, currentUserId, callViewModel)
        }
    }

    ProvideAppLanguage {
    NOVATheme(darkTheme = settings.darkMode) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
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
                val peerUserId = event.payload["peerUserId"]
                    ?: event.payload["callerId"]
                    ?: event.actorUserId
                    ?: ""
                val participantName = event.payload["peerName"]
                    ?: event.payload["callerName"]
                    ?: event.payload["partnerName"]
                    ?: event.payload["summaryText"]
                    ?: event.title
                    ?: "User"
                callViewModel.receiveIncomingCall(
                    participantName = participantName,
                    threadId = event.threadId.orEmpty(),
                    callId = event.callId,
                    peerUserId = peerUserId,
                    callType = if (event.payload["callType"]?.uppercase() == "VIDEO") CallType.Video else CallType.Voice,
                )
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
