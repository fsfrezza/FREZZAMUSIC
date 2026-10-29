package com.frezzamusic.app.ui

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/** Full lyrics remain scrollable; touch suspends following until two seconds after release. */
@Composable internal fun SyncedLyricsView(lines: List<String>, current: Int, focus: Int = current) {
    val state = rememberLazyListState()
    var browsing by remember { mutableStateOf(false) }
    var fingerDown by remember { mutableStateOf(false) }
    LaunchedEffect(browsing, fingerDown) {
        if (browsing && !fingerDown) {
            delay(2_000)
            browsing = false
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val rowHeight = maxHeight / 3
        val edgePadding = (maxHeight - rowHeight) / 2
        val target = focus.coerceIn(0, (lines.size - 1).coerceAtLeast(0))
        LaunchedEffect(target, current, browsing, maxHeight) {
            if (browsing || lines.isEmpty()) return@LaunchedEffect
            snapshotFlow { state.layoutInfo.totalItemsCount }.first { it == lines.size }
            withFrameNanos { } // Measure the new font size before computing the center.
            // Avoid jumping when the next line is already on screen.
            if (state.layoutInfo.visibleItemsInfo.none { it.index == target }) {
                state.animateScrollToItem(target)
            }
            val layout = state.layoutInfo
            val item = layout.visibleItemsInfo.firstOrNull { it.index == target } ?: return@LaunchedEffect
            val center = (layout.viewportStartOffset + layout.viewportEndOffset) / 2f
            state.animateScrollBy(item.offset + item.size / 2f - center)
        }
        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    browsing = true
                    fingerDown = true
                    try {
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                        } while (event.changes.any { it.pressed })
                    } finally {
                        fingerDown = false
                    }
                }
            },
            contentPadding = PaddingValues(vertical = edgePadding)
        ) {
            itemsIndexed(lines) { index, text ->
                val active = index == current
                Box(
                    Modifier.fillMaxWidth().heightIn(min = rowHeight).padding(horizontal = 24.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text,
                        style = if (active) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.bodyLarge,
                        fontSize = if (active) 26.sp else 16.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
