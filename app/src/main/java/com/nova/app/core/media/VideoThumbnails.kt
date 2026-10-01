package com.nova.app.core.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Locale

suspend fun loadVideoFrameBitmap(
    context: Context,
    source: String?,
): Bitmap? = withContext(Dispatchers.IO) {
    extractVideoFrameBitmap(context, source)
}

suspend fun createVideoThumbnailJpegBytes(
    context: Context,
    uri: Uri,
    quality: Int = 82,
): ByteArray? = withContext(Dispatchers.IO) {
    val bitmap = extractVideoFrameBitmap(context, uri.toString()) ?: return@withContext null
    ByteArrayOutputStream().use { output ->
        if (bitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(1, 100), output)) {
            output.toByteArray()
        } else {
            null
        }
    }
}

private fun extractVideoFrameBitmap(
    context: Context,
    source: String?,
): Bitmap? {
    if (source.isNullOrBlank()) return null

    val retriever = MediaMetadataRetriever()
    return try {
        if (!setVideoDataSource(context, retriever, source)) {
            return null
        }
        retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            ?: retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST)
    } catch (_: Throwable) {
        null
    } finally {
        runCatching { retriever.release() }
    }
}

private fun setVideoDataSource(
    context: Context,
    retriever: MediaMetadataRetriever,
    source: String,
): Boolean {
    val uri = runCatching { Uri.parse(source) }.getOrNull()
    val scheme = uri?.scheme?.lowercase(Locale.ROOT)
    return when {
        uri != null && (scheme == "content" || scheme == "android.resource" || scheme == "file") -> {
            retriever.setDataSource(context, uri)
            true
        }
        scheme == "http" || scheme == "https" -> false
        scheme.isNullOrBlank() -> {
            retriever.setDataSource(source)
            true
        }
        else -> false
    }
}
