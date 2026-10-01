package com.nova.app.core.i18n

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.nova.app.R

/**
 * The repository and view models (no Android context) emit a few fixed English messages.
 * Map those to localized strings where they are displayed; anything else passes through.
 */
private val KNOWN_MESSAGES: Map<String, Int> = mapOf(
    "Backend is not connected" to R.string.msg_backend_not_connected,
    "Unable to load discover" to R.string.msg_discover_failed,
    "Unable to send poke" to R.string.msg_poke_failed,
    "Unable to load profile" to R.string.profile_load_failed,
    "Unable to load users" to R.string.msg_users_failed,
    "Missed call" to R.string.call_missed,
    "No answer" to R.string.call_no_answer,
    "Declined call" to R.string.call_declined,
    "Rejected call" to R.string.call_rejected,
    "Busy" to R.string.call_busy,
    "Canceled call" to R.string.call_canceled,
    "Call dropped" to R.string.call_dropped,
    "Call ended" to R.string.call_ended,
)

private val CONNECTED_PREFIX = Regex("^Connected (\\d+:\\d{2}(?::\\d{2})?)$")

@Composable
fun localizedMessage(message: String): String {
    KNOWN_MESSAGES[message.trim()]?.let { return stringResource(it) }
    CONNECTED_PREFIX.find(message.trim())?.let {
        return stringResource(R.string.call_connected_duration, it.groupValues[1])
    }
    return message
}
