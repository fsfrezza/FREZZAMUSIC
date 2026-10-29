package com.frezzamusic.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.frezzamusic.app.model.Track
import com.frezzamusic.app.player.PlaybackController

/** The actual artwork supplies the background colors, under a high-contrast surface gradient. */
@Composable internal fun PlayerBackdrop(track: Track?, content: @Composable () -> Unit) {
    val surface = MaterialTheme.colorScheme.surface
    Box(Modifier.fillMaxSize().background(surface)) {
        if (!track?.artwork.isNullOrBlank()) AsyncImage(
            model = track?.artwork, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().blur(80.dp).alpha(0.45f)
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
            surface.copy(alpha = 0.88f), surface.copy(alpha = 0.72f), surface.copy(alpha = 0.96f)
        ))))
        content()
    }
}

@Composable internal fun ExpandedPlayerFooter(track: Track?, pc: PlaybackController, position: Long, duration: Long) {
    val player = pc.controller
    var scrubbing by remember(track?.id) { mutableStateOf(false) }
    var seekPosition by remember(track?.id) { mutableLongStateOf(0L) }
    val shownPosition = if (scrubbing) seekPosition else position
    val limit = duration.coerceAtLeast(1L)
    val canSeek = track != null && duration > 0 && player?.isCurrentMediaItemSeekable == true
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.75f)
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (!track?.artwork.isNullOrBlank()) AsyncImage(
                    model = track?.artwork, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp))
                ) else Icon(Icons.Default.Album, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(track?.title ?: "Nada tocando", style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(track?.artist.orEmpty(), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Slider(
                value = shownPosition.coerceIn(0L, limit).toFloat(),
                onValueChange = { scrubbing = true; seekPosition = it.toLong() },
                onValueChangeFinished = { if (scrubbing) pc.seek(seekPosition); scrubbing = false },
                valueRange = 0f..limit.toFloat(), enabled = canSeek,
                modifier = Modifier.fillMaxWidth().height(32.dp)
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(playerTime(shownPosition), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(playerTime(duration), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = pc::shuffle, enabled = track != null) {
                    Icon(Icons.Default.Shuffle, "Aleatório", tint = if (player?.shuffleModeEnabled == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = pc::previous, enabled = track != null) { Icon(Icons.Default.SkipPrevious, "Faixa anterior", Modifier.size(30.dp)) }
                FilledIconButton(onClick = pc::toggle, enabled = track != null, modifier = Modifier.size(52.dp)) {
                    Icon(if (player?.isPlaying == true) Icons.Default.Pause else Icons.Default.PlayArrow,
                        if (player?.isPlaying == true) "Pausar" else "Reproduzir", Modifier.size(32.dp))
                }
                IconButton(onClick = pc::next, enabled = track != null) { Icon(Icons.Default.SkipNext, "Próxima faixa", Modifier.size(30.dp)) }
                IconButton(onClick = pc::repeat, enabled = track != null) {
                    val mode = player?.repeatMode ?: 0
                    Icon(if (mode == 1) Icons.Default.RepeatOne else Icons.Default.Repeat,
                        if (mode == 1) "Repetindo esta faixa" else if (mode == 2) "Repetindo a fila" else "Repetição desligada",
                        tint = if (mode != 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun playerTime(ms: Long): String {
    val seconds = ms.coerceAtLeast(0L) / 1000
    return "${seconds / 60}:" + (seconds % 60).toString().padStart(2, '0')
}
