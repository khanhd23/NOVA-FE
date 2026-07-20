package com.nova.app.core.model

enum class PostMediaKind {
    IMAGE,
    VIDEO,
    UNKNOWN
}

private val VIDEO_EXTENSIONS = setOf("mp4", "mov", "mkv", "webm", "m4v")
private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "heic", "heif")

private fun String.normalizedMediaValue(): String = trim()

private fun String.mediaExtension(): String? {
    val trimmed = normalizedMediaValue()
    if (trimmed.isEmpty()) return null
    val withoutQuery = trimmed.substringBefore('?').substringBefore('#')
    val extension = withoutQuery.substringAfterLast('.', missingDelimiterValue = "").lowercase()
    return extension.takeIf { it.isNotBlank() }
}

fun String.detectPostMediaKind(): PostMediaKind {
    val lower = normalizedMediaValue().lowercase()
    val extension = mediaExtension()
    return when {
        lower.startsWith("video/") -> PostMediaKind.VIDEO
        lower.startsWith("image/") -> PostMediaKind.IMAGE
        extension != null && extension in VIDEO_EXTENSIONS -> PostMediaKind.VIDEO
        extension != null && extension in IMAGE_EXTENSIONS -> PostMediaKind.IMAGE
        else -> PostMediaKind.UNKNOWN
    }
}

fun String.isVideoMediaUrl(): Boolean = detectPostMediaKind() == PostMediaKind.VIDEO

fun String.isImageMediaUrl(): Boolean = detectPostMediaKind() == PostMediaKind.IMAGE

fun List<String>.normalizedPostMediaUrls(): List<String> =
    asSequence()
        .map { it.normalizedMediaValue() }
        .filter { it.isNotBlank() }
        .distinct()
        .toList()

fun List<String>.hasVideoMedia(): Boolean = normalizedPostMediaUrls().any { it.isVideoMediaUrl() }

fun List<String>.hasImageMedia(): Boolean = normalizedPostMediaUrls().any { it.isImageMediaUrl() }

fun List<String>.hasMixedMedia(): Boolean = hasVideoMedia() && hasImageMedia()

fun List<String>.mediaKinds(): List<PostMediaKind> = normalizedPostMediaUrls().map { it.detectPostMediaKind() }
