package com.frezzamusic.app

import com.frezzamusic.app.links.SharedLinkRouter
import com.frezzamusic.app.links.SharedContent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import coil3.compose.AsyncImage
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
import com.frezzamusic.app.ui.MetadataConflictReview
import com.frezzamusic.app.ui.theme.FrezzaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner

class MainActivity : ComponentActivity() {
    private var sharedUri by mutableStateOf<Uri?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (SharedLinkRouter.forward(this, intent.data)) { finish(); return }
        sharedUri = intent.data
    }

    private lateinit var folders: FolderMusicRepository
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private var mediaPermissionResult: ((Boolean) -> Unit)? = null
    private var visualizerPermissionResult: ((Boolean) -> Unit)? = null
    private val mediaPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { mediaPermissionResult?.invoke(it) }
    private val visualizerPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { visualizerPermissionResult?.invoke(it) }
    private var folderAdded: (() -> Unit)? = null
    private var writeGrantCallback: ((Uri?) -> Unit)? = null
    private var writeTarget: Uri? = null
    private val writePermission by lazy { MusicWritePermission(this) }
    private val writePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        val granted = result.resultCode == RESULT_OK && uri != null && writePermission.rememberGrant(uri, result.data?.flags ?: 0)
        val target = writeTarget
        val writable = if (granted && uri != null && target != null) writePermission.writableFileInTree(uri, target)?.uri else null
        writeGrantCallback?.invoke(writable)
        writeTarget = null
        writeGrantCallback = null
    }
    private var artistImagePicked: ((Uri) -> Unit)? = null
    private val imagePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { try { contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_:Exception){}; artistImagePicked?.invoke(it) } }

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
        if (SharedLinkRouter.forward(this, intent?.data)) { finish(); return }
        sharedUri = intent?.data
        folders = FolderMusicRepository(this)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        setContent {
            FrezzaTheme {
                FrezzaMusicApp(
                    repo = folders,
                    sharedUri = sharedUri,
                    consumeSharedUri = { sharedUri = null },
                    requestFolderWrite = { track, callback ->
                        val target = Uri.parse(track.uri)
                        val matching = folders.folders().firstOrNull { tree ->
                            writePermission.writableFileInTree(tree, target) != null
                        }
                        if (matching != null) callback(writePermission.writableFileInTree(matching, target)?.uri)
                        else {
                            writeGrantCallback = callback
                            writeTarget = target
                            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                            }
                            writePicker.launch(intent)
                        }
                    },
                    pickFolder = { callback -> folderAdded = callback; picker.launch(null) },
                    pickArtistImage = { callback -> artistImagePicked = callback; imagePicker.launch(arrayOf("image/*")) },
                    requestMediaAccess = { callback -> mediaPermissionResult=callback; mediaPermission.launch(if(Build.VERSION.SDK_INT>=33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE) },
                    requestVisualizerAccess = { callback -> visualizerPermissionResult=callback; visualizerPermission.launch(Manifest.permission.RECORD_AUDIO) }
                )
            }
        }
    }
}

enum class AppTab { HOME, LIBRARY, ONLINE, PLAYLISTS, MORE }

