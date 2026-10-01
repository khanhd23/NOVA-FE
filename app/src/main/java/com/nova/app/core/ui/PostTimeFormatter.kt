package com.nova.app.core.ui

import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SameDayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
private val SameYearFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
private val DifferentYearFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

fun formatPostTimestamp(createdAt: String?, fallback: String = ""): String {
    val instant = parseTimestamp(createdAt) ?: return fallback
    val zone = ZoneId.systemDefault()
    val dateTime = instant.atZone(zone)
    val today = LocalDate.now(zone)
    return when {
        dateTime.toLocalDate() == today -> dateTime.format(SameDayFormatter)
        dateTime.year == today.year -> dateTime.format(SameYearFormatter)
        else -> dateTime.format(DifferentYearFormatter)
    }
}

private fun parseTimestamp(value: String?): Instant? {
    val trimmed = value?.trim().orEmpty()
    if (trimmed.isBlank() || trimmed.equals("null", ignoreCase = true)) {
        return null
    }
    return runCatching { Instant.parse(trimmed) }
        .getOrElse {
            runCatching { OffsetDateTime.parse(trimmed).toInstant() }.getOrNull()
        }
}
