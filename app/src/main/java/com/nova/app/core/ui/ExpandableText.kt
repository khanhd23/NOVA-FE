package com.nova.app.core.ui

import androidx.compose.ui.res.stringResource
import com.nova.app.R

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.nova.app.core.model.CommunityMention

@Composable
fun ExpandableText(
    text: String,
    modifier: Modifier = Modifier,
    collapsedMaxLines: Int = 2,
    mentions: List<CommunityMention> = emptyList(),
    onMentionClick: (String) -> Unit = {},
) {
    var expanded by remember(text) { mutableStateOf(false) }
    var hasOverflow by remember(text, collapsedMaxLines) { mutableStateOf(false) }
    val trimmed = text.trim()
    val mentionText = remember(trimmed, mentions) {
        buildMentionAnnotatedString(trimmed, mentions)
    }
    val maxLines = if (expanded) Int.MAX_VALUE else collapsedMaxLines
    val showToggle = expanded || hasOverflow

    Column(modifier = modifier) {
        if (mentionText != null) {
            ClickableText(
                text = mentionText,
                style = TextStyle(
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 14.sp,
                ),
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { result ->
                    if (!expanded) {
                        hasOverflow = result.hasVisualOverflow || result.lineCount > collapsedMaxLines
                    }
                },
                onClick = { offset ->
                    mentionText.getStringAnnotations(MENTION_TAG, offset, offset)
                        .firstOrNull()
                        ?.item
                        ?.let(onMentionClick)
                },
            )
        } else {
            Text(
                text = trimmed,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 14.sp,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { result ->
                    if (!expanded) {
                        hasOverflow = result.hasVisualOverflow || result.lineCount > collapsedMaxLines
                    }
                },
            )
        }
        if (showToggle) {
            Text(
                text = if (expanded) stringResource(R.string.text_less) else stringResource(R.string.text_more),
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { expanded = !expanded },
            )
        }
    }
}

private const val MENTION_TAG = "mention_user_id"

private fun buildMentionAnnotatedString(
    text: String,
    mentions: List<CommunityMention>,
): AnnotatedString? {
    if (text.isBlank() || mentions.isEmpty()) {
        return null
    }
    val ranges = mentionRanges(text, mentions)
    if (ranges.isEmpty()) {
        return null
    }
    return AnnotatedString.Builder(text).apply {
        ranges.forEach { range ->
            addStyle(
                SpanStyle(fontWeight = FontWeight.Bold),
                start = range.start,
                end = range.end,
            )
            addStringAnnotation(
                tag = MENTION_TAG,
                annotation = range.userId,
                start = range.start,
                end = range.end,
            )
        }
    }.toAnnotatedString()
}

private fun mentionRanges(text: String, mentions: List<CommunityMention>): List<MentionRange> {
    val ranges = mutableListOf<MentionRange>()
    val occupied = BooleanArray(text.length)
    mentions.forEach { mention ->
        mention.aliases()
            .sortedByDescending { it.length }
            .forEach { alias ->
                var index = text.indexOf(alias, ignoreCase = true)
                while (index >= 0) {
                    val end = index + alias.length
                    if ((index until end).none { occupied[it] }) {
                        ranges += MentionRange(index, end, mention.userId)
                        for (position in index until end) {
                            occupied[position] = true
                        }
                    }
                    index = text.indexOf(alias, startIndex = end, ignoreCase = true)
                }
            }
    }
    return ranges.sortedBy { it.start }
}

private fun CommunityMention.aliases(): List<String> {
    return listOf(
        username,
        displayName.replace(Regex("\\s+"), "."),
        displayName.replace(Regex("\\s+"), ""),
        displayName,
    )
        .map { it.trim().trimStart('@') }
        .filter { it.isNotBlank() }
        .distinct()
        .map { "@$it" }
}

private data class MentionRange(
    val start: Int,
    val end: Int,
    val userId: String,
)
