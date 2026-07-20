package com.nova.app.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp

@Composable
fun ExpandableText(
    text: String,
    modifier: Modifier = Modifier,
    collapsedMaxLines: Int = 3,
) {
    var expanded by remember(text) { mutableStateOf(false) }
    val trimmed = text.trim()
    val canExpand = trimmed.length > 140 || trimmed.lines().size > collapsedMaxLines

    Column(modifier = modifier) {
        Text(
            text = trimmed,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 14.sp,
            maxLines = if (expanded) Int.MAX_VALUE else collapsedMaxLines,
            overflow = TextOverflow.Ellipsis,
        )
        if (canExpand) {
            Text(
                text = if (expanded) "Thu gọn" else "Xem thêm",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
                modifier = Modifier.clickable { expanded = !expanded },
            )
        }
    }
}
