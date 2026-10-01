package com.nova.app.core.call

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class CallAudioRoute { Earpiece, Speaker, Headset, Bluetooth }

data class CallAudioState(
    val route: CallAudioRoute = CallAudioRoute.Earpiece,
    val available: List<CallAudioRoute> = listOf(CallAudioRoute.Earpiece, CallAudioRoute.Speaker),
)

/**
 * Owns everything a phone call does with audio: ringtone + vibration for incoming calls,
 * ringback tone for outgoing calls, communication audio mode, output routing
 * (earpiece / speaker / wired headset / Bluetooth) and the proximity screen-off lock.
 */
class CallAudioController(context: Context) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow(CallAudioState())
    val state: StateFlow<CallAudioState> = _state.asStateFlow()

    private var ringtone: Ringtone? = null
    private var vibrating = false
    private var toneGenerator: ToneGenerator? = null
    private var focusRequest: AudioFocusRequest? = null
    private var inCallAudio = false
    private var isVideoCall = false
    private var userSelectedRoute = false
    private var proximityWanted = false
    private var proximityLock: PowerManager.WakeLock? = null

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = onDevicesChanged()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = onDevicesChanged()
    }

    // region Ringing

    fun startIncomingRing() {
        if (ringtone?.isPlaying == true || vibrating) return
        val ringerMode = audioManager.ringerMode
        if (ringerMode == AudioManager.RINGER_MODE_NORMAL) {
            runCatching {
                val uri = RingtoneManager.getActualDefaultRingtoneUri(appContext, RingtoneManager.TYPE_RINGTONE)
                    ?: Settings.System.DEFAULT_RINGTONE_URI
                ringtone = RingtoneManager.getRingtone(appContext, uri)?.apply {
                    audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) isLooping = true
                    play()
                }
            }.onFailure { Log.w(TAG, "Unable to play ringtone", it) }
        }
        if (ringerMode != AudioManager.RINGER_MODE_SILENT) {
            vibrator()?.let { vibrator ->
                vibrating = true
                val pattern = longArrayOf(0, 800, 1200)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(pattern, 0)
                }
            }
        }
    }

    fun startRingback() {
        if (toneGenerator != null) return
        toneGenerator = runCatching {
            ToneGenerator(AudioManager.STREAM_VOICE_CALL, RINGBACK_VOLUME).also {
                it.startTone(ToneGenerator.TONE_SUP_RINGTONE)
            }
        }.getOrNull()
    }

    fun stopRinging() {
        runCatching { ringtone?.stop() }
        ringtone = null
        if (vibrating) {
            vibrator()?.cancel()
            vibrating = false
        }
        toneGenerator?.let {
            runCatching { it.stopTone() }
            it.release()
        }
        toneGenerator = null
    }

    // endregion

    // region Call audio + routing

    fun enterCallAudio(isVideo: Boolean) {
        isVideoCall = isVideo
        if (inCallAudio) return
        inCallAudio = true
        userSelectedRoute = false
        requestFocus()
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.registerAudioDeviceCallback(deviceCallback, mainHandler)
        applyRoute(defaultRoute(availableRoutes()))
    }

    /** Messenger-style audio button: cycles through the outputs that are currently available. */
    fun cycleRoute() {
        if (!inCallAudio) return
        val available = availableRoutes()
        val index = available.indexOf(_state.value.route)
        userSelectedRoute = true
        applyRoute(available[(index + 1).mod(available.size)])
    }

    fun setProximityEnabled(enabled: Boolean) {
        proximityWanted = enabled
        updateProximityLock()
    }

    fun release() {
        stopRinging()
        if (inCallAudio) {
            runCatching { audioManager.unregisterAudioDeviceCallback(deviceCallback) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            } else {
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = false
                @Suppress("DEPRECATION")
                runCatching { audioManager.stopBluetoothSco() }
            }
            audioManager.mode = AudioManager.MODE_NORMAL
            abandonFocus()
        }
        inCallAudio = false
        userSelectedRoute = false
        proximityWanted = false
        updateProximityLock()
        _state.value = CallAudioState()
    }

    private fun onDevicesChanged() {
        if (!inCallAudio) return
        val available = availableRoutes()
        val current = _state.value.route
        // A headset/Bluetooth device appearing takes over, like on a phone call;
        // a removed device falls back to the default route.
        val route = when {
            !userSelectedRoute -> defaultRoute(available)
            current !in available -> defaultRoute(available)
            else -> current
        }
        applyRoute(route)
    }

    private fun defaultRoute(available: List<CallAudioRoute>): CallAudioRoute = when {
        CallAudioRoute.Bluetooth in available -> CallAudioRoute.Bluetooth
        CallAudioRoute.Headset in available -> CallAudioRoute.Headset
        isVideoCall || CallAudioRoute.Earpiece !in available -> CallAudioRoute.Speaker
        else -> CallAudioRoute.Earpiece
    }

    private fun availableRoutes(): List<CallAudioRoute> {
        val routes = linkedSetOf<CallAudioRoute>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.availableCommunicationDevices.forEach { device ->
                device.toRoute()?.let(routes::add)
            }
        } else {
            if (appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
                routes += CallAudioRoute.Earpiece
            }
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).forEach { device ->
                device.toRoute()?.takeIf { it != CallAudioRoute.Earpiece }?.let(routes::add)
            }
        }
        routes += CallAudioRoute.Speaker
        // Earpiece first, then speaker, then accessories: the order the button cycles through.
        return CallAudioRoute.entries.filter { it in routes }
    }

    private fun applyRoute(route: CallAudioRoute) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val device = audioManager.availableCommunicationDevices.firstOrNull { it.toRoute() == route }
            if (device != null) {
                audioManager.setCommunicationDevice(device)
            }
        } else {
            @Suppress("DEPRECATION")
            when (route) {
                CallAudioRoute.Speaker -> {
                    runCatching { audioManager.stopBluetoothSco() }
                    audioManager.isBluetoothScoOn = false
                    audioManager.isSpeakerphoneOn = true
                }
                CallAudioRoute.Bluetooth -> {
                    audioManager.isSpeakerphoneOn = false
                    runCatching { audioManager.startBluetoothSco() }
                    audioManager.isBluetoothScoOn = true
                }
                CallAudioRoute.Earpiece, CallAudioRoute.Headset -> {
                    runCatching { audioManager.stopBluetoothSco() }
                    audioManager.isBluetoothScoOn = false
                    audioManager.isSpeakerphoneOn = false
                }
            }
        }
        _state.value = CallAudioState(route = route, available = availableRoutes())
        updateProximityLock()
    }

    private fun AudioDeviceInfo.toRoute(): CallAudioRoute? = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> CallAudioRoute.Earpiece
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> CallAudioRoute.Speaker
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_USB_HEADSET -> CallAudioRoute.Headset
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> CallAudioRoute.Bluetooth
        else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && type == AudioDeviceInfo.TYPE_BLE_HEADSET) {
            CallAudioRoute.Bluetooth
        } else {
            null
        }
    }

    // endregion

    private fun updateProximityLock() {
        // Turn the screen off when the phone is held to the ear, only when audio uses the earpiece.
        val wanted = proximityWanted && inCallAudio && _state.value.route == CallAudioRoute.Earpiece
        val lock = proximityLock ?: run {
            val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!powerManager.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) return
            powerManager.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "nova:call-proximity").also {
                it.setReferenceCounted(false)
                proximityLock = it
            }
        }
        if (wanted && !lock.isHeld) {
            lock.acquire(MAX_CALL_WAKELOCK_MS)
        } else if (!wanted && lock.isHeld) {
            lock.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY)
        }
    }

    private fun requestFocus() {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attributes)
                .build()
            focusRequest = request
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        }
    }

    private fun abandonFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let(audioManager::abandonAudioFocusRequest)
            focusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
    }

    private fun vibrator(): Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }?.takeIf { it.hasVibrator() }

    private companion object {
        const val TAG = "NovaCallAudio"
        const val RINGBACK_VOLUME = 80
        const val MAX_CALL_WAKELOCK_MS = 4 * 60 * 60 * 1000L
    }
}
