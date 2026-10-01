package com.nova.app.core.i18n

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.nova.app.R

/** Interest values are stored and sent to the backend in English; these map them to display labels. */
private val INTEREST_LABELS: Map<String, Int> = mapOf(
    "travel" to R.string.interest_travel,
    "music" to R.string.interest_music,
    "photography" to R.string.interest_photography,
    "gaming" to R.string.interest_gaming,
    "art" to R.string.interest_art,
    "sports" to R.string.interest_sports,
    "cooking" to R.string.interest_cooking,
    "nature" to R.string.interest_nature,
    "movies" to R.string.interest_movies,
    "tech" to R.string.interest_tech,
    "fitness" to R.string.interest_fitness,
    "reading" to R.string.interest_reading,
    "coffee" to R.string.interest_coffee,
    "dancing" to R.string.interest_dancing,
    "pets" to R.string.interest_pets,
    "fashion" to R.string.interest_fashion,
    "yoga" to R.string.interest_yoga,
    "anime" to R.string.interest_anime,
)

@StringRes
fun interestLabelRes(interest: String): Int? = INTEREST_LABELS[interest.trim().lowercase()]

/** Localized label for a known interest, or the raw value for custom ones. */
@Composable
fun interestLabel(interest: String): String =
    interestLabelRes(interest)?.let { stringResource(it) } ?: interest
