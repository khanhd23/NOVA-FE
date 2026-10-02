package com.nova.app.core.webrtc

import android.content.Context
import android.graphics.SurfaceTexture
import android.view.TextureView
import org.webrtc.EglBase
import org.webrtc.EglRenderer
import org.webrtc.GlRectDrawer
import org.webrtc.VideoFrame
import org.webrtc.VideoSink
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Renders WebRTC video into a TextureView instead of a SurfaceView.
 *
 * A SurfaceView draws on its own window layer, so Compose clipping (rounded corners) and
 * overlapping views don't apply to it. A TextureView is composited with the rest of the UI,
 * so `Modifier.clip(RoundedCornerShape(...))` crops the video as expected.
 */
class TextureViewRenderer(context: Context) : TextureView(context), TextureView.SurfaceTextureListener, VideoSink {

    private val eglRenderer = EglRenderer("NovaTextureRenderer")
    private var initialized = false

    init {
        surfaceTextureListener = this
    }

    fun init(eglContext: EglBase.Context) {
        if (initialized) return
        eglRenderer.init(eglContext, EglBase.CONFIG_PLAIN, GlRectDrawer())
        initialized = true
        surfaceTexture?.let { onSurfaceTextureAvailable(it, width, height) }
    }

    fun setMirror(mirror: Boolean) = eglRenderer.setMirror(mirror)

    /** Shows nothing until the next frame, e.g. after the camera was switched off. */
    fun clearImage() = eglRenderer.clearImage()

    fun release() {
        if (!initialized) return
        eglRenderer.release()
        initialized = false
    }

    override fun onFrame(frame: VideoFrame) {
        eglRenderer.onFrame(frame)
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        if (!initialized) return
        updateAspectRatio(width, height)
        eglRenderer.createEglSurface(surface)
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        updateAspectRatio(width, height)
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        if (initialized) {
            // Wait until the render thread stops using the surface before it is destroyed.
            val released = CountDownLatch(1)
            eglRenderer.releaseEglSurface { released.countDown() }
            released.await(SURFACE_RELEASE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        }
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit

    private fun updateAspectRatio(width: Int, height: Int) {
        // Crops frames to the view's aspect ratio: "fill" scaling without stretching.
        if (width > 0 && height > 0) {
            eglRenderer.setLayoutAspectRatio(width.toFloat() / height)
        }
    }

    private companion object {
        const val SURFACE_RELEASE_TIMEOUT_MS = 500L
    }
}
