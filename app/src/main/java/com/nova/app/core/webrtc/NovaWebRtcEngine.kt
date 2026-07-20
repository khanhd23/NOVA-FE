package com.nova.app.core.webrtc

import android.content.Context
import android.graphics.PixelFormat
import android.util.Log
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RendererCommon
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import com.nova.app.core.backend.BackendCallSignal
import com.nova.app.core.backend.BackendIceServer
import com.nova.app.core.backend.BackendRealtimeEvent
import com.nova.app.core.backend.BackendRealtimeConfig
import com.nova.app.core.backend.BackendRuntime
import com.nova.app.core.model.CallType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class WebRtcCallState(
    val callId: String? = null,
    val threadId: String = "",
    val peerUserId: String = "",
    val callType: CallType = CallType.Voice,
    val isOutgoing: Boolean = true,
    val hasLocalAccepted: Boolean = false,
    val remoteDescriptionReady: Boolean = false,
    val connectionState: String = "idle",
    val localPreviewReady: Boolean = false,
    val remoteVideoReady: Boolean = false,
    val errorMessage: String? = null,
)

object NovaWebRtcEngineRegistry {
    @Volatile
    var engine: NovaWebRtcEngine? = null
}

class NovaWebRtcEngine {
    private val scope = CoroutineScope(Job() + Dispatchers.IO)
    private val lock = Mutex()
    private val defaultIceServers = listOf(
        BackendIceServer("stun:stun.l.google.com:19302"),
        BackendIceServer("stun:stun1.l.google.com:19302"),
    )

    private var appContext: Context? = null
    private var backendRuntime: BackendRuntime? = null
    private var initialized = false
    private var eglBase: EglBase? = null
    private var factory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var localVideoSource: VideoSource? = null
    private var localAudioSource: AudioSource? = null
    private var localVideoTrack: VideoTrack? = null
    private var localAudioTrack: AudioTrack? = null
    private var remoteVideoTrack: VideoTrack? = null
    private var videoCapturer: VideoCapturer? = null
    private var localVideoCaptureStarted: Boolean = false
    private val localRenderers: MutableSet<SurfaceViewRenderer> = linkedSetOf()
    private val remoteRenderers: MutableSet<SurfaceViewRenderer> = linkedSetOf()
    private var currentState: WebRtcCallState = WebRtcCallState()
    private var pendingSignals: MutableList<BackendRealtimeEvent> = mutableListOf()
    @Volatile
    private var configuredIceServers: List<BackendIceServer> = defaultIceServers
    @Volatile
    private var maxIceRestartAttempts: Int = 2
    @Volatile
    private var iceRestartBackoffMs: Long = 1000L
    private var iceRestartAttempts: Int = 0
    private var iceRestartJob: Job? = null

    fun initialize(context: Context, runtime: BackendRuntime) {
        if (initialized) {
            backendRuntime = runtime
            appContext = context.applicationContext
            return
        }

        appContext = context.applicationContext
        backendRuntime = runtime
        eglBase = EglBase.create()

        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions
                .builder(appContext)
                .createInitializationOptions()
        )

        val audioModule = org.webrtc.audio.JavaAudioDeviceModule.builder(appContext)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .createAudioDeviceModule()

        val encoderFactory = DefaultVideoEncoderFactory(eglBase!!.eglBaseContext, true, true)
        val decoderFactory = DefaultVideoDecoderFactory(eglBase!!.eglBaseContext)

        factory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(audioModule)
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()

