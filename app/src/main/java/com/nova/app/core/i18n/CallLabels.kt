package com.nova.app.core.i18n

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.nova.app.R
import com.nova.app.core.model.CallDirection
import com.nova.app.core.model.CallEndReason
import com.nova.app.core.model.CallStatus
import com.nova.app.core.model.displayLabel

@Composable
fun CallEndReason.localizedLabel(): String = stringResource(
    when (this) {
        CallEndReason.HungUp -> R.string.call_status_ended
        CallEndReason.Canceled -> R.string.call_status_canceled
        CallEndReason.Declined -> R.string.call_status_declined
        CallEndReason.Rejected -> R.string.call_status_rejected
        CallEndReason.Missed -> R.string.call_status_missed
        CallEndReason.NoAnswer -> R.string.call_no_answer
        CallEndReason.Busy -> R.string.call_busy
        CallEndReason.Dropped -> R.string.call_status_dropped
        CallEndReason.Completed -> R.string.call_status_completed
    }
)

@Composable
fun CallDirection.localizedLabel(): String = stringResource(
    when (this) {
        CallDirection.Incoming -> R.string.call_incoming
        CallDirection.Outgoing -> R.string.call_outgoing
    }
)

@Composable
fun CallStatus.localizedLabel(): String = stringResource(
    when (this) {
        CallStatus.Idle -> R.string.call_status_idle
        CallStatus.Ringing -> R.string.call_status_ringing
        CallStatus.InCall -> R.string.call_status_in_call
        CallStatus.Ended -> R.string.call_status_ended
    }
)

/** `lastEventLabel` is produced in English by the call view model; localize the known values. */
@Composable
fun localizedCallEvent(label: String): String {
    CallEndReason.entries.firstOrNull { it.displayLabel() == label }?.let { return it.localizedLabel() }
    return when (label) {
        "Connected" -> stringResource(R.string.call_connected)
        "Connecting" -> stringResource(R.string.call_connecting)
        "Incoming call" -> stringResource(R.string.call_incoming_call)
        "Calling" -> stringResource(R.string.call_calling)
        else -> label
    }
}
