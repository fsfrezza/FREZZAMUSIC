package com.frezzamusic.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.frezzamusic.app.data.*
import com.frezzamusic.app.model.*
import com.frezzamusic.app.player.PlaybackController
import com.frezzamusic.app.ui.FullPlayer
import com.frezzamusic.app.ui.theme.FrezzaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private lateinit var folders: FolderMusicRepository
    private var folderAdded: (() -> Unit)? = null

    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri ?: return@registerForActivityResult
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {
        }
        folders.add(uri)
        folderAdded?.invoke()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        folders = FolderMusicRepository(this)
        setContent {
            FrezzaTheme {
                FrezzaMusicApp(
                    repo = folders,
                    pickFolder = { callback ->
                        folderAdded = callback
                        picker.launch(null)
                    }
                )
            }
        }
    }
}

enum class AppTab { HOME, LIBRARY, ONLINE, PLAYLISTS, NEWS, MORE }

@Composable
fun FrezzaMusicApp(repo: FolderMusicRepository, pickFolder: ((() -> Unit)) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val user = remember { UserLibraryRepository(context) }
    val playback = remember { PlaybackController(context, DevelopmentDriveStreamResolver(), user) }

    var changeCounter by remember { mutableIntStateOf(0) }
    var tab by remember { mutableStateOf(AppTab.HOME) }
    var localTracks by remember { mutableStateOf(emptyList<Track>()) }
    var artists by remember { mutableStateOf(emptyList<Artist>()) }
    var roots by remember { mutableStateOf(repo.folders()) }
    var selectedAlbum by remember { mutableStateOf<Album?>(null) }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var playerExpanded by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        playback.onChanged = { changeCounter++ }
        playback.connect()
        onDispose { playback.release() }
    }

    suspend fun refreshLibrary() {
        loading = true
        roots = repo.folders()
        localTracks = withContext(Dispatchers.IO) { repo.scan() }
        artists = FrezzaDriveCatalog().artists().let { catalog ->
            when (BuildConfig.ARTIST_FILTER) {
                "" -> catalog
                else -> catalog.filter { it.name.equals(BuildConfig.ARTIST_FILTER, ignoreCase = true) }
            }
        }
        loading = false
    }

    LaunchedEffect(Unit) { refreshLibrary() }
    LaunchedEffect(changeCounter) {
        if (changeCounter > 0 && roots != repo.folders()) refreshLibrary()
    }

    val remoteTracks = artists.flatMap { it.albums }.flatMap { it.tracks }
    val allTracks = localTracks + remoteTracks
    val controller = playback.controller
    val currentTrack = allTracks.find { it.id == controller?.currentMediaItem?.mediaId }

    Scaffold(
        bottomBar = {
            Column {
                MiniPlayer(
                    track = currentTrack,
                    playing = controller?.isPlaying == true,
                    toggle = playback::toggle,
                    expand = { if (currentTrack != null) playerExpanded = true }
                )
                NavigationBar {
                    val tabs = listOf(
                        AppTab.HOME to Icons.Default.Home,
                        AppTab.LIBRARY to Icons.Default.LibraryMusic,
                        AppTab.ONLINE to Icons.Default.Cloud,
                        AppTab.PLAYLISTS to Icons.Default.PlaylistPlay,
                        AppTab.MORE to Icons.Default.MoreHoriz
                    )
                    tabs.forEach { (item, icon) ->
                        NavigationBarItem(
                            selected = tab == item,
                            onClick = { tab = item; selectedAlbum = null },
                            icon = { Icon(icon, contentDescription = null) },
                            label = { Text(item.name.lowercase().replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (tab) {
                AppTab.HOME -> HomeScreen(localTracks, remoteTracks, user, allTracks, { playback.play(it, allTracks) }) { tab = AppTab.ONLINE }
                AppTab.LIBRARY -> LibraryScreen(localTracks, query, { query = it }, { playback.play(it, localTracks) }, user)
                AppTab.ONLINE -> OnlineScreen(artists, selectedAlbum, { selectedAlbum = it }, { selectedAlbum = null }, { track, album -> playback.play(track, album.tracks) }, user)
                AppTab.PLAYLISTS -> CollectionsScreen(user, allTracks)
                AppTab.MORE -> MoreScreen(roots, { pickFolder { changeCounter++ } }, { repo.remove(it); changeCounter++ })
            }
            if (playerExpanded) {
                Surface(Modifier.fillMaxSize()) {
                    FullPlayer(currentTrack, playback, onClose = { playerExpanded = false })
                }
            }
            if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun HomeScreen(local: List<Track>, remote: List<Track>, user: UserLibraryRepository, all: List<Track>, play: (Track) -> Unit, online: () -> Unit) {
    val favorites = user.favorites()
    LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(if (BuildConfig.PROJECT_MODE == "FREZZAMUSIC") "FREZZAMUSIC" else BuildConfig.ARTIST_FILTER, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Text("${local.size} locais • ${remote.size} online • ${favorites.size} favoritas")
        }
        item {
            Button(onClick = online) {
                Icon(Icons.Default.Cloud, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Abrir FREZZAMUSIC Online")
            }
        }
        item { Text("Recentes", fontWeight = FontWeight.Bold) }
        items(user.history().mapNotNull { h -> all.find { it.id == h.trackId } }.take(12), key = { it.id }) { track ->
            TrackRow(track, favorites.contains(track.id), { play(track) }) { user.toggleFavorite(track.id) }
        }
    }
}

@Composable
private fun LibraryScreen(tracks: List<Track>, query: String, setQuery: (String) -> Unit, play: (Track) -> Unit, user: UserLibraryRepository) {
    val modes = listOf("Músicas", "Artistas", "Álbuns", "Gêneros", "Pastas")
    var mode by remember { mutableStateOf(modes.first()) }
    val filtered = tracks.filter { track ->
        query.isBlank() || listOf(track.title, track.artist, track.album, track.genre.orEmpty()).any { it.contains(query, ignoreCase = true) }
    }

    Column {
        OutlinedTextField(
            value = query,
            onValueChange = setQuery,
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            label = { Text("Buscar biblioteca") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
        )
        ScrollableTabRow(selectedTabIndex = modes.indexOf(mode)) {
            modes.forEach { item ->
                androidx.compose.material3.Tab(
                    selected = mode == item,
                    onClick = { mode = item },
                    text = { Text(item) }
                )
            }
        }
        LazyColumn {
            when (mode) {
                "Músicas" -> items(filtered, key = { it.id }) { track ->
                    TrackRow(track, user.favorites().contains(track.id), { play(track) }) { user.toggleFavorite(track.id) }
                }
                "Artistas" -> items(filtered.groupBy { it.artist }.toList(), key = { it.first }) { (name, list) ->
                    ListItem(headlineContent = { Text(name) }, supportingContent = { Text("${list.size} faixas") }, leadingContent = { Icon(Icons.Default.Person, null) })
                }
                "Álbuns" -> items(filtered.groupBy { it.album }.toList(), key = { it.first }) { (name, list) ->
                    ListItem(headlineContent = { Text(name) }, supportingContent = { Text("${list.first().artist} • ${list.size} faixas") }, leadingContent = { Icon(Icons.Default.Album, null) })
                }
                "Gêneros" -> items(filtered.groupBy { it.genre ?: "Sem gênero" }.toList(), key = { it.first }) { (name, list) ->
                    ListItem(headlineContent = { Text(name) }, supportingContent = { Text("${list.size} faixas") })
                }
                else -> item { Text("As pastas autorizadas são administradas em Mais → Pastas.", modifier = Modifier.padding(20.dp)) }
            }
        }
    }
}

@Composable
private fun OnlineScreen(artists: List<Artist>, album: Album?, open: (Album) -> Unit, back: () -> Unit, play: (Track, Album) -> Unit, user: UserLibraryRepository) {
    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        if (album != null) {
            item {
                TextButton(onClick = back) { Icon(Icons.Default.ArrowBack, null); Text("Álbuns") }
                Text(album.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(album.artist)
            }
            items(album.tracks, key = { it.id }) { track ->
                TrackRow(track, user.favorites().contains(track.id), { play(track, album) }) { user.toggleFavorite(track.id) }
            }
        } else {
            item {
                Text(if (BuildConfig.PROJECT_MODE == "FREZZAMUSIC") "FREZZAMUSIC Online" else BuildConfig.ARTIST_FILTER, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Streaming • catálogo remoto")
            }
            artists.forEach { artist ->
                item(key = "artist-${artist.name}") { Text(artist.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 18.dp)) }
                items(artist.albums, key = { it.id }) { item ->
                    ListItem(
                        headlineContent = { Text(item.title) },
                        supportingContent = { Text("${item.tracks.size} faixas") },
                        leadingContent = { Icon(Icons.Default.Album, null) },
                        trailingContent = { Icon(Icons.Default.ChevronRight, null) },
                        modifier = Modifier.clickable { open(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CollectionsScreen(user: UserLibraryRepository, all: List<Track>) {
    var name by remember { mutableStateOf("") }
    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        item {
            Text("Playlists e filas", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = name, onValueChange = { name = it }, modifier = Modifier.weight(1f), label = { Text("Nova playlist") })
                IconButton(onClick = {
                    if (name.isNotBlank()) {
                        user.savePlaylist(UserPlaylist(System.currentTimeMillis().toString(), name))
                        name = ""
                    }
                }) { Icon(Icons.Default.Add, null) }
            }
        }
        items(user.playlists(), key = { it.id }) { playlist ->
            val count = playlist.trackIds.count { id -> all.any { it.id == id } }
            ListItem(
                headlineContent = { Text(playlist.name) },
                supportingContent = { Text("$count faixas") },
                leadingContent = { Icon(Icons.Default.PlaylistPlay, null) },
                trailingContent = { IconButton(onClick = { user.deletePlaylist(playlist.id) }) { Icon(Icons.Default.Delete, null) } }
            )
        }
        item { Text("Filas salvas", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp)) }
        items(user.queues(), key = { it.id }) { queue ->
            ListItem(headlineContent = { Text(queue.name) }, supportingContent = { Text("${queue.trackIds.size} faixas") }, leadingContent = { Icon(Icons.Default.QueueMusic, null) })
        }
    }
}

@Composable
private fun NewsScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val identity = remember { ProjectIdentities.forMode(BuildConfig.PROJECT_MODE) }
    val news = remember { ReleaseRepository().announcements(BuildConfig.PROJECT_MODE) }
    LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text(identity.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Text(identity.subtitle, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            Text(identity.about, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
            identity.editorialUrl?.let { url ->
                OutlinedButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }) {
                    Icon(Icons.Default.Article, null); Spacer(Modifier.width(8.dp)); Text("Conteúdo editorial")
                }
            }
            Text("Novidades e lançamentos", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 20.dp))
        }
        items(news, key = { it.id }) { item ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (item.type == NewsType.BLOG_POST) Icons.Default.Article else Icons.Default.Campaign, null)
                        Spacer(Modifier.width(8.dp))
                        Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Text(item.artist + (item.releaseDate?.let { " • $it" } ?: ""), style = MaterialTheme.typography.labelMedium)
                    Text(item.message, modifier = Modifier.padding(top = 8.dp))
                    item.externalUrl?.let { url ->
                        TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }) { Text("Abrir") }
                    }
                }
            }
        }
    }
}

@Composable
private fun MoreScreen(folders: List<Uri>, add: () -> Unit, remove: (Uri) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        item {
            Text("Configurações", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Pastas da biblioteca", fontWeight = FontWeight.Bold)
            Button(onClick = add) { Text("Adicionar pasta") }
        }
        items(folders, key = { it.toString() }) { uri ->
            ListItem(
                headlineContent = { Text(uri.lastPathSegment ?: "Pasta") },
                supportingContent = { Text(uri.toString(), maxLines = 1) },
                leadingContent = { Icon(Icons.Default.Folder, null) },
                trailingContent = { IconButton(onClick = { remove(uri) }) { Icon(Icons.Default.Delete, null) } }
            )
        }
        item {
            HorizontalDivider()
            ListItem(headlineContent = { Text("Letras e LRC") }, supportingContent = { Text("Estrutura preparada para letras embutidas/arquivos sincronizados") }, leadingContent = { Icon(Icons.Default.Lyrics, null) })
            ListItem(headlineContent = { Text("Áudio") }, supportingContent = { Text("Media3 • gapless quando suportado • velocidade e crossfade preparados para evolução") }, leadingContent = { Icon(Icons.Default.Equalizer, null) })
            ListItem(headlineContent = { Text("Streaming e downloads") }, supportingContent = { Text("O catálogo oficial é livre para ouvir. Downloads em alta qualidade serão liberados por contribuição/licença.") }, leadingContent = { Icon(Icons.Default.Download, null) })
        }
    }
}

@Composable
private fun TrackRow(track: Track, favorite: Boolean, play: () -> Unit, toggleFavorite: () -> Unit) {
    ListItem(
        headlineContent = { Text(track.title) },
        supportingContent = { Text("${track.artist} • ${track.album}") },
        leadingContent = { Icon(if (track.remote) Icons.Default.Cloud else Icons.Default.MusicNote, null) },
        trailingContent = { IconButton(onClick = toggleFavorite) { Icon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null) } },
        modifier = Modifier.clickable(onClick = play)
    )
}

@Composable
private fun MiniPlayer(track: Track?, playing: Boolean, toggle: () -> Unit, expand: () -> Unit) {
    Surface(tonalElevation = 6.dp, modifier = Modifier.clickable(onClick = expand)) {
        Row(modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Album, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(track?.title ?: "Nada tocando", maxLines = 1, fontWeight = FontWeight.SemiBold)
                Text(track?.artist ?: "Selecione uma faixa", maxLines = 1, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = toggle, enabled = track != null) { Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null) }
        }
    }
}
