package com.frezzamusic.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.frezzamusic.app.data.OnlineSubtitle
import com.frezzamusic.app.data.OnlineSubtitles
import com.frezzamusic.app.data.SubtitleCue
import com.frezzamusic.app.data.SubtitleFormat
import com.frezzamusic.app.model.Track
import kotlinx.coroutines.CancellationException
import java.util.Locale

@Composable internal fun LyricsPage(track: Track?, pos: Long) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("subtitle_language", 0) }
    var languages by remember(track?.id) { mutableStateOf<List<OnlineSubtitle>>(emptyList()) }
    var selected by remember(track?.id) { mutableStateOf<OnlineSubtitle?>(null) }
    var cues by remember(track?.id, selected?.id) { mutableStateOf<List<SubtitleCue>>(emptyList()) }
    var loading by remember(track?.id) { mutableStateOf(false) }
    var error by remember(track?.id) { mutableStateOf<String?>(null) }
    var retry by remember(track?.id) { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    LaunchedEffect(track?.id, retry) {
        val t = track ?: return@LaunchedEffect
        loading = true; error = null
        try {
            languages = OnlineSubtitles.available(t)
            val preferred = prefs.getString("language", Locale.getDefault().language)
            selected = languages.firstOrNull { it.language == preferred }
                ?: languages.firstOrNull { it.language.substringBefore('-') == preferred?.substringBefore('-') }
                ?: languages.firstOrNull { it.language == "pt" } ?: languages.firstOrNull()
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = "Não foi possível consultar as legendas online. Verifique sua conexão." }
        finally { loading = false }
    }
    LaunchedEffect(track?.id, selected?.id, retry) {
        val subtitle = selected ?: return@LaunchedEffect
        loading = true; error = null; cues = emptyList()
        try { cues = OnlineSubtitles.load(subtitle) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = "Não foi possível carregar esta legenda. Tente novamente." }
        finally { loading = false }
    }
    Column(Modifier.fillMaxSize()) {
        if (languages.isNotEmpty()) Box(Modifier.padding(horizontal = 16.dp)) {
            TextButton(onClick = { menu = true }) { Text("Idioma: ${selected?.label.orEmpty()} ▾") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                languages.forEach { language -> DropdownMenuItem(text = { Text(language.label) }, onClick = {
                    selected = language; menu = false; prefs.edit().putString("language", language.language).apply()
                }) }
            }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        val message = error
        if (message != null) {
            Text(message, Modifier.padding(16.dp))
            TextButton(onClick = { retry++ }) { Text("Tentar novamente") }
        }
        if (cues.isNotEmpty()) {
            val current = SubtitleFormat.current(cues, pos)
            val state = androidx.compose.foundation.lazy.rememberLazyListState()
            LaunchedEffect(current) { if (current >= 0) state.animateScrollToItem(current) }
            LazyColumn(state = state, modifier = Modifier.weight(1f), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                itemsIndexed(cues) { i, cue -> Text(cue.text,
                    style = if (i == current) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
                    fontWeight = if (i == current) FontWeight.Bold else FontWeight.Normal,
                    color = if (i == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        } else if (!loading) Box(Modifier.weight(1f)) { LocalLyricsPage(track, pos) }
    }
}