        initialized = true
    }

    fun applyRealtimeConfig(config: BackendRealtimeConfig?) {
        val iceServers = config?.iceServers
            ?.filter { it.url.isNotBlank() }
            .orEmpty()
            .ifEmpty { defaultIceServers }
        configuredIceServers = iceServers
        maxIceRestartAttempts = (config?.maxRestartAttempts ?: 2).coerceAtLeast(0)
        iceRestartBackoffMs = (config?.restartBackoffMs ?: 1000L).coerceAtLeast(250L)
    }

    fun attachLocalRenderer(renderer: SurfaceViewRenderer) {
        val egl = eglBase?.eglBaseContext ?: return
        renderer.holder.setFormat(PixelFormat.TRANSLUCENT)
        renderer.init(egl, null)
        renderer.setEnableHardwareScaler(true)
        renderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
        renderer.setMirror(true)
        renderer.setZOrderOnTop(true)
        renderer.setZOrderMediaOverlay(true)
        localRenderers.add(renderer)
        localVideoTrack?.addSink(renderer)
        Log.d(TAG, "Local renderer attached. renderers=${localRenderers.size}, hasTrack=${localVideoTrack != null}")
    }

    fun attachRemoteRenderer(renderer: SurfaceViewRenderer) {
        val egl = eglBase?.eglBaseContext ?: return
        renderer.holder.setFormat(PixelFormat.OPAQUE)
        renderer.init(egl, null)
        renderer.setEnableHardwareScaler(true)
        renderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
        renderer.setMirror(false)
        renderer.setZOrderOnTop(false)
        renderer.setZOrderMediaOverlay(false)
        remoteRenderers.add(renderer)
        remoteVideoTrack?.addSink(renderer)
        currentState = currentState.copy(remoteVideoReady = remoteVideoTrack != null)
        Log.d(TAG, "Remote renderer attached. renderers=${remoteRenderers.size}, hasTrack=${remoteVideoTrack != null}")
    }

    fun detachLocalRenderer(renderer: SurfaceViewRenderer) {
        localVideoTrack?.removeSink(renderer)
        localRenderers.remove(renderer)
        Log.d(TAG, "Local renderer detached. renderers=${localRenderers.size}")
    }

    fun detachRemoteRenderer(renderer: SurfaceViewRenderer) {
        remoteVideoTrack?.removeSink(renderer)
        remoteRenderers.remove(renderer)
        Log.d(TAG, "Remote renderer detached. renderers=${remoteRenderers.size}")
    }

    suspend fun beginOutgoingCall(
        callId: String,
        threadId: String,
        peerUserId: String,
        callType: CallType,
    ) = lock.withLock {
        startSession(
            callId = callId,
            threadId = threadId,
            peerUserId = peerUserId,
            callType = callType,
            isOutgoing = true,
        )
        createOffer(iceRestart = false)
        drainPendingSignals()
    }

    suspend fun beginIncomingCall(
        callId: String,
        threadId: String,
        peerUserId: String,
        callType: CallType,
    ) = lock.withLock {
        startSession(
            callId = callId,
            threadId = threadId,
            peerUserId = peerUserId,
            callType = callType,
            isOutgoing = false,
        )
        drainPendingSignals()
    }

    suspend fun handleRealtimeEvent(event: BackendRealtimeEvent) {
        lock.withLock {
            if (event.type.name != "CALL_SIGNAL") {
                return@withLock
            }
            val state = currentState
            if (state.callId.isNullOrBlank() || event.callId.isNullOrBlank()) {
                pendingSignals.add(event)
                return@withLock
            }
            if (state.callId != event.callId) {
                pendingSignals.add(event)
                return@withLock
            }
            applySignal(event)
        }
    }

    suspend fun answerCurrentCall() {
        lock.withLock {
            if (currentState.callId.isNullOrBlank()) {
                return@withLock
            }
            currentState = currentState.copy(hasLocalAccepted = true)
            drainPendingSignals()
        }
    }

    suspend fun endCurrentCall(sendBye: Boolean = true) = lock.withLock {
        if (sendBye) {
            sendSignal("bye", null, null, null, null, null)
        }
        releasePeerConnection()
        currentState = WebRtcCallState()
        pendingSignals.clear()
    }

    suspend fun setMicEnabled(enabled: Boolean) = lock.withLock {
        localAudioTrack?.setEnabled(enabled)
    }

    suspend fun setVideoEnabled(enabled: Boolean) = lock.withLock {
        if (enabled) {
            val context = appContext ?: return@withLock
            if (localVideoTrack == null) {
                prepareLocalMedia(context, CallType.Video)
                return@withLock
            }
            startLocalVideoCaptureIfNeeded()
            localVideoTrack?.setEnabled(true)
            attachCurrentRenderers()
            currentState = currentState.copy(localPreviewReady = true)
        } else {
            localVideoTrack?.setEnabled(false)
            stopLocalVideoCapture()
            currentState = currentState.copy(localPreviewReady = false)
        }
    }

    suspend fun ensureLocalVideoPreview() = lock.withLock {
        if (localVideoTrack != null) {
            attachCurrentRenderers()
            Log.d(TAG, "Local preview already active. renderers=${localRenderers.size}")
            return@withLock
        }
        val context = appContext ?: return@withLock
        prepareLocalMedia(context, CallType.Video)
        Log.d(TAG, "Local preview requested. hasTrack=${localVideoTrack != null}, renderers=${localRenderers.size}")
    }

    suspend fun switchCamera() {
        lock.withLock {
            val capturer = videoCapturer as? CameraVideoCapturer ?: return@withLock
            capturer.switchCamera(object : CameraVideoCapturer.CameraSwitchHandler {
                override fun onCameraSwitchDone(isFrontCamera: Boolean) {
                    Log.d(TAG, "Camera switched, front=$isFrontCamera")
                }

                override fun onCameraSwitchError(errorDescription: String?) {
                    Log.w(TAG, "Camera switch failed: $errorDescription")
                }
            })
        }
    }

    private fun startSession(
        callId: String,
        threadId: String,
        peerUserId: String,
        callType: CallType,
        isOutgoing: Boolean,
    ) {
        cancelIceRestart()
        releasePeerConnection()
        currentState = WebRtcCallState(
            callId = callId,
            threadId = threadId,
            peerUserId = peerUserId,
            callType = callType,
            isOutgoing = isOutgoing,
            hasLocalAccepted = isOutgoing,
            connectionState = "connecting",
            localPreviewReady = false,
            remoteVideoReady = false,
            errorMessage = null,
        )

        val factory = factory ?: return
        val context = appContext ?: return

        val iceServers = buildIceServers()
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers)
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN

        peerConnection = factory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onSignalingChange(newState: PeerConnection.SignalingState) = Unit

            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState) {
                currentState = currentState.copy(connectionState = newState.name.lowercase(Locale.ROOT))
                when (newState) {
                    PeerConnection.IceConnectionState.CONNECTED,
                    PeerConnection.IceConnectionState.COMPLETED -> {
                        cancelIceRestart()
                        currentState = currentState.copy(errorMessage = null)
                    }
                    PeerConnection.IceConnectionState.DISCONNECTED,
                    PeerConnection.IceConnectionState.FAILED -> scheduleIceRestart(newState.name.lowercase(Locale.ROOT))
                    else -> Unit
                }
            }

            override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit

            override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState) = Unit

            override fun onIceCandidate(candidate: IceCandidate) {
                scope.launch {
                    backendRuntime?.sendCallSignal(
                        BackendCallSignal(
                            targetUserId = currentState.peerUserId,
                            threadId = currentState.threadId,
                            callId = currentState.callId.orEmpty(),
                            signalType = "candidate",
                            candidate = candidate.sdp,
                            sdpMid = candidate.sdpMid,
                            sdpMLineIndex = candidate.sdpMLineIndex,
                            video = currentState.callType == CallType.Video,
                        )
                    )
                }
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) = Unit

            override fun onAddStream(stream: MediaStream) {
                stream.videoTracks.firstOrNull()?.let { track ->
                    remoteVideoTrack = track
                    remoteRenderers.forEach { renderer ->
                        track.addSink(renderer)
                    }
                    currentState = currentState.copy(remoteVideoReady = true)
                }
            }

            override fun onRemoveStream(stream: MediaStream) = Unit

            override fun onDataChannel(dataChannel: org.webrtc.DataChannel) = Unit

            override fun onRenegotiationNeeded() = Unit

            override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) = Unit

            override fun onTrack(transceiver: org.webrtc.RtpTransceiver?) {
                val track = transceiver?.receiver?.track()
                if (track is VideoTrack) {
                    remoteVideoTrack = track
                    remoteRenderers.forEach { renderer ->
                        track.addSink(renderer)
                    }
                    currentState = currentState.copy(remoteVideoReady = true)
                }
            }
        }) ?: return

        prepareLocalMedia(context, callType)
        attachCurrentRenderers()
    }

    private fun prepareLocalMedia(context: Context, callType: CallType) {
        val factory = factory ?: return
        val videoEnabled = callType == CallType.Video

        localAudioSource = factory.createAudioSource(MediaConstraints())
        localAudioTrack = factory.createAudioTrack("audio-track", localAudioSource)

        if (videoEnabled) {
            val capturer = createCameraCapturer(context)
            if (capturer != null) {
                videoCapturer = capturer
                val egl = eglBase?.eglBaseContext
                if (egl != null) {
                    val helper = SurfaceTextureHelper.create("CaptureThread", egl)
                    surfaceTextureHelper = helper
                    val videoSource = factory.createVideoSource(false)
                    localVideoSource = videoSource
                    capturer.initialize(helper, context, videoSource.capturerObserver)
                    try {
                        capturer.startCapture(LOCAL_VIDEO_WIDTH, LOCAL_VIDEO_HEIGHT, LOCAL_VIDEO_FPS)
                        localVideoCaptureStarted = true
                    } catch (throwable: Throwable) {
                        localVideoCaptureStarted = false
                        Log.w(TAG, "Failed to start camera capture: ${throwable.message}")
                    }
                    localVideoTrack = factory.createVideoTrack("video-track", videoSource)
                    localVideoTrack?.setEnabled(true)
                    Log.d(TAG, "Local video track created")
                }
            }
        }

        currentState = currentState.copy(
            localPreviewReady = videoEnabled && localVideoTrack != null,
        )

        attachCurrentRenderers()
        val connection = peerConnection ?: return
        localAudioTrack?.let { track ->
            connection.addTrack(track, listOf("audio"))
        }
        localVideoTrack?.let { track ->
            connection.addTrack(track, listOf("video"))
        }
    }

    private fun startLocalVideoCaptureIfNeeded() {
        val capturer = videoCapturer ?: return
        if (localVideoCaptureStarted) {
            return
        }
        try {
            capturer.startCapture(LOCAL_VIDEO_WIDTH, LOCAL_VIDEO_HEIGHT, LOCAL_VIDEO_FPS)
            localVideoCaptureStarted = true
        } catch (throwable: Throwable) {
            localVideoCaptureStarted = false
            Log.w(TAG, "Failed to restart camera capture: ${throwable.message}")
        }
    }

    private fun stopLocalVideoCapture() {
        val capturer = videoCapturer ?: return
        if (!localVideoCaptureStarted) {
            return
        }
        capturer.stopCaptureSafely()
        localVideoCaptureStarted = false
    }

    private fun attachCurrentRenderers() {
        localVideoTrack?.let { track ->
            localRenderers.forEach { renderer ->
                track.removeSink(renderer)
                track.addSink(renderer)
            }
            Log.d(TAG, "Local track attached to ${localRenderers.size} renderer(s)")
        }
    }

    private fun createCameraCapturer(context: Context): VideoCapturer? {
        val enumerator = Camera2Enumerator(context)
        val front = enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
        val back = enumerator.deviceNames.firstOrNull { enumerator.isBackFacing(it) }
        val deviceName = front ?: back ?: return null
        return enumerator.createCapturer(deviceName, null)
    }

    private suspend fun createOffer(iceRestart: Boolean) {
        val connection = peerConnection ?: return
        val constraints = offerConstraints(iceRestart)
        val offer = awaitSdp { observer ->
            connection.createOffer(observer, constraints)
        }
        connection.setLocalDescription(SimpleSdpObserver(), offer)
        sendSignal(
            signalType = "offer",
            sdpType = offer.type.name,
            sdp = offer.description,
            candidate = null,
            sdpMid = null,
            sdpMLineIndex = null,
        )
    }

    private fun applySignal(event: BackendRealtimeEvent) {
        val signalType = event.payload["signalType"]?.lowercase(Locale.ROOT) ?: return
        val connection = peerConnection ?: run {
            pendingSignals.add(event)
            return
        }
        when (signalType) {
            "offer" -> {
                if (!currentState.hasLocalAccepted) {
                    pendingSignals.add(event)
                    return
                }
                val sdp = event.payload["sdp"].orEmpty()
                val remote = SessionDescription(SessionDescription.Type.OFFER, sdp)
                connection.setRemoteDescription(SimpleSdpObserver(), remote)
                currentState = currentState.copy(
                    remoteDescriptionReady = true,
                    connectionState = "connecting",
                )
                scope.launch {
                    val answer = awaitSdp { observer ->
                        connection.createAnswer(observer, answerConstraints())
                    }
                    connection.setLocalDescription(SimpleSdpObserver(), answer)
                    sendSignal(
                        signalType = "answer",
                        sdpType = answer.type.name,
                        sdp = answer.description,
                        candidate = null,
                        sdpMid = null,
                        sdpMLineIndex = null,
                    )
                }
                drainPendingSignals()
            }
            "answer" -> {
                val sdp = event.payload["sdp"].orEmpty()
                val remote = SessionDescription(SessionDescription.Type.ANSWER, sdp)
                connection.setRemoteDescription(SimpleSdpObserver(), remote)
                currentState = currentState.copy(
                    connectionState = "connected",
                    remoteDescriptionReady = true,
                )
                drainPendingSignals()
            }
            "candidate" -> {
                if (!currentState.remoteDescriptionReady) {
                    pendingSignals.add(event)
                    return
                }
                val candidate = IceCandidate(
                    event.payload["sdpMid"].orEmpty(),
                    event.payload["sdpMLineIndex"]?.toIntOrNull() ?: 0,
                    event.payload["candidate"].orEmpty(),
                )
                connection.addIceCandidate(candidate)
            }
            "bye" -> {
                scope.launch {
                    endCurrentCall(sendBye = false)
                }
            }
        }
    }

    private suspend fun sendSignal(
        signalType: String,
        sdpType: String?,
        sdp: String?,
        candidate: String?,
        sdpMid: String?,
        sdpMLineIndex: Int?,
    ) {
        val signal = BackendCallSignal(
            targetUserId = currentState.peerUserId,
            threadId = currentState.threadId,
            callId = currentState.callId.orEmpty(),
            signalType = signalType,
            sdpType = sdpType,
            sdp = sdp,
            candidate = candidate,
            sdpMid = sdpMid,
            sdpMLineIndex = sdpMLineIndex,
            video = currentState.callType == CallType.Video,
        )
        backendRuntime?.sendCallSignal(signal)
    }

    private fun drainPendingSignals() {
        if (pendingSignals.isEmpty()) {
            return
        }
        val snapshot = pendingSignals.toList()
        pendingSignals.clear()
        snapshot.sortedBy { signalPriority(it.payload["signalType"]) }
            .forEach { applySignal(it) }
    }

    private fun signalPriority(signalType: String?): Int {
        return when (signalType?.lowercase(Locale.ROOT)) {
            "offer" -> 0
            "answer" -> 1
            "candidate" -> 2
            "bye" -> 3
            else -> 4
        }
    }

    private fun releasePeerConnection() {
        cancelIceRestart()
        try {
            peerConnection?.close()
        } catch (_: Throwable) {
        }
        peerConnection = null
        localVideoTrack?.let { track ->
            localRenderers.forEach { renderer -> track.removeSink(renderer) }
        }
        localVideoTrack?.dispose()
        localVideoTrack = null
        localAudioTrack?.dispose()
        localAudioTrack = null
        remoteVideoTrack?.let { track ->
            remoteRenderers.forEach { renderer -> track.removeSink(renderer) }
        }
        remoteVideoTrack = null
        localVideoSource?.dispose()
        localVideoSource = null
        localAudioSource?.dispose()
        localAudioSource = null
        videoCapturer?.stopCaptureSafely()
        videoCapturer?.dispose()
        videoCapturer = null
        localVideoCaptureStarted = false
        surfaceTextureHelper?.dispose()
        surfaceTextureHelper = null
        currentState = currentState.copy(
            localPreviewReady = false,
            remoteVideoReady = false,
            connectionState = "idle",
        )
    }

    private fun buildIceServers(): List<PeerConnection.IceServer> {
        return configuredIceServers.ifEmpty { defaultIceServers }
            .map { server ->
                val builder = PeerConnection.IceServer.builder(server.url)
                server.username?.takeIf { it.isNotBlank() }?.let { builder.setUsername(it) }
                server.credential?.takeIf { it.isNotBlank() }?.let { builder.setPassword(it) }
                builder.createIceServer()
            }
    }

    private fun offerConstraints(iceRestart: Boolean) = MediaConstraints().apply {
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        if (iceRestart) {
            mandatory.add(MediaConstraints.KeyValuePair("IceRestart", "true"))
        }
    }

    private fun answerConstraints() = MediaConstraints().apply {
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
    }

    private suspend fun awaitSdp(block: (SdpObserver) -> Unit): SessionDescription {
        return suspendCancellableCoroutine { cont ->
            block(object : SdpObserver {
                override fun onCreateSuccess(desc: SessionDescription) {
                    if (cont.isActive) cont.resume(desc)
                }

                override fun onSetSuccess() = Unit

                override fun onCreateFailure(error: String) {
                    if (cont.isActive) cont.resumeWithException(IllegalStateException(error))
                }

                override fun onSetFailure(error: String) = Unit
            })
        }
    }

    private inner class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(desc: SessionDescription) = Unit
        override fun onSetSuccess() = Unit
        override fun onCreateFailure(error: String) = Unit
        override fun onSetFailure(error: String) = Unit
    }

    private fun scheduleIceRestart(reason: String) {
        val callId = currentState.callId ?: return
        if (peerConnection == null) {
            return
        }
        if (!currentState.hasLocalAccepted && !currentState.remoteDescriptionReady) {
            return
        }
        if (iceRestartJob?.isActive == true) {
            return
        }
        if (iceRestartAttempts >= maxIceRestartAttempts) {
            currentState = currentState.copy(
                connectionState = "failed",
                errorMessage = "Call connection unstable",
            )
            return
        }

        iceRestartAttempts += 1
        val delayMs = iceRestartBackoffMs * iceRestartAttempts
        currentState = currentState.copy(
            connectionState = "reconnecting",
            errorMessage = "Reconnecting ($reason)",
        )

        iceRestartJob = scope.launch {
            delay(delayMs)
            lock.withLock {
                val connection = peerConnection ?: return@withLock
                if (currentState.callId != callId) {
                    return@withLock
                }
                runCatching {
                    val offer = awaitSdp { observer ->
                        connection.createOffer(observer, offerConstraints(iceRestart = true))
                    }
                    connection.setLocalDescription(SimpleSdpObserver(), offer)
                    sendSignal(
                        signalType = "offer",
                        sdpType = offer.type.name,
                        sdp = offer.description,
                        candidate = null,
                        sdpMid = null,
                        sdpMLineIndex = null,
                    )
                }.onSuccess {
                    currentState = currentState.copy(
                        connectionState = "connecting",
                        errorMessage = null,
                    )
                }.onFailure { throwable ->
                    currentState = currentState.copy(
                        connectionState = "failed",
                        errorMessage = throwable.message ?: "Failed to restart call",
                    )
                }
            }
        }
    }

    private fun cancelIceRestart() {
        iceRestartJob?.cancel()
        iceRestartJob = null
        iceRestartAttempts = 0
    }

    private fun VideoCapturer.stopCaptureSafely() {
        try {
            stopCapture()
        } catch (_: Throwable) {
        }
    }

    companion object {
        private const val TAG = "NovaWebRtc"
        private const val LOCAL_VIDEO_WIDTH = 1280
        private const val LOCAL_VIDEO_HEIGHT = 720
        private const val LOCAL_VIDEO_FPS = 30
    }
}
