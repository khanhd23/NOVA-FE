package com.nova.app.feature.chat

import android.content.Context
import android.media.MediaRecorder
import android.os.SystemClock
import java.io.File

class VoiceNoteRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var currentFile: File? = null
    private var startedAtMs: Long = 0L

    fun isRecording(): Boolean = recorder != null

    fun start(): Boolean {
        if (recorder != null) {
            return false
        }

        val directory = File(context.cacheDir, "voice-notes")
        if (!directory.exists()) {
            directory.mkdirs()
        }
        val file = File.createTempFile("voice-note-", ".m4a", directory)
        val mediaRecorder = MediaRecorder()
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        mediaRecorder.setAudioEncodingBitRate(128_000)
        mediaRecorder.setAudioSamplingRate(44_100)
        mediaRecorder.setOutputFile(file.absolutePath)
        mediaRecorder.prepare()
        mediaRecorder.start()

        recorder = mediaRecorder
        currentFile = file
        startedAtMs = SystemClock.elapsedRealtime()
        return true
    }

    fun stop(): VoiceNoteResult? {
        val mediaRecorder = recorder ?: return null
        val file = currentFile ?: return null
        return try {
            mediaRecorder.stop()
            VoiceNoteResult(
                file = file,
                durationSeconds = ((SystemClock.elapsedRealtime() - startedAtMs) / 1000L).toInt().coerceAtLeast(1),
            )
        } catch (_: Throwable) {
            file.delete()
            null
        } finally {
            runCatching { mediaRecorder.reset() }
            runCatching { mediaRecorder.release() }
            recorder = null
            currentFile = null
            startedAtMs = 0L
        }
    }

    fun cancel() {
        val file = currentFile
        stop()
        file?.delete()
    }
}

data class VoiceNoteResult(
    val file: File,
    val durationSeconds: Int,
)
