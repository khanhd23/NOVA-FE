package com.nova.app

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nova.app.core.backend.ACTION_ANSWER_CALL
import com.nova.app.core.backend.ACTION_OPEN_CALL
import com.nova.app.core.call.AppVisibility
import com.nova.app.core.call.CallActions
import com.nova.app.core.call.CallSystemRegistry
import android.view.WindowManager

class MainActivity : ComponentActivity() {
    private var launchIntent by mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launchIntent = intent
        prepareCallWindow(intent)
        // Registered before Compose, so it only runs when no screen handled Back (i.e. at the root).
        // Finishing the activity there would end an ongoing call; keep the call alive instead.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (CallSystemRegistry.system?.hasActiveCall == true) {
                    leaveAppKeepingCall()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })
        enableEdgeToEdge()
        setContent {
            NovaApp(launchIntent = launchIntent)
        }
    }

    override fun onStart() {
        super.onStart()
        AppVisibility.isForeground = true
    }

    override fun onStop() {
        AppVisibility.isForeground = false
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        launchIntent = intent
        prepareCallWindow(intent)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Like Messenger: leaving the app during a video call shrinks it to a floating window.
        // PiP must start right now here, so only when the call is already on screen.
        enterCallPictureInPicture(allowExpandFirst = false)
    }

    /** Back at the root during a call: video goes to picture-in-picture, voice keeps running in the background. */
    private fun leaveAppKeepingCall() {
        if (!enterCallPictureInPicture(allowExpandFirst = true)) {
            moveTaskToBack(true)
        }
    }

    private fun enterCallPictureInPicture(allowExpandFirst: Boolean): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            CallSystemRegistry.system?.isVideoCallInProgress != true ||
            isInPictureInPictureMode
        ) {
            return false
        }
        val params = PictureInPictureParams.Builder().setAspectRatio(Rational(9, 16)).build()
        if (CallSystemRegistry.system?.currentCall?.isMinimized == true) {
            if (!allowExpandFirst) return false
            // Show the call full screen first so the floating window contains the video, not the app.
            CallActions.requestExpand()
            window.decorView.postDelayed({ runCatching { enterPictureInPictureMode(params) } }, PIP_AFTER_EXPAND_DELAY_MS)
            return true
        }
        return runCatching { enterPictureInPictureMode(params) }.getOrDefault(false)
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        CallSystemRegistry.system?.setPictureInPicture(isInPictureInPictureMode)
    }

    private companion object {
        const val PIP_AFTER_EXPAND_DELAY_MS = 200L
    }

    private fun prepareCallWindow(intent: Intent?) {
        val action = intent?.action
        if (action != ACTION_OPEN_CALL && action != ACTION_ANSWER_CALL) {
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
    }
}