@Composable
fun FrezzaMusicApp(repo: FolderMusicRepository, sharedUri: Uri?, consumeSharedUri: () -> Unit, requestFolderWrite: (Track, (Uri?) -> Unit) -> Unit, pickFolder: ((() -> Unit)) -> Unit, pickArtistImage: (((Uri) -> Unit)) -> Unit, requestMediaAccess: ((Boolean) -> Unit) -> Unit, requestVisualizerAccess: ((Boolean) -> Unit) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val user = remember { UserLibraryRepository(context) }
    val metadata = remember { MetadataConflictRepository(context) }
    var showConflictReview by remember { mutableStateOf(false) }
    var showConflictNotice by remember { mutableStateOf(false) }
    var metadataRevision by remember { mutableIntStateOf(0) }
    var editingTrack by remember { mutableStateOf<Track?>(null) }
    var writeError by remember { mutableStateOf<String?>(null) }
    val tagWriter = remember { LocalMp3TagWriter(context) }
    val editScope = rememberCoroutineScope()
    val streamResolver = remember { DevelopmentDriveStreamResolver() }
    val playback = remember { PlaybackController(context, streamResolver, user) }
    val downloads = remember { OfflineDownloadRepository(context, streamResolver) }
    val settings = remember { SettingsRepository(context) }
    val purchaseIdentity = remember { PurchaseIdentity(context) }
    val globalRankings = remember { GlobalRankingsRepository.get(context) }
    DisposableEffect(purchaseIdentity,globalRankings) {
        globalRankings.configureIdentity(purchaseIdentity)
        val remove=purchaseIdentity.onAuthChanged { globalRankings.syncLogin() }
        onDispose { remove() }
    }
    val purchasesApi = remember(purchaseIdentity) {
        if (purchaseIdentity.available) PurchasedDownloadsApi(BuildConfig.PURCHASE_API_URL, { purchaseIdentity.idToken() })
        else null
    }
    val purchasedStorage = remember(purchasesApi) { purchasesApi?.let { PurchasedAudioStorage(context, it, purchaseIdentity) } }
    SideEffect { playback.configurePurchasedPlayback(purchasesApi, purchasedStorage, purchaseIdentity) }

    var changeCounter by remember { mutableIntStateOf(0) }
    var tab by remember { mutableStateOf(AppTab.HOME) }
    var localTracks by remember { mutableStateOf(emptyList<Track>()) }
    var artists by remember { mutableStateOf(emptyList<Artist>()) }
    var roots by remember { mutableStateOf(repo.folders()) }
    var selectedAlbum by remember { mutableStateOf<Album?>(null) }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var catalogLoaded by remember { mutableStateOf(false) }
    val liveCatalog = remember { LiveDriveCatalog(context) }
    var catalogStatus by remember { mutableStateOf(liveCatalog.status()) }
    var automaticCatalogCheck by remember { mutableStateOf(settings.autoCatalogCheck) }
    var checkingCatalog by remember { mutableStateOf(false) }
    var pendingCatalog by remember { mutableStateOf<CatalogPreview?>(null) }
    var playerExpanded by remember { mutableStateOf(false) }
    var fullPlayerPage by remember { mutableIntStateOf(0) }
    var purchaseInitialTrack by remember { mutableStateOf<Track?>(null) }
    var purchaseInitialAlbum by remember { mutableStateOf<Album?>(null) }
    var showPurchase by remember { mutableStateOf(false) }
    var showMyPurchases by remember { mutableStateOf(false) }
    val onboardingPrefs=remember{context.getSharedPreferences("onboarding",android.content.Context.MODE_PRIVATE)}
    var showMediaPrompt by remember{mutableStateOf(!onboardingPrefs.getBoolean("media_prompt_done",false))}
    var deviceScan by remember{mutableStateOf(onboardingPrefs.getBoolean("scan_device",false))}

    DisposableEffect(Unit) {
        playback.onChanged = { changeCounter++ }
        playback.connect()
        onDispose { playback.release() }
    }

    suspend fun refreshLibrary(online: Boolean = false) {
        if (loading || checkingCatalog) return
        loading = true
        try {
            artists = liveCatalog.cached(BuildConfig.ARTIST_FILTER)
            roots = repo.folders()
            localTracks = withContext(Dispatchers.IO) { val manual=repo.scan(); val device=if(deviceScan) repo.scanDevice() else emptyList(); repo.mergeWithoutDuplicates(manual,device) }
            if (online) {
                catalogStatus = "Atualizando catálogo online…"
                val update = liveCatalog.refresh(BuildConfig.ARTIST_FILTER)
                artists = update.artists
                catalogStatus = update.message
                selectedAlbum = selectedAlbum?.let { selected -> artists.flatMap { it.albums }.find { it.id == selected.id } }
            }
            (localTracks + artists.flatMap { it.albums }.flatMap { it.tracks }).forEach { track ->
                metadata.reconcile("track:" + track.id, mapOf("title" to track.title, "artist" to track.artist, "album" to track.album, "genre" to track.genre, "trackNumber" to track.trackNumber?.toString()))
            }
            if (metadata.pending().isNotEmpty()) showConflictNotice = true
        } catch (error: kotlinx.coroutines.CancellationException) { throw error }
        catch (_: Exception) { catalogStatus = "A atualização não foi concluída. Tente novamente." }
        finally { loading = false; catalogLoaded = true }
    }

    LaunchedEffect(Unit) { refreshLibrary(online = !liveCatalog.hasCatalog(BuildConfig.ARTIST_FILTER)) }
    LaunchedEffect(changeCounter) {
        if (changeCounter > 0) refreshLibrary()
    }
    val refreshAll: () -> Unit = { pendingCatalog = null; editScope.launch { refreshLibrary(online = true) }; Unit }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner, automaticCatalogCheck) {
        if (!automaticCatalogCheck) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            CatalogAutoCheck.afterOpen {
                // A new foreground session restarts the 30-second timer. Never run alongside a refresh.
                while (loading) kotlinx.coroutines.delay(250)
                if (pendingCatalog == null) {
                    checkingCatalog = true
                    catalogStatus = "Verificando atualizações do catálogo…"
                    try {
                        val preview = liveCatalog.check(BuildConfig.ARTIST_FILTER)
                        if (preview.hasChanges) {
                            pendingCatalog = preview
                            catalogStatus = "Há alterações disponíveis. Aguardando sua confirmação."
                        } else if (preview.failures.isNotEmpty()) {
                            catalogStatus = "Não foi possível verificar ${preview.failures.joinToString()}. A biblioteca foi mantida."
                        } else catalogStatus = "A biblioteca online está atualizada."
                    } catch (error: kotlinx.coroutines.CancellationException) { throw error }
                    catch (_: Exception) { catalogStatus = "Não foi possível verificar atualizações. A biblioteca foi mantida." }
                    finally { checkingCatalog = false }
                }
            }
        }
    }

    fun edited(track: Track): Track = track.copy(
        title = metadata.effectiveValue("track:" + track.id, "title", track.title) ?: track.title,
        artist = metadata.effectiveValue("track:" + track.id, "artist", track.artist) ?: track.artist,
        album = metadata.effectiveValue("track:" + track.id, "album", track.album) ?: track.album,
        genre = metadata.effectiveValue("track:" + track.id, "genre", track.genre),
        trackNumber = metadata.effectiveValue("track:" + track.id, "trackNumber", track.trackNumber?.toString())?.toIntOrNull())
    val visibleLocalTracks = remember(localTracks, metadataRevision) { localTracks.map(::edited) }
    val visibleArtists = remember(artists, metadataRevision) { artists.map { artist -> artist.copy(albums = artist.albums.map { album -> album.copy(tracks = album.tracks.map(::edited)) }) } }
    val remoteTracks = visibleArtists.flatMap { it.albums }.flatMap { it.tracks }
    val allTracks = visibleLocalTracks + remoteTracks
    val controller = playback.controller
    val currentTrack = allTracks.find { it.id == controller?.currentMediaItem?.mediaId }

    var sharedTrack by remember { mutableStateOf<Track?>(null) }
    var linkError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(sharedUri, visibleArtists, loading, catalogLoaded) {
        val uri = sharedUri ?: return@LaunchedEffect
        val target = SharedContent.parse(uri)
        if (target == null) {
            linkError = "Link de música ou álbum inválido."
            consumeSharedUri()
        } else if (BuildConfig.ARTIST_FILTER.isNotBlank() && !BuildConfig.ARTIST_FILTER.equals(target.artist, true)) {
            linkError = "Este conteúdo pertence a outro projeto. Abra o link no FREZZAMUSIC ou no aplicativo da banda."
            consumeSharedUri()
        } else if (!loading && catalogLoaded) {
            val albums = visibleArtists.filter { it.name.equals(target.artist, true) }.flatMap { it.albums }
            val found = albums.filter { a ->
                if (target.kind == "album") a.tracks.any { it.folder?.let { id -> SharedContent.matches(target, id) } == true }
                else a.tracks.any { it.remoteFileId?.let { id -> SharedContent.matches(target, id) } == true }
            }.singleOrNull()
            if (found != null) {
                tab = AppTab.ONLINE
                playerExpanded = false
                selectedAlbum = found
                sharedTrack = if (target.kind == "track") found.tracks.first { it.remoteFileId?.let { id -> SharedContent.matches(target, id) } == true } else null
            } else linkError = "Conteúdo não encontrado. Atualize a biblioteca e abra o link novamente."
            consumeSharedUri()
        }
    }
    if (sharedTrack != null) {
        val track = sharedTrack!!
        AlertDialog(onDismissRequest = { sharedTrack = null }, title = { Text(track.title) },
            text = { Text(track.artist + " • " + track.album) },
            confirmButton = { TextButton(enabled = controller != null, onClick = {
                playback.play(track, selectedAlbum?.tracks ?: listOf(track))
                fullPlayerPage = 0; playerExpanded = true; sharedTrack = null
            }) { Text("Ouvir música") } },
            dismissButton = { TextButton(onClick = { sharedTrack = null }) { Text("Ver álbum") } })
    }
    if (linkError != null) AlertDialog(onDismissRequest = { linkError = null },
        title = { Text("Link compartilhado") }, text = { Text(linkError!!) },
        confirmButton = { TextButton(onClick = { linkError = null; refreshAll() }) { Text("Atualizar biblioteca") } },
        dismissButton = { TextButton(onClick = { linkError = null }) { Text("Fechar") } })

    Scaffold(
        bottomBar = {
            Column {
                if(playerExpanded && fullPlayerPage==0) {
                    PlayerTransportBar(playback, controller?.isPlaying == true)
                } else {
                    MiniPlayer(
                        track = currentTrack,
                        playing = controller?.isPlaying == true,
                        toggle = playback::toggle,
                        expand = { if (currentTrack != null) { fullPlayerPage=0; playerExpanded = true } }
                    )
                }
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
                            onClick = { tab = item; selectedAlbum = null; playerExpanded = false },
                            icon = { Icon(icon, contentDescription = null) },
                            label = { Text(when(item){ AppTab.HOME -> "Início"; AppTab.LIBRARY -> "Locais"; AppTab.ONLINE -> if (BuildConfig.PROJECT_MODE == "SOLASIAS") "SOLASIAS" else BuildConfig.ARTIST_FILTER.ifBlank { "Online" }; AppTab.PLAYLISTS -> "Listas"; AppTab.MORE -> "Mais" }, maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (tab) {
                AppTab.HOME -> HomeScreen(visibleLocalTracks, remoteTracks, user, allTracks, { playback.play(it, allTracks); fullPlayerPage=0; playerExpanded=true }, { track -> playback.play(track, allTracks, playback.resumePosition(track.id)); fullPlayerPage=0; playerExpanded=true }, playback.lastTrackId()) { tab = AppTab.ONLINE }
                AppTab.LIBRARY -> LibraryScreen(visibleLocalTracks, query, { query = it }, { playback.play(it, visibleLocalTracks); fullPlayerPage=0; playerExpanded=true }, user, pickArtistImage, onEdit = { editingTrack = it })
                AppTab.ONLINE -> OnlineScreen(visibleArtists, selectedAlbum, { selectedAlbum = it }, { selectedAlbum = null }, { track, album -> playback.play(track, album.tracks); fullPlayerPage=0; playerExpanded=true }, user, downloads, { track, album -> purchaseInitialTrack=track; purchaseInitialAlbum=album; showPurchase=true }, refresh = refreshAll, refreshing = loading || checkingCatalog, catalogStatus = catalogStatus)
                AppTab.PLAYLISTS -> CollectionsScreen(user, allTracks, playback, onPlay = { track, tracks -> playback.play(track, tracks); fullPlayerPage=0; playerExpanded=true })
                AppTab.MORE -> MoreScreen(roots, { pickFolder { changeCounter++ } }, { repo.remove(it); changeCounter++ }, refreshAll, downloads, settings, playback, onMyPurchases = { showMyPurchases = true }, refreshing = loading || checkingCatalog, catalogStatus = catalogStatus, automaticCheck = automaticCatalogCheck, setAutomaticCheck = { enabled -> automaticCatalogCheck = enabled; settings.autoCatalogCheck = enabled })
            }
            pendingCatalog?.let { preview ->
                AlertDialog(
                    onDismissRequest = { pendingCatalog = null; catalogStatus = "Atualização adiada. Sua biblioteca foi mantida." },
                    title = { Text("Atualizar a biblioteca?") },
                    text = { Text("Encontramos mudanças no Drive: " + preview.summary() +
                        if (preview.failures.isEmpty()) "" else " Não foi possível consultar ${preview.failures.joinToString()}; esses catálogos serão mantidos.") },
                    confirmButton = { Button(onClick = {
                        pendingCatalog = null
                        editScope.launch {
                            loading = true
                            try {
                                val update = liveCatalog.apply(preview, BuildConfig.ARTIST_FILTER)
                                artists = update.artists
                                catalogStatus = update.message
                                selectedAlbum = selectedAlbum?.let { selected -> artists.flatMap { it.albums }.find { it.id == selected.id } }
                            } catch (error: kotlinx.coroutines.CancellationException) { throw error }
                            catch (_: Exception) { catalogStatus = "A confirmação expirou ou a atualização falhou. Toque em Atualizar biblioteca para tentar novamente." }
                            finally { loading = false }
                            refreshLibrary()
                        }
                    }) { Text("Atualizar") } },
                    dismissButton = { TextButton(onClick = { pendingCatalog = null; catalogStatus = "Atualização adiada. Sua biblioteca foi mantida." }) { Text("Agora não") } }
                )
            }
            editingTrack?.let { track ->
                TrackMetadataEditor(track, onDismiss = { editingTrack = null }, onSave = { changes ->
                    if (track.remote) {
                        changes.forEach { (field, value) -> metadata.saveEdit("track:" + track.id, field, value) }
                        metadataRevision++
                        editingTrack = null
                    } else requestFolderWrite(track) { writableUri ->
                        if (writableUri == null) writeError = "A pasta selecionada não corresponde ao arquivo ou a autorização foi negada. Selecione a pasta correta na próxima tentativa."
                        else editScope.launch {
                            try {
                                withContext(Dispatchers.IO) { tagWriter.save(writableUri, changes) }
                                editingTrack = null
                                changeCounter++
                            } catch (error: Exception) {
                                writeError = error.message ?: "Não foi possível gravar as tags."
                            }
                        }
                    }
                })
            }
            if (writeError != null) AlertDialog(onDismissRequest = { writeError = null }, title = { Text("Edição não concluída") }, text = { Text(writeError.orEmpty()) }, confirmButton = { TextButton(onClick = { writeError = null }) { Text("Entendi") } })
            if (showConflictReview) {
                Surface(Modifier.fillMaxSize()) {
                    MetadataConflictReview(metadata, onClose = { showConflictReview = false }, onApplied = { metadataRevision++ })
                }
            }
            if (showConflictNotice && !showConflictReview) AlertDialog(
                onDismissRequest = { showConflictNotice = false },
                title = { Text("Alterações encontradas") },
                text = { Text("Existem "+metadata.pending().size+" divergências entre suas edições e as fontes. Suas edições foram preservadas.") },
                confirmButton = { Button(onClick = { showConflictNotice = false; showConflictReview = true }) { Text("Ver alterações") } },
                dismissButton = { TextButton(onClick = { showConflictNotice = false }) { Text("Depois") } }
            )
            if (showMyPurchases) {
                Surface(Modifier.fillMaxSize()) { MyPurchasesScreen(remoteTracks, purchasesApi, purchasedStorage, purchaseIdentity, onClose = { showMyPurchases = false }, onSignOut = { playback.stopForAccountChange() }) }
            }
            if (showPurchase) {
                Surface(Modifier.fillMaxSize()) { PurchaseScreen(artists, purchaseInitialTrack, purchaseInitialAlbum, onClose={showPurchase=false}) }
            }
            if (playerExpanded && !showPurchase) {
                Surface(Modifier.fillMaxSize()) {
                    FullPlayer(currentTrack, playback, user, requestVisualizerAccess = requestVisualizerAccess, onPageChanged = { fullPlayerPage=it }, onClose = { playerExpanded = false })
                }
            }
            if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            if(showMediaPrompt) AlertDialog(onDismissRequest={},title={Text("Buscar mídias no celular?")},text={Text("Deseja que o FREZZAMUSIC procure as músicas presentes no celular e as adicione à biblioteca? Se preferir, você poderá continuar adicionando pastas manualmente em Mais.")},confirmButton={Button(onClick={requestMediaAccess{granted->onboardingPrefs.edit().putBoolean("media_prompt_done",true).putBoolean("scan_device",granted).apply();deviceScan=granted;showMediaPrompt=false;if(granted)changeCounter++}}){Text("Buscar mídias")}},dismissButton={OutlinedButton(onClick={onboardingPrefs.edit().putBoolean("media_prompt_done",true).putBoolean("scan_device",false).apply();showMediaPrompt=false}){Text("Agora não")}})
        }
    }
}

@Composable
private fun rememberFavorites(user: UserLibraryRepository): Set<String> {
 var favorites by remember(user) { mutableStateOf(user.favorites()) }
 DisposableEffect(user) { val remove=user.onFavoritesChanged { favorites=user.favorites() }; onDispose { remove() } }
 return favorites
}

@Composable
private fun HomeScreen(local: List<Track>, remote: List<Track>, user: UserLibraryRepository, all: List<Track>, play: (Track) -> Unit, resume: (Track) -> Unit, lastTrackId: String?, online: () -> Unit) {
    val favorites = rememberFavorites(user)
    val recent = user.history().mapNotNull { h -> all.find { it.id == h.trackId } }.take(8)
    val favoriteTracks = all.filter { it.id in favorites }.take(8)
    val lastTrack = lastTrackId?.let { id -> all.find { it.id == id } }
    val onlineAlbums = remote.groupBy { it.artist to it.album }.values.take(6)
    val rankingContext=androidx.compose.ui.platform.LocalContext.current
    val rankingsRepository=remember(rankingContext) { GlobalRankingsRepository.get(rankingContext) }
    var rankingSnapshot by remember { mutableStateOf<RankingSnapshot?>(null) }
    var rankingStatus by remember { mutableStateOf("Carregando rankings globais…") }
    var rankingRefresh by remember { mutableIntStateOf(0) }
    var rankingScope by remember { mutableStateOf(if(BuildConfig.PROJECT_MODE=="FREZZAMUSIC") "general" else BuildConfig.ARTIST_FILTER.lowercase(java.util.Locale.ROOT)) }
    LaunchedEffect(remote,rankingRefresh) {
        if(remote.isEmpty()) { rankingStatus="Carregando catálogo para os rankings…"; return@LaunchedEffect }
        while(kotlinx.coroutines.currentCoroutineContext().isActive) {
            try { rankingSnapshot=rankingsRepository.rankings(remote); rankingStatus="Contagem de todos os ouvintes dos três apps." }
            catch(error:kotlinx.coroutines.CancellationException){throw error}
            catch(_:Exception){rankingStatus=if(rankingSnapshot==null) "Não foi possível carregar os rankings. Tente atualizar." else "Sem conexão. Mostrando o último ranking obtido nesta sessão."}
            kotlinx.coroutines.delay(30_000)
        }
    }
    LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            if (BuildConfig.PROJECT_MODE == "FREZZAMUSIC") {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(painterResource(com.frezzamusic.app.R.drawable.frezzamusic_logo), contentDescription="FREZZAMUSIC", tint=Color.Unspecified, modifier=Modifier.size(132.dp))
                    Text("FREZZAMUSIC", style=MaterialTheme.typography.headlineMedium, fontWeight=FontWeight.Black)
                }
            } else Text(if(BuildConfig.PROJECT_MODE=="SOLASIAS") "SOLASIAS" else BuildConfig.ARTIST_FILTER, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Text("${local.size} locais • ${remote.size} online • ${favorites.size} favoritas", style=MaterialTheme.typography.bodySmall, modifier=Modifier.fillMaxWidth(), textAlign=TextAlign.Center)
        }
        item {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                Text("Rankings globais",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
                IconButton(onClick={rankingRefresh++}){Icon(Icons.Default.Refresh,"Atualizar rankings")}
            }
            Text(rankingStatus,style=MaterialTheme.typography.bodySmall)
            if(BuildConfig.PROJECT_MODE=="FREZZAMUSIC"){
                val scopes=listOf("general" to "Geral","solasias" to "SOLASIAS","thefrezza" to "theFrezza")
                ScrollableTabRow(scopes.indexOfFirst{it.first==rankingScope}.coerceAtLeast(0)){
                    scopes.forEach{(key,label)->Tab(selected=key==rankingScope,onClick={rankingScope=key},text={Text(label)})}
                }
            }
        }
        val lists=rankingSnapshot?.scopes?.get(rankingScope)
        for((kind,ranked) in listOf("Mais ouvidas" to lists?.played,"Mais curtidas" to lists?.liked)) {
            item(key="ranking-title-"+kind){Text(kind,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)}
            if(ranked.isNullOrEmpty()){
                item(key="ranking-empty-"+kind){Text(if(rankingSnapshot==null) "Aguardando dados do serviço." else "Ainda não há dados suficientes para esta lista.",style=MaterialTheme.typography.bodySmall)}
            } else items(ranked.take(10),key={"ranking-"+kind+"-"+it.track.id}) { rankedTrack ->
                val track=rankedTrack.track
                val position=ranked.indexOf(rankedTrack)+1
                ListItem(
                    headlineContent={Text("$position. ${track.title}",maxLines=1,overflow=TextOverflow.Ellipsis)},
                    supportingContent={Text(track.artist+" • "+rankedTrack.total+if(kind=="Mais ouvidas") " reproduções" else " curtidas")},
                    leadingContent={if(!track.artwork.isNullOrBlank())AsyncImage(track.artwork,null,Modifier.size(48.dp),contentScale=ContentScale.Crop)else Icon(Icons.Default.MusicNote,null)},
                    trailingContent={Icon(Icons.Default.PlayArrow,"Ouvir ${track.title}")},
                    modifier=Modifier.clickable{play(track)}
                )
            }
        }
        if (lastTrack != null) {
            item {
                ElevatedCard(Modifier.fillMaxWidth().clickable { resume(lastTrack) }) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (!lastTrack.artwork.isNullOrBlank()) AsyncImage(lastTrack.artwork, null, Modifier.size(64.dp), contentScale=ContentScale.Crop)
                        else Icon(Icons.Default.PlayCircle, null, Modifier.size(64.dp))
                        Column(Modifier.weight(1f).padding(horizontal=12.dp)) {
                            Text("Continue ouvindo", color=MaterialTheme.colorScheme.primary, fontWeight=FontWeight.Bold)
                            Text(lastTrack.title, fontWeight=FontWeight.Bold, maxLines=1, overflow=TextOverflow.Ellipsis)
                            Text(lastTrack.artist, style=MaterialTheme.typography.bodySmall)
                        }
                        Icon(Icons.Default.PlayArrow, "Continuar")
                    }
                }
            }
        }
        if (recent.isNotEmpty()) {
            item { Text("Tocadas recentemente", style=MaterialTheme.typography.titleMedium, fontWeight=FontWeight.Bold) }
            item {
                LazyRow(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    items(recent, key = { "recent-"+it.id }) { track ->
                        ElevatedCard(Modifier.width(150.dp).clickable { play(track) }) {
                            if (!track.artwork.isNullOrBlank()) AsyncImage(track.artwork,null,Modifier.fillMaxWidth().aspectRatio(1f),contentScale=ContentScale.Crop)
                            else Box(Modifier.fillMaxWidth().aspectRatio(1f),contentAlignment=Alignment.Center){Icon(Icons.Default.MusicNote,null,Modifier.size(54.dp))}
                            Text(track.title,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.padding(start=10.dp,end=10.dp,top=8.dp))
                            Text(track.artist,style=MaterialTheme.typography.bodySmall,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.padding(start=10.dp,end=10.dp,bottom=8.dp))
                        }
                    }
                }
            }
        }
        if (favoriteTracks.isNotEmpty()) {
            item { Text("Favoritas", style=MaterialTheme.typography.titleMedium, fontWeight=FontWeight.Bold, modifier=Modifier.padding(top=6.dp)) }
            item {
                LazyRow(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    items(favoriteTracks, key = { "fav-"+it.id }) { track ->
                        ElevatedCard(Modifier.width(150.dp).clickable { play(track) }) {
                            if (!track.artwork.isNullOrBlank()) AsyncImage(track.artwork,null,Modifier.fillMaxWidth().aspectRatio(1f),contentScale=ContentScale.Crop)
                            else Box(Modifier.fillMaxWidth().aspectRatio(1f),contentAlignment=Alignment.Center){Icon(Icons.Default.Favorite,null,Modifier.size(54.dp))}
                            Text(track.title,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.padding(start=10.dp,end=10.dp,top=8.dp))
                            Text(track.artist,style=MaterialTheme.typography.bodySmall,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.padding(start=10.dp,end=10.dp,bottom=8.dp))
                        }
                    }
                }
            }
        }
        if (onlineAlbums.isNotEmpty()) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically) {
                    Text("Do catálogo online", style=MaterialTheme.typography.titleMedium, fontWeight=FontWeight.Bold, modifier=Modifier.weight(1f))
                    TextButton(onClick=online){ Text("Ver tudo") }
                }
            }
            items(onlineAlbums, key={ "online-"+it.first().artist+"-"+it.first().album }) { tracks ->
                val first=tracks.first()
                ListItem(
                    headlineContent={Text(first.album, maxLines=1, overflow=TextOverflow.Ellipsis)},
                    supportingContent={Text(first.artist+" • "+tracks.size+" faixas")},
                    leadingContent={if(!first.artwork.isNullOrBlank()) AsyncImage(first.artwork,null,Modifier.size(56.dp),contentScale=ContentScale.Crop) else Icon(Icons.Default.Album,null)},
                    trailingContent={Icon(Icons.Default.PlayArrow,null)},
                    modifier=Modifier.clickable{play(first)}
                )
            }
        }
        if (remote.isEmpty()) {
            item {
                ElevatedCard(Modifier.fillMaxWidth().clickable(onClick=online)) {
                    Row(Modifier.padding(16.dp), verticalAlignment=Alignment.CenterVertically) {
                        Icon(Icons.Default.Cloud, contentDescription=null, tint=MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Catálogo online", fontWeight=FontWeight.Bold)
                            Text("Abra as músicas disponíveis online.", style=MaterialTheme.typography.bodySmall)
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription=null)
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryScreen(tracks: List<Track>, query: String, setQuery: (String) -> Unit, play: (Track) -> Unit, user: UserLibraryRepository, pickArtistImage: (((Uri) -> Unit)) -> Unit, onEdit: (Track) -> Unit) {
 val favorites=rememberFavorites(user)

    val modes=listOf("Músicas","Artistas","Álbuns","Pastas")
    var mode by remember{mutableStateOf("Músicas")}; var sort by remember{mutableStateOf("Título")}; var ascending by remember{mutableStateOf(true)}; var artistFilter by remember{mutableStateOf("Todas")}; var artistMenu by remember{mutableStateOf(false)}; var sortMenu by remember{mutableStateOf(false)}
    val artistOptions=listOf("Todas")+tracks.map{it.artist}.distinct().sorted()
    val base=tracks.filter{artistFilter=="Todas"||it.artist==artistFilter}.filter{query.isBlank()||listOf(it.title,it.artist,it.album,it.genre.orEmpty()).any{s->s.contains(query,true)}}
    val filtered=base.sortedWith(when(sort){"Artista"->compareBy(String.CASE_INSENSITIVE_ORDER){it.artist};"Álbum"->compareBy(String.CASE_INSENSITIVE_ORDER){it.album};"Data"->compareBy<Track>{it.dateMs?:0L};else->compareBy(String.CASE_INSENSITIVE_ORDER){it.title}}).let{if(ascending)it else it.reversed()}
    Column {
      OutlinedTextField(query,setQuery,Modifier.fillMaxWidth().padding(12.dp),singleLine=true,label={Text("Buscar mídias locais")},leadingIcon={Icon(Icons.Default.Search,null)})
      if(mode=="Músicas"||mode=="Álbuns") Column(Modifier.padding(horizontal=12.dp)){
        Row(verticalAlignment=Alignment.CenterVertically){Box(Modifier.weight(1f)){OutlinedButton(onClick={artistMenu=true},modifier=Modifier.fillMaxWidth()){Text(if(artistFilter=="Todas")"Todos os artistas" else artistFilter,maxLines=1,overflow=TextOverflow.Ellipsis);Icon(Icons.Default.ArrowDropDown,null)};DropdownMenu(artistMenu,{artistMenu=false}){artistOptions.forEach{a->DropdownMenuItem(text={Text(if(a=="Todas")"Todos os artistas" else a)},onClick={artistFilter=a;artistMenu=false})}}};Spacer(Modifier.width(8.dp));Box(Modifier.weight(1f)){OutlinedButton(onClick={sortMenu=true},modifier=Modifier.fillMaxWidth()){Text("Ordenar: $sort",maxLines=1);Icon(Icons.Default.ArrowDropDown,null)};DropdownMenu(sortMenu,{sortMenu=false}){listOf("Título","Artista","Álbum","Data").forEach{o->DropdownMenuItem(text={Text(o)},onClick={sort=o;sortMenu=false})}}};IconButton({ascending=!ascending}){Icon(if(ascending)Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,if(ascending)"Crescente" else "Decrescente")}}
      }
      ScrollableTabRow(modes.indexOf(mode)){modes.forEach{m->Tab(mode==m,{mode=m},text={Text(m,maxLines=1)})}}
      LazyColumn{when(mode){
        "Músicas"->items(filtered,key={it.id}){t->TrackRow(t,favorites.contains(t.id),{play(t)},{user.toggleFavorite(t)},onEdit)}
        "Artistas"->items(tracks.filter{query.isBlank()||it.artist.contains(query,true)}.groupBy{it.artist}.toList(),key={it.first}){(n,l)->val custom=user.artistImage(n);ElevatedCard(Modifier.padding(8.dp).fillMaxWidth().clickable{artistFilter=n;mode="Músicas"}){Row(Modifier.padding(10.dp),verticalAlignment=Alignment.CenterVertically){Card(Modifier.size(92.dp)){if(custom!=null)AsyncImage(custom,n,Modifier.fillMaxSize(),contentScale=ContentScale.Fit)else Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Icon(Icons.Default.Person,null,Modifier.size(50.dp))}};Column(Modifier.weight(1f).padding(start=12.dp)){Text(n,fontWeight=FontWeight.Bold);Text("${l.size} faixas");TextButton(onClick={pickArtistImage{uri->user.setArtistImage(n,uri.toString())}}){Icon(Icons.Default.Image,null);Text(" Escolher logo")};if(custom!=null)TextButton(onClick={user.clearArtistImage(n)}){Text("Remover imagem")}}}}}
        "Álbuns"->items(filtered.groupBy{it.artist+" / "+it.album}.values.toList().let{groups->when(sort){"Artista"->groups.sortedBy{it.first().artist.lowercase()};"Data"->AlbumFolderOrder.local(groups,tracks);else->groups.sortedBy{it.first().album.lowercase()}}.let{if(ascending)it else it.reversed()}},key={it.first().artist+"/"+it.first().album}){l->val f=l.first();ListItem(headlineContent={Text(f.album,maxLines=1,overflow=TextOverflow.Ellipsis)},supportingContent={Text(f.artist+" • "+l.size+" faixas")},leadingContent={if(!f.artwork.isNullOrBlank())AsyncImage(f.artwork,null,Modifier.size(56.dp),contentScale=ContentScale.Crop)else Icon(Icons.Default.Album,null)},modifier=Modifier.clickable{play(f)})}
        else->{
          val folders=tracks.filter{query.isBlank()||it.folder.orEmpty().contains(query,true)||it.title.contains(query,true)}.groupBy{it.folder?.takeIf{s->s.isNotBlank()}?:"Sem pasta identificada"}.toSortedMap(String.CASE_INSENSITIVE_ORDER)
          folders.forEach{(folder,folderTracks)->
            item(key="folder-header-"+folder){ListItem(headlineContent={Text(folder,fontWeight=FontWeight.Bold)},supportingContent={Text("${folderTracks.size} faixas")},leadingContent={Icon(Icons.Default.Folder,null)})}
            items(folderTracks,key={"folder-"+folder+"-"+it.id}){t->TrackRow(t,favorites.contains(t.id),{play(t)},{user.toggleFavorite(t)},onEdit)}
          }
        }
      }}
    }
}
@Composable
private fun OnlineScreen(artists: List<Artist>, album: Album?, open: (Album) -> Unit, back: () -> Unit, play: (Track, Album) -> Unit, user: UserLibraryRepository, downloads: OfflineDownloadRepository, buy: (Track?, Album?) -> Unit, refresh: () -> Unit, refreshing: Boolean, catalogStatus: String) {
 val shareContext = androidx.compose.ui.platform.LocalContext.current
 val favorites=rememberFavorites(user)

 val allAlbums=artists.flatMap{it.albums}; val tracks=allAlbums.flatMap{it.tracks}; var mode by remember{mutableStateOf("Álbuns")};var query by remember{mutableStateOf("")};var artistFilter by remember{mutableStateOf("Todas")};var artistMenu by remember{mutableStateOf(false)};var sort by remember{mutableStateOf("Título")};var ascending by remember{mutableStateOf(true)};var sortMenu by remember{mutableStateOf(false)}
 val artistOptions=listOf("Todas")+artists.map{it.name}.sorted()
 if(album!=null) LazyColumn(contentPadding=PaddingValues(16.dp)){item{TextButton(back){Icon(Icons.Default.ArrowBack,null);Text("Álbuns")};Card(Modifier.fillMaxWidth().aspectRatio(1f)){val art=album.artwork?:album.tracks.firstOrNull()?.artwork;if(!art.isNullOrBlank())AsyncImage(art,"Capa de "+album.title,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)else Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Icon(Icons.Default.Album,null,Modifier.size(90.dp))}};Text(album.title,style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=12.dp));Text(album.artist);SharedContent.albumUrl(album)?.let { link -> TextButton(onClick = { SharedContent.share(shareContext, album.artist + " — " + album.title, link) }) { Icon(Icons.Default.Share, null); Text(" Compartilhar álbum") } }};items(album.tracks,key={it.id}){t->OnlineTrackRow(t,favorites.contains(t.id),{play(t,album)},{user.toggleFavorite(t)},downloads,{buy(t,album)})}}
 else Column{
  Row(Modifier.fillMaxWidth().padding(horizontal=12.dp), verticalAlignment=Alignment.CenterVertically) {
   Text(catalogStatus, Modifier.weight(1f), style=MaterialTheme.typography.bodySmall)
   IconButton(onClick=refresh, enabled=!refreshing) { Icon(Icons.Default.Refresh, "Atualizar catálogo online") }
  }

  OutlinedTextField(query,{query=it},Modifier.fillMaxWidth().padding(12.dp),singleLine=true,label={Text(if (BuildConfig.ARTIST_FILTER.isBlank()) "Buscar músicas online" else "Buscar em ${if(BuildConfig.PROJECT_MODE=="SOLASIAS") "SOLASIAS" else BuildConfig.ARTIST_FILTER}")},leadingIcon={Icon(Icons.Default.Search,null)})
  if(mode=="Músicas"||mode=="Álbuns")Column(Modifier.padding(horizontal=12.dp)){Row(verticalAlignment=Alignment.CenterVertically){Box(Modifier.weight(1f)){OutlinedButton({artistMenu=true},modifier=Modifier.fillMaxWidth()){Text(if(artistFilter=="Todas")"Todos os artistas" else artistFilter,maxLines=1,overflow=TextOverflow.Ellipsis);Icon(Icons.Default.ArrowDropDown,null)};DropdownMenu(artistMenu,{artistMenu=false}){artistOptions.forEach{a->DropdownMenuItem(text={Text(if(a=="Todas")"Todos os artistas" else a)},onClick={artistFilter=a;artistMenu=false})}}};Spacer(Modifier.width(8.dp));Box(Modifier.weight(1f)){OutlinedButton({sortMenu=true},modifier=Modifier.fillMaxWidth()){Text("Ordenar: $sort",maxLines=1);Icon(Icons.Default.ArrowDropDown,null)};DropdownMenu(sortMenu,{sortMenu=false}){listOf("Título","Artista","Álbum","Data").forEach{o->DropdownMenuItem(text={Text(o)},onClick={sort=o;sortMenu=false})}}};IconButton({ascending=!ascending}){Icon(if(ascending)Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,if(ascending)"Crescente" else "Decrescente")}}}
  val modes=listOf("Músicas","Artistas","Álbuns");ScrollableTabRow(modes.indexOf(mode)){modes.forEach{m->Tab(mode==m,{mode=m},text={Text(m,maxLines=1)})}}
  val ft=tracks.filter{artistFilter=="Todas"||it.artist==artistFilter}.filter{query.isBlank()||listOf(it.title,it.artist,it.album).any{s->s.contains(query,true)}}.sortedWith(when(sort){"Artista"->compareBy(String.CASE_INSENSITIVE_ORDER){it.artist};"Álbum"->compareBy(String.CASE_INSENSITIVE_ORDER){it.album};"Data"->compareBy<Track>{it.dateMs?:0L};else->compareBy(String.CASE_INSENSITIVE_ORDER){it.title}}).let{if(ascending)it else it.reversed()}
  when(mode){"Músicas"->LazyColumn{items(ft,key={it.id}){t->val a=allAlbums.firstOrNull{x->x.title==t.album&&x.artist==t.artist};OnlineTrackRow(t,favorites.contains(t.id),{if(a!=null)play(t,a)},{user.toggleFavorite(t)},downloads,{buy(t,a)})}};"Artistas"->LazyVerticalGrid(columns=GridCells.Fixed(2),contentPadding=PaddingValues(10.dp)){gridItems(artists.filter{query.isBlank()||it.name.contains(query,true)},key={it.id}){a->val logo=ArtistLogos.forArtist(a.name) ?: a.albums.firstOrNull()?.artwork;Column(Modifier.padding(6.dp).clickable{artistFilter=a.name;mode="Músicas"}){Card(Modifier.fillMaxWidth().aspectRatio(1f)){if(logo != null && (!(logo is String) || logo.isNotBlank())) AsyncImage(logo,a.name,Modifier.fillMaxSize(),contentScale=ContentScale.Fit)else Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Icon(Icons.Default.Person,null,Modifier.size(60.dp))}};Text(a.name,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=6.dp));Text(a.albums.sumOf{it.tracks.size}.toString()+" faixas",style=MaterialTheme.typography.bodySmall)}}};else->{val chronologicalAlbums=AlbumFolderOrder.albums(allAlbums)
  val fa=(if(sort=="Data")chronologicalAlbums else allAlbums).filter{artistFilter=="Todas"||it.artist==artistFilter}.filter{query.isBlank()||it.title.contains(query,true)}.let{ if(sort=="Data")it else it.sortedWith(if(sort=="Artista")compareBy(String.CASE_INSENSITIVE_ORDER){a->a.artist}else compareBy(String.CASE_INSENSITIVE_ORDER){a->a.title}) }.let{if(ascending)it else it.reversed()};LazyColumn{items(fa,key={it.id}){a->val art=a.artwork?:a.tracks.firstOrNull()?.artwork;ListItem(headlineContent={Text(a.title,maxLines=1,overflow=TextOverflow.Ellipsis)},supportingContent={Text(a.artist+" • "+a.tracks.size+" faixas")},leadingContent={if(!art.isNullOrBlank())AsyncImage(art,null,Modifier.size(64.dp),contentScale=ContentScale.Crop)else Icon(Icons.Default.Album,null)},trailingContent={Row{IconButton(onClick={buy(null,a)}){Icon(Icons.Default.Download,"Baixar álbum")};Icon(Icons.Default.ChevronRight,null)}},modifier=Modifier.clickable{open(a)})}}}}
 }
}
@Composable
private fun CollectionsScreen(user: UserLibraryRepository, all: List<Track>, playback: PlaybackController, onPlay: (Track, List<Track>) -> Unit) {
    var name by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<UserPlaylist?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    val playlist = selected?.let { target -> user.playlists().find { it.id == target.id } }
    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        if (playlist != null) {
            item {
                TextButton(onClick = { selected = null }) { Icon(Icons.Default.ArrowBack, null); Text("Listas") }
                Text(playlist.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                val tracks = playlist.trackIds.mapNotNull { id -> all.find { it.id == id } }
                Button(onClick = { tracks.firstOrNull()?.let { onPlay(it, tracks) } }, enabled = tracks.isNotEmpty()) { Icon(Icons.Default.PlayArrow, null); Text(" Reproduzir") }
            }
            items(playlist.trackIds.mapNotNull { id -> all.find { it.id == id } }, key = { it.id }) { track ->
                ListItem(headlineContent = { Text(track.title) }, supportingContent = { Text(track.artist) },
                    trailingContent = { IconButton(onClick = { user.removeFromPlaylist(playlist.id, track.id); revision++ }) { Icon(Icons.Default.RemoveCircleOutline, null) } },
                    modifier = Modifier.clickable { val tracks=playlist.trackIds.mapNotNull { id->all.find { it.id==id } }; onPlay(track, tracks) })
            }
            item { Text("Adicionar faixas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 18.dp)) }
            items(all.filter { it.id !in playlist.trackIds }, key = { "add-" + it.id }) { track ->
                ListItem(
                    headlineContent = { Text(track.title) },
                    supportingContent = { Text(track.artist + " • " + track.album) },
                    leadingContent = { Icon(if (track.remote) Icons.Default.Cloud else Icons.Default.MusicNote, null) },
                    trailingContent = { IconButton(onClick = { user.addToPlaylist(playlist.id, track.id); revision++ }) { Icon(Icons.Default.AddCircleOutline, "Adicionar") } }
                )
            }
        } else {
            item {
                Text("Listas e filas", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, modifier = Modifier.weight(1f), label = { Text("Nova lista") })
                    IconButton(onClick = { if (name.isNotBlank()) { user.savePlaylist(UserPlaylist(System.currentTimeMillis().toString(), name)); name=""; revision++ } }) { Icon(Icons.Default.Add, null) }
                }
            }
            items(user.playlists(), key = { it.id }) { p ->
                ListItem(headlineContent = { Text(p.name) }, supportingContent = { Text("${p.trackIds.size} faixas") },
                    leadingContent = { Icon(Icons.Default.PlaylistPlay, null) },
                    trailingContent = { IconButton(onClick = { user.deletePlaylist(p.id); revision++ }) { Icon(Icons.Default.Delete, null) } },
                    modifier = Modifier.clickable { selected = p })
            }
            item {
                val ids=(0 until (playback.controller?.mediaItemCount?:0)).mapNotNull { playback.controller?.getMediaItemAt(it)?.mediaId }
                OutlinedButton(onClick={ if(ids.isNotEmpty()){user.saveQueue(MusicQueue(System.currentTimeMillis().toString(),"Fila salva",ids));revision++}},enabled=ids.isNotEmpty()){
                    Icon(Icons.Default.Save,null);Text(" Salvar fila atual")
                }
                Text("Filas salvas", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
            }
            items(user.queues(), key = { it.id }) { q ->
                val tracks=q.trackIds.mapNotNull{id->all.find{it.id==id}}
                ListItem(headlineContent={Text(q.name)},supportingContent={Text("${tracks.size} faixas")},leadingContent={Icon(Icons.Default.QueueMusic,null)},
                    trailingContent={IconButton(onClick={user.deleteQueue(q.id);revision++}){Icon(Icons.Default.Delete,null)}},
                    modifier=Modifier.clickable{tracks.firstOrNull()?.let{playback.play(it,tracks)}})
            }
        }
    }
}

@Composable
private fun MoreScreen(folders: List<Uri>, add: () -> Unit, remove: (Uri) -> Unit, refresh: () -> Unit, downloads: OfflineDownloadRepository, settings: SettingsRepository, playback: PlaybackController, onMyPurchases: () -> Unit, refreshing: Boolean, catalogStatus: String, automaticCheck: Boolean, setAutomaticCheck: (Boolean) -> Unit) {
    var speed by remember { mutableFloatStateOf(settings.speed) }
    var crossfade by remember { mutableIntStateOf(settings.crossfadeSeconds) }
    var downloadRevision by remember { mutableIntStateOf(0) }
    val downloadedCount = remember(downloadRevision) { downloads.downloadedTrackIds().size }
    val downloadedBytes = remember(downloadRevision) { downloads.totalBytes() }
    var confirmClear by remember { mutableStateOf(false) }
    val storageLabel = remember(downloadedBytes) { if (downloadedBytes < 1024L * 1024L) "${downloadedBytes / 1024L} KB" else String.format(java.util.Locale.getDefault(), "%.1f MB", downloadedBytes / (1024.0 * 1024.0)) }

    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        item {
            Text("Configurações", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text("Biblioteca local e catálogo online", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Atualize as músicas do aparelho e os álbuns de Solasias e theFrezza no Drive.", style = MaterialTheme.typography.bodySmall)
            Text(catalogStatus, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = add) { Text("Adicionar pasta") }
                OutlinedButton(onClick = refresh, enabled = !refreshing) { Icon(Icons.Default.Refresh, null); Text(if (refreshing) " Atualizando…" else " Atualizar biblioteca") }
            }
        }
        item {
            ListItem(
                headlineContent = { Text("Verificar atualizações automaticamente") },
                supportingContent = { Text("Após 30 segundos ao abrir o app. A biblioteca só muda com sua confirmação.") },
                trailingContent = { Switch(checked = automaticCheck, onCheckedChange = setAutomaticCheck) }
            )
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
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Text("Reprodução", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            ListItem(headlineContent = { Text("Letras e LRC") }, supportingContent = { Text("Compatível com letras embutidas e sincronizadas quando disponíveis.") }, leadingContent = { Icon(Icons.Default.Lyrics, null) })
            ListItem(headlineContent = { Text("Velocidade padrão") }, supportingContent = { Text(speed.toString() + "x") }, leadingContent = { Icon(Icons.Default.Speed, null) })
            Slider(value=speed,onValueChange={speed=it},onValueChangeFinished={settings.speed=speed;playback.setSpeed(speed)},valueRange=0.5f..2f,steps=5,modifier=Modifier.padding(horizontal=16.dp))
            ListItem(headlineContent = { Text("Crossfade") }, supportingContent = { Text(if(crossfade==0)"Desativado" else crossfade.toString() + " s") }, leadingContent = { Icon(Icons.Default.Equalizer, null) })
            Slider(value=crossfade.toFloat(),onValueChange={crossfade=it.toInt()},onValueChangeFinished={settings.crossfadeSeconds=crossfade},valueRange=0f..12f,steps=11,modifier=Modifier.padding(horizontal=16.dp))
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Text("Offline e streaming", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedButton(onClick = onMyPurchases) { Text("Minhas Compras") }
            ListItem(headlineContent = { Text("Downloads offline") }, supportingContent = { Text("$downloadedCount faixas • $storageLabel usados") }, leadingContent = { Icon(Icons.Default.Download, null) }, trailingContent = { if (downloadedCount > 0) TextButton(onClick = { confirmClear = true }) { Text("Limpar") } })
            ListItem(headlineContent = { Text("Streaming e downloads") }, supportingContent = { Text("O catálogo oficial é gratuito para ouvir. A compra permite baixar, com a mesma qualidade do streaming.") }, leadingContent = { Icon(Icons.Default.Cloud, null) })
            ListItem(headlineContent = { Text("Versão instalada") }, supportingContent = { Text("${BuildConfig.VERSION_NAME} • build ${BuildConfig.VERSION_CODE}") }, leadingContent = { Icon(Icons.Default.Info, null) })
        }
    }
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("Limpar downloads?") },
        text = { Text("Os $downloadedCount arquivos baixados serão removidos deste aparelho. As músicas continuarão disponíveis por streaming.") },
        confirmButton = { Button(onClick = { downloads.clearAll(); downloadRevision++; confirmClear = false }) { Text("Limpar") } },
        dismissButton = { OutlinedButton(onClick = { confirmClear = false }) { Text("Cancelar") } }
    )
}

@Composable
private fun OnlineTrackRow(track: Track, favorite: Boolean, play: () -> Unit, toggleFavorite: () -> Unit, downloads: OfflineDownloadRepository, buy: () -> Unit) {
    val shareContext = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember(track.id) { mutableStateOf(downloads.state(track)) }
    var error by remember(track.id) { mutableStateOf<String?>(null) }
    ListItem(
        headlineContent = { Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(track.artist + " • " + track.album + when(state){ DownloadState.DOWNLOADED -> " • Offline"; DownloadState.DOWNLOADING -> " • Baixando…"; else -> "" }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingContent = { if (!track.artwork.isNullOrBlank()) AsyncImage(model=track.artwork,contentDescription=null,modifier=Modifier.size(48.dp),contentScale=ContentScale.Crop) else Icon(Icons.Default.Cloud, null) },
        trailingContent = {
            Row {
                SharedContent.trackUrl(track)?.let { link -> IconButton(onClick = { SharedContent.share(shareContext, track.artist + " — " + track.title, link) }) { Icon(Icons.Default.Share, "Compartilhar música") } }
                IconButton(onClick = buy) { Icon(Icons.Default.Download, "Opções de download") }
                IconButton(onClick = toggleFavorite) { Icon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null) }
            }
        },
        modifier = Modifier.clickable(onClick = play)
    )
    if (error != null) Text(error!!, color=MaterialTheme.colorScheme.error, style=MaterialTheme.typography.bodySmall, modifier=Modifier.padding(horizontal=16.dp))
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun TrackRow(track: Track, favorite: Boolean, play: () -> Unit, toggleFavorite: () -> Unit, onEdit: (Track) -> Unit) {
    ListItem(
        headlineContent = { Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text("${track.artist} • ${track.album} • ${if (track.remote) "Online" else "Local"}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingContent = { if (!track.artwork.isNullOrBlank()) AsyncImage(model=track.artwork,contentDescription=null,modifier=Modifier.size(48.dp),contentScale=ContentScale.Crop) else Icon(if (track.remote) Icons.Default.Cloud else Icons.Default.MusicNote, null) },
        trailingContent = { IconButton(onClick = toggleFavorite) { Icon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null) } },
        modifier = Modifier.combinedClickable(onClick = play, onLongClick = { onEdit(track) })
    )
}

@Composable
private fun TrackMetadataEditor(track: Track, onDismiss: () -> Unit, onSave: (Map<String,String>) -> Unit) {
    var title by remember(track.id) { mutableStateOf(track.title) }
    var artist by remember(track.id) { mutableStateOf(track.artist) }
    var album by remember(track.id) { mutableStateOf(track.album) }
    var genre by remember(track.id) { mutableStateOf(track.genre.orEmpty()) }
    var number by remember(track.id) { mutableStateOf(track.trackNumber?.toString().orEmpty()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Editar música") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(title, { title = it }, label = { Text("Título") }, singleLine = true)
            OutlinedTextField(artist, { artist = it }, label = { Text("Artista") }, singleLine = true)
            OutlinedTextField(album, { album = it }, label = { Text("Álbum") }, singleLine = true)
            OutlinedTextField(genre, { genre = it }, label = { Text("Gênero") }, singleLine = true)
            OutlinedTextField(number, { number = it.filter(Char::isDigit) }, label = { Text("Número da faixa") }, singleLine = true)
        }
    }, confirmButton = { Button(onClick = {
        onSave(mapOf("title" to title, "artist" to artist, "album" to album, "genre" to genre, "trackNumber" to number))
    }, enabled = title.isNotBlank() && artist.isNotBlank()) { Text("Salvar") } },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
private fun PlayerTransportBar(playback: PlaybackController, playing: Boolean) {
    Surface(tonalElevation = 6.dp) {
        Row(Modifier.fillMaxWidth().height(72.dp).padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceEvenly) {
            IconButton(onClick=playback::shuffle,modifier=Modifier.size(48.dp)){Icon(Icons.Default.Shuffle,"Aleatório",Modifier.size(28.dp))}
            IconButton(onClick=playback::previous,modifier=Modifier.size(56.dp)){Icon(Icons.Default.SkipPrevious,"Anterior",Modifier.size(38.dp))}
            FilledIconButton(onClick=playback::toggle,modifier=Modifier.size(64.dp)){Icon(if(playing) Icons.Default.Pause else Icons.Default.PlayArrow,"Reproduzir",Modifier.size(44.dp))}
            IconButton(onClick=playback::next,modifier=Modifier.size(56.dp)){Icon(Icons.Default.SkipNext,"Próxima",Modifier.size(38.dp))}
            IconButton(onClick=playback::repeat,modifier=Modifier.size(48.dp)){Icon(Icons.Default.Repeat,"Repetição",Modifier.size(28.dp))}
        }
    }
}

@Composable
private fun MiniPlayer(track: Track?, playing: Boolean, toggle: () -> Unit, expand: () -> Unit) {
    Surface(tonalElevation = 6.dp, modifier = Modifier.clickable(onClick = expand)) {
        Row(modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!track?.artwork.isNullOrBlank()) AsyncImage(model=track?.artwork,contentDescription=null,modifier=Modifier.size(44.dp),contentScale=ContentScale.Crop) else Icon(Icons.Default.Album, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(track?.title ?: "Nada tocando", maxLines = 1, fontWeight = FontWeight.SemiBold)
                Text(track?.artist ?: "Selecione uma faixa", maxLines = 1, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = toggle, enabled = track != null) { Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null) }
        }
    }
}
