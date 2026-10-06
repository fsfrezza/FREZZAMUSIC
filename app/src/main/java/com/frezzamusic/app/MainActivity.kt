package com.frezzamusic.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
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
import com.frezzamusic.app.ui.theme.FrezzaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var folders: FolderMusicRepository
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private var mediaPermissionResult: ((Boolean) -> Unit)? = null
    private val mediaPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { mediaPermissionResult?.invoke(it) }
    private var folderAdded: (() -> Unit)? = null
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
        folders = FolderMusicRepository(this)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        setContent {
            FrezzaTheme {
                FrezzaMusicApp(
                    repo = folders,
                    pickFolder = { callback -> folderAdded = callback; picker.launch(null) },
                    pickArtistImage = { callback -> artistImagePicked = callback; imagePicker.launch(arrayOf("image/*")) },
                    requestMediaAccess = { callback -> mediaPermissionResult=callback; mediaPermission.launch(if(Build.VERSION.SDK_INT>=33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE) }
                )
            }
        }
    }
}

enum class AppTab { HOME, LIBRARY, ONLINE, PLAYLISTS, NEWS, MORE }

@Composable
fun FrezzaMusicApp(repo: FolderMusicRepository, pickFolder: ((() -> Unit)) -> Unit, pickArtistImage: (((Uri) -> Unit)) -> Unit, requestMediaAccess: ((Boolean) -> Unit) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val user = remember { UserLibraryRepository(context) }
    val streamResolver = remember { DevelopmentDriveStreamResolver() }
    val playback = remember { PlaybackController(context, streamResolver, user) }
    val downloads = remember { OfflineDownloadRepository(context, streamResolver) }
    val settings = remember { SettingsRepository(context) }

    var changeCounter by remember { mutableIntStateOf(0) }
    var tab by remember { mutableStateOf(AppTab.HOME) }
    var localTracks by remember { mutableStateOf(emptyList<Track>()) }
    var artists by remember { mutableStateOf(emptyList<Artist>()) }
    var roots by remember { mutableStateOf(repo.folders()) }
    var selectedAlbum by remember { mutableStateOf<Album?>(null) }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var playerExpanded by remember { mutableStateOf(false) }
    val onboardingPrefs=remember{context.getSharedPreferences("onboarding",android.content.Context.MODE_PRIVATE)}
    var showMediaPrompt by remember{mutableStateOf(!onboardingPrefs.getBoolean("media_prompt_done",false))}
    var deviceScan by remember{mutableStateOf(onboardingPrefs.getBoolean("scan_device",false))}

    DisposableEffect(Unit) {
        playback.onChanged = { changeCounter++ }
        playback.connect()
        onDispose { playback.release() }
    }

    suspend fun refreshLibrary() {
        loading = true
        roots = repo.folders()
        localTracks = withContext(Dispatchers.IO) { val manual=repo.scan(); val device=if(deviceScan) repo.scanDevice() else emptyList(); repo.mergeWithoutDuplicates(manual,device) }
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
        if (changeCounter > 0) refreshLibrary()
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
                        AppTab.NEWS to Icons.Default.Newspaper,
                        AppTab.MORE to Icons.Default.MoreHoriz
                    )
                    tabs.forEach { (item, icon) ->
                        NavigationBarItem(
                            selected = tab == item,
                            onClick = { tab = item; selectedAlbum = null; playerExpanded = false },
                            icon = { Icon(icon, contentDescription = null) },
                            label = { Text(when(item){ AppTab.HOME -> "Início"; AppTab.LIBRARY -> "Locais"; AppTab.ONLINE -> "Externas"; AppTab.PLAYLISTS -> "Listas"; AppTab.NEWS -> "Novidades"; AppTab.MORE -> "Mais" }, maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (tab) {
                AppTab.HOME -> HomeScreen(localTracks, remoteTracks, user, allTracks, { playback.play(it, allTracks) }, { track -> playback.play(track, allTracks, playback.resumePosition(track.id)) }, playback.lastTrackId()) { tab = AppTab.ONLINE }
                AppTab.LIBRARY -> LibraryScreen(localTracks, query, { query = it }, { playback.play(it, localTracks) }, user, pickArtistImage)
                AppTab.ONLINE -> OnlineScreen(artists, selectedAlbum, { selectedAlbum = it }, { selectedAlbum = null }, { track, album -> playback.play(track, album.tracks) }, user, downloads)
                AppTab.PLAYLISTS -> CollectionsScreen(user, allTracks, playback)
                AppTab.NEWS -> NewsScreen()
                AppTab.MORE -> MoreScreen(roots, { pickFolder { changeCounter++ } }, { repo.remove(it); changeCounter++ }, { changeCounter++; loading=true }, downloads, settings, playback)
            }
            if (playerExpanded) {
                Surface(Modifier.fillMaxSize()) {
                    FullPlayer(currentTrack, playback, user, onClose = { playerExpanded = false })
                }
            }
            if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            if(showMediaPrompt) AlertDialog(onDismissRequest={},title={Text("Buscar mídias no celular?")},text={Text("Deseja que o FREZZAMUSIC procure as músicas presentes no celular e as adicione à biblioteca? Se preferir, você poderá continuar adicionando pastas manualmente em Mais.")},confirmButton={Button(onClick={requestMediaAccess{granted->onboardingPrefs.edit().putBoolean("media_prompt_done",true).putBoolean("scan_device",granted).apply();deviceScan=granted;showMediaPrompt=false;if(granted)changeCounter++}}){Text("Buscar mídias")}},dismissButton={OutlinedButton(onClick={onboardingPrefs.edit().putBoolean("media_prompt_done",true).putBoolean("scan_device",false).apply();showMediaPrompt=false}){Text("Agora não")}})
        }
    }
}

@Composable
private fun HomeScreen(local: List<Track>, remote: List<Track>, user: UserLibraryRepository, all: List<Track>, play: (Track) -> Unit, resume: (Track) -> Unit, lastTrackId: String?, online: () -> Unit) {
    val favorites = user.favorites()
    val recent = user.history().mapNotNull { h -> all.find { it.id == h.trackId } }.take(8)
    val favoriteTracks = all.filter { it.id in favorites }.take(8)
    val lastTrack = lastTrackId?.let { id -> all.find { it.id == id } }
    val onlineAlbums = remote.groupBy { it.artist to it.album }.values.take(6)
    LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            if (BuildConfig.PROJECT_MODE == "FREZZAMUSIC") {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(painterResource(com.frezzamusic.app.R.drawable.frezzamusic_logo), contentDescription="FREZZAMUSIC", tint=Color.Unspecified, modifier=Modifier.size(132.dp))
                    Text("FREZZAMUSIC", style=MaterialTheme.typography.headlineMedium, fontWeight=FontWeight.Black)
                }
            } else Text(BuildConfig.ARTIST_FILTER, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Text("${local.size} locais • ${remote.size} online • ${favorites.size} favoritas")
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
            items(recent, key = { "recent-"+it.id }) { track -> TrackRow(track, favorites.contains(track.id), { play(track) }) { user.toggleFavorite(track.id) } }
        }
        if (favoriteTracks.isNotEmpty()) {
            item { Text("Favoritas", style=MaterialTheme.typography.titleMedium, fontWeight=FontWeight.Bold, modifier=Modifier.padding(top=6.dp)) }
            items(favoriteTracks, key = { "fav-"+it.id }) { track -> TrackRow(track, true, { play(track) }) { user.toggleFavorite(track.id) } }
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
        item {
            OutlinedButton(onClick = online, modifier=Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Cloud, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Abrir FREZZAMUSIC Online")
            }
        }
    }
}

@Composable
private fun LibraryScreen(tracks: List<Track>, query: String, setQuery: (String) -> Unit, play: (Track) -> Unit, user: UserLibraryRepository, pickArtistImage: (((Uri) -> Unit)) -> Unit) {
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
        "Músicas"->items(filtered,key={it.id}){t->TrackRow(t,user.favorites().contains(t.id),{play(t)}){user.toggleFavorite(t.id)}}
        "Artistas"->items(tracks.filter{query.isBlank()||it.artist.contains(query,true)}.groupBy{it.artist}.toList(),key={it.first}){(n,l)->val custom=user.artistImage(n);ElevatedCard(Modifier.padding(8.dp).fillMaxWidth().clickable{artistFilter=n;mode="Músicas"}){Row(Modifier.padding(10.dp),verticalAlignment=Alignment.CenterVertically){Card(Modifier.size(92.dp)){if(custom!=null)AsyncImage(custom,n,Modifier.fillMaxSize(),contentScale=ContentScale.Fit)else Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Icon(Icons.Default.Person,null,Modifier.size(50.dp))}};Column(Modifier.weight(1f).padding(start=12.dp)){Text(n,fontWeight=FontWeight.Bold);Text("${l.size} faixas");TextButton(onClick={pickArtistImage{uri->user.setArtistImage(n,uri.toString())}}){Icon(Icons.Default.Image,null);Text(" Escolher logo")};if(custom!=null)TextButton(onClick={user.clearArtistImage(n)}){Text("Remover imagem")}}}}}
        "Álbuns"->items(filtered.groupBy{it.artist+" / "+it.album}.values.toList().let{groups->when(sort){"Artista"->groups.sortedBy{it.first().artist.lowercase()};"Data"->groups.sortedBy{it.maxOfOrNull{x->x.dateMs?:0L}?:0L};else->groups.sortedBy{it.first().album.lowercase()}}.let{if(ascending)it else it.reversed()}},key={it.first().artist+"/"+it.first().album}){l->val f=l.first();ListItem(headlineContent={Text(f.album,maxLines=1,overflow=TextOverflow.Ellipsis)},supportingContent={Text(f.artist+" • "+l.size+" faixas")},leadingContent={if(!f.artwork.isNullOrBlank())AsyncImage(f.artwork,null,Modifier.size(56.dp),contentScale=ContentScale.Crop)else Icon(Icons.Default.Album,null)},modifier=Modifier.clickable{play(f)})}
        else->{
          val folders=tracks.filter{query.isBlank()||it.folder.orEmpty().contains(query,true)||it.title.contains(query,true)}.groupBy{it.folder?.takeIf{s->s.isNotBlank()}?:"Sem pasta identificada"}.toSortedMap(String.CASE_INSENSITIVE_ORDER)
          folders.forEach{(folder,folderTracks)->
            item(key="folder-header-"+folder){ListItem(headlineContent={Text(folder,fontWeight=FontWeight.Bold)},supportingContent={Text("${folderTracks.size} faixas")},leadingContent={Icon(Icons.Default.Folder,null)})}
            items(folderTracks,key={"folder-"+folder+"-"+it.id}){t->TrackRow(t,user.favorites().contains(t.id),{play(t)}){user.toggleFavorite(t.id)}}
          }
        }
      }}
    }
}
@Composable
private fun OnlineScreen(artists: List<Artist>, album: Album?, open: (Album) -> Unit, back: () -> Unit, play: (Track, Album) -> Unit, user: UserLibraryRepository, downloads: OfflineDownloadRepository) {
 val allAlbums=artists.flatMap{it.albums}; val tracks=allAlbums.flatMap{it.tracks}; var mode by remember{mutableStateOf("Álbuns")};var query by remember{mutableStateOf("")};var artistFilter by remember{mutableStateOf("Todas")};var artistMenu by remember{mutableStateOf(false)};var sort by remember{mutableStateOf("Título")};var ascending by remember{mutableStateOf(true)};var sortMenu by remember{mutableStateOf(false)}
 val artistOptions=listOf("Todas")+artists.map{it.name}.sorted()
 if(album!=null) LazyColumn(contentPadding=PaddingValues(16.dp)){item{TextButton(back){Icon(Icons.Default.ArrowBack,null);Text("Álbuns")};Card(Modifier.fillMaxWidth().aspectRatio(1f)){val art=album.artwork?:album.tracks.firstOrNull()?.artwork;if(!art.isNullOrBlank())AsyncImage(art,"Capa de "+album.title,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)else Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Icon(Icons.Default.Album,null,Modifier.size(90.dp))}};Text(album.title,style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=12.dp));Text(album.artist)};items(album.tracks,key={it.id}){t->OnlineTrackRow(t,user.favorites().contains(t.id),{play(t,album)},{user.toggleFavorite(t.id)},downloads)}}
 else Column{
  OutlinedTextField(query,{query=it},Modifier.fillMaxWidth().padding(12.dp),singleLine=true,label={Text("Buscar mídias externas")},leadingIcon={Icon(Icons.Default.Search,null)})
  if(mode=="Músicas"||mode=="Álbuns")Column(Modifier.padding(horizontal=12.dp)){Row(verticalAlignment=Alignment.CenterVertically){Box(Modifier.weight(1f)){OutlinedButton({artistMenu=true},modifier=Modifier.fillMaxWidth()){Text(if(artistFilter=="Todas")"Todos os artistas" else artistFilter,maxLines=1,overflow=TextOverflow.Ellipsis);Icon(Icons.Default.ArrowDropDown,null)};DropdownMenu(artistMenu,{artistMenu=false}){artistOptions.forEach{a->DropdownMenuItem(text={Text(if(a=="Todas")"Todos os artistas" else a)},onClick={artistFilter=a;artistMenu=false})}}};Spacer(Modifier.width(8.dp));Box(Modifier.weight(1f)){OutlinedButton({sortMenu=true},modifier=Modifier.fillMaxWidth()){Text("Ordenar: $sort",maxLines=1);Icon(Icons.Default.ArrowDropDown,null)};DropdownMenu(sortMenu,{sortMenu=false}){listOf("Título","Artista","Álbum","Data").forEach{o->DropdownMenuItem(text={Text(o)},onClick={sort=o;sortMenu=false})}}};IconButton({ascending=!ascending}){Icon(if(ascending)Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,if(ascending)"Crescente" else "Decrescente")}}}
  val modes=listOf("Músicas","Artistas","Álbuns");ScrollableTabRow(modes.indexOf(mode)){modes.forEach{m->Tab(mode==m,{mode=m},text={Text(m,maxLines=1)})}}
  val ft=tracks.filter{artistFilter=="Todas"||it.artist==artistFilter}.filter{query.isBlank()||listOf(it.title,it.artist,it.album).any{s->s.contains(query,true)}}.sortedWith(when(sort){"Artista"->compareBy(String.CASE_INSENSITIVE_ORDER){it.artist};"Álbum"->compareBy(String.CASE_INSENSITIVE_ORDER){it.album};"Data"->compareBy<Track>{it.dateMs?:0L};else->compareBy(String.CASE_INSENSITIVE_ORDER){it.title}}).let{if(ascending)it else it.reversed()}
  when(mode){"Músicas"->LazyColumn{items(ft,key={it.id}){t->val a=allAlbums.firstOrNull{x->x.title==t.album&&x.artist==t.artist};OnlineTrackRow(t,user.favorites().contains(t.id),{if(a!=null)play(t,a)},{user.toggleFavorite(t.id)},downloads)}};"Artistas"->LazyVerticalGrid(columns=GridCells.Fixed(2),contentPadding=PaddingValues(10.dp)){gridItems(artists.filter{query.isBlank()||it.name.contains(query,true)},key={it.id}){a->val logo=ArtistLogos.forArtist(a.name) ?: a.albums.firstOrNull()?.artwork;Column(Modifier.padding(6.dp).clickable{artistFilter=a.name;mode="Músicas"}){Card(Modifier.fillMaxWidth().aspectRatio(1f)){if(logo != null && (!(logo is String) || logo.isNotBlank())) AsyncImage(logo,a.name,Modifier.fillMaxSize(),contentScale=ContentScale.Fit)else Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Icon(Icons.Default.Person,null,Modifier.size(60.dp))}};Text(a.name,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=6.dp));Text(a.albums.sumOf{it.tracks.size}.toString()+" faixas",style=MaterialTheme.typography.bodySmall)}}};else->{val fa=allAlbums.filter{artistFilter=="Todas"||it.artist==artistFilter}.filter{query.isBlank()||it.title.contains(query,true)}.sortedWith(when(sort){"Artista"->compareBy(String.CASE_INSENSITIVE_ORDER){it.artist};"Data"->compareBy<Album>{it.releaseDate?:""};else->compareBy(String.CASE_INSENSITIVE_ORDER){it.title}}).let{if(ascending)it else it.reversed()};LazyColumn{items(fa,key={it.id}){a->val art=a.artwork?:a.tracks.firstOrNull()?.artwork;ListItem(headlineContent={Text(a.title,maxLines=1,overflow=TextOverflow.Ellipsis)},supportingContent={Text(a.artist+" • "+a.tracks.size+" faixas")},leadingContent={if(!art.isNullOrBlank())AsyncImage(art,null,Modifier.size(64.dp),contentScale=ContentScale.Crop)else Icon(Icons.Default.Album,null)},trailingContent={Icon(Icons.Default.ChevronRight,null)},modifier=Modifier.clickable{open(a)})}}}}
 }
}
@Composable
private fun CollectionsScreen(user: UserLibraryRepository, all: List<Track>, playback: PlaybackController) {
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
                Button(onClick = { tracks.firstOrNull()?.let { playback.play(it, tracks) } }, enabled = tracks.isNotEmpty()) { Icon(Icons.Default.PlayArrow, null); Text(" Reproduzir") }
            }
            items(playlist.trackIds.mapNotNull { id -> all.find { it.id == id } }, key = { it.id }) { track ->
                ListItem(headlineContent = { Text(track.title) }, supportingContent = { Text(track.artist) },
                    trailingContent = { IconButton(onClick = { user.removeFromPlaylist(playlist.id, track.id); revision++ }) { Icon(Icons.Default.RemoveCircleOutline, null) } },
                    modifier = Modifier.clickable { val tracks=playlist.trackIds.mapNotNull { id->all.find { it.id==id } }; playback.play(track, tracks) })
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
private fun NewsScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val identity = remember { ProjectIdentities.forMode(BuildConfig.PROJECT_MODE) }
    val repository = remember { ReleaseRepository() }
    var news by remember { mutableStateOf(repository.announcements(BuildConfig.PROJECT_MODE)) }
    var refreshing by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        news = repository.liveAnnouncements(BuildConfig.PROJECT_MODE)
        refreshing = false
    }
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
            if(refreshing) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top=8.dp))
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
private fun MoreScreen(folders: List<Uri>, add: () -> Unit, remove: (Uri) -> Unit, refresh: () -> Unit, downloads: OfflineDownloadRepository, settings: SettingsRepository, playback: PlaybackController) {
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
            Text("Biblioteca local", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Pastas usadas para localizar músicas armazenadas neste aparelho.", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = add) { Text("Adicionar pasta") }
                OutlinedButton(onClick = refresh) { Icon(Icons.Default.Refresh, null); Text(" Atualizar biblioteca") }
            }
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
            ListItem(headlineContent = { Text("Downloads offline") }, supportingContent = { Text("$downloadedCount faixas • $storageLabel usados") }, leadingContent = { Icon(Icons.Default.Download, null) }, trailingContent = { if (downloadedCount > 0) TextButton(onClick = { confirmClear = true }) { Text("Limpar") } })
            ListItem(headlineContent = { Text("Streaming e downloads") }, supportingContent = { Text("O catálogo oficial é livre para ouvir. Downloads em alta qualidade serão liberados por contribuição/licença.") }, leadingContent = { Icon(Icons.Default.Cloud, null) })
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
private fun OnlineTrackRow(track: Track, favorite: Boolean, play: () -> Unit, toggleFavorite: () -> Unit, downloads: OfflineDownloadRepository) {
    val scope = rememberCoroutineScope()
    var state by remember(track.id) { mutableStateOf(downloads.state(track)) }
    var error by remember(track.id) { mutableStateOf<String?>(null) }
    ListItem(
        headlineContent = { Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(track.artist + " • " + track.album + when(state){ DownloadState.DOWNLOADED -> " • Offline"; DownloadState.DOWNLOADING -> " • Baixando…"; else -> "" }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingContent = { if (!track.artwork.isNullOrBlank()) AsyncImage(model=track.artwork,contentDescription=null,modifier=Modifier.size(48.dp),contentScale=ContentScale.Crop) else Icon(Icons.Default.Cloud, null) },
        trailingContent = {
            Row {
                if (track.canDownload) IconButton(onClick = {
                    when(state) {
                        DownloadState.DOWNLOADED -> { downloads.remove(track); state=DownloadState.NOT_DOWNLOADED }
                        DownloadState.DOWNLOADING -> Unit
                        else -> scope.launch { state=DownloadState.DOWNLOADING; error=null; val result=downloads.download(track); state=if(result.isSuccess) DownloadState.DOWNLOADED else DownloadState.FAILED; error=result.exceptionOrNull()?.message }
                    }
                }) { Icon(when(state){ DownloadState.DOWNLOADED -> Icons.Default.Delete; DownloadState.DOWNLOADING -> Icons.Default.HourglassTop; else -> Icons.Default.Download }, if(state==DownloadState.DOWNLOADED) "Remover download" else "Baixar") }
                IconButton(onClick = toggleFavorite) { Icon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null) }
            }
        },
        modifier = Modifier.clickable(onClick = play)
    )
    if (error != null) Text(error!!, color=MaterialTheme.colorScheme.error, style=MaterialTheme.typography.bodySmall, modifier=Modifier.padding(horizontal=16.dp))
}

@Composable
private fun TrackRow(track: Track, favorite: Boolean, play: () -> Unit, toggleFavorite: () -> Unit) {
    ListItem(
        headlineContent = { Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text("${track.artist} • ${track.album} • ${if (track.remote) "Online" else "Local"}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingContent = { if (!track.artwork.isNullOrBlank()) AsyncImage(model=track.artwork,contentDescription=null,modifier=Modifier.size(48.dp),contentScale=ContentScale.Crop) else Icon(if (track.remote) Icons.Default.Cloud else Icons.Default.MusicNote, null) },
        trailingContent = { IconButton(onClick = toggleFavorite) { Icon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null) } },
        modifier = Modifier.clickable(onClick = play)
    )
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
