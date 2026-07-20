package com.nova.app.core.model

import android.net.Uri
import androidx.compose.runtime.Immutable

enum class ChatAttachmentKind {
    Image,
    Video,
    Audio,
    File,
}

@Immutable
data class ChatAttachmentDraft(
    val uri: Uri,
    val kind: ChatAttachmentKind,
    val name: String,
    val mimeType: String,
    val durationSeconds: Int? = null,
    val previewUri: Uri? = null,
)
