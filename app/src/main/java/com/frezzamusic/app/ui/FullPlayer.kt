package com.frezzamusic.app.ui
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Path
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import com.frezzamusic.app.data.LrcParser
import com.frezzamusic.app.model.Track
import com.frezzamusic.app.player.PlaybackController
import com.frezzamusic.app.player.PlaybackService
import com.frezzamusic.app.support.SupportPricing
import kotlinx.coroutines.delay

@Composable fun FullPlayer(track:Track?,pc:PlaybackController,user:com.frezzamusic.app.data.UserLibraryRepository,requestVisualizerAccess:((Boolean)->Unit)->Unit,onClose:()->Unit){
 val p=pc.controller; var page by remember{mutableIntStateOf(0)}; var pos by remember{mutableLongStateOf(p?.currentPosition?:0L)}; var support by remember{mutableStateOf(false)}; var playlistPicker by remember{mutableStateOf(false)}
 LaunchedEffect(p?.isPlaying,p?.currentMediaItemIndex){while(true){pos=p?.currentPosition?:0L;delay(500)}}
 val dur=(p?.duration?:0L).coerceAtLeast(0L)
 Column(Modifier.fillMaxSize().padding(horizontal=20.dp,vertical=12.dp),horizontalAlignment=Alignment.CenterHorizontally){
  var favoriteRevision by remember{mutableIntStateOf(0)};val favorite=track?.id?.let{user.favorites().contains(it)}==true
  Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){IconButton(onClose){Icon(Icons.Default.KeyboardArrowDown,"Fechar")};Text("Tocando agora",Modifier.weight(1f),fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis);IconButton(onClick={track?.let{user.toggleFavorite(it.id);favoriteRevision++}},enabled=track!=null){Icon(if(favorite)Icons.Default.Favorite else Icons.Default.FavoriteBorder,"Favorita")};IconButton(onClick={playlistPicker=true},enabled=track!=null){Icon(Icons.Default.PlaylistAdd,"Adicionar à playlist")};if(track?.remote==true)IconButton(onClick={support=true}){Icon(Icons.Default.Download,"Apoiar e baixar")}}
  TabRow(page){Tab(page==0,{page=0},text={Text("Tocar",maxLines=1)});Tab(page==1,{page=1},text={Text("Letras")});Tab(page==2,{page=2},text={Text("Fila")});Tab(page==3,{page=3},text={Text("Áudio")})}
  when(page){0->PlayerPage(track,pc,pos,dur){pos=it};1->LyricsPage(track,pos);2->QueuePage(pc);else->AudioPage(pc,requestVisualizerAccess)}
 }
 if(playlistPicker&&track!=null)AlertDialog(onDismissRequest={playlistPicker=false},title={Text("Adicionar à lista")},text={Column{val playlists=user.playlists();if(playlists.isEmpty())Text("Nenhuma lista criada.") else playlists.forEach{pl->TextButton(onClick={user.addToPlaylist(pl.id,track.id);playlistPicker=false},modifier=Modifier.fillMaxWidth()){Text(pl.name)}}}},confirmButton={TextButton({playlistPicker=false}){Text("Fechar")}})
 if(support)AlertDialog(onDismissRequest={support=false},icon={Icon(Icons.Default.Favorite,null)},title={Text("Gostou desta música? Apoie o projeto")},text={Text("Todas as músicas podem ser ouvidas gratuitamente. Sua contribuição ajuda a manter o aplicativo e a produção de novas músicas. Como agradecimento, o download em alta qualidade será liberado quando o sistema de contribuições estiver ativo.\n\nFaixa: ${SupportPricing.formatBrl(track?.downloadPriceBrl ?: SupportPricing.TRACK_PRICE_BRL)}. Álbuns: ${SupportPricing.formatBrl(SupportPricing.albumPrice(8))} a ${SupportPricing.formatBrl(SupportPricing.albumPrice(17))}, conforme a quantidade de faixas.")},confirmButton={TextButton({support=false}){Text("Entendi")}},dismissButton={TextButton({support=false}){Text("Agora não")}})
}
@Composable private fun PlayerPage(t:Track?,pc:PlaybackController,pos:Long,dur:Long,setPos:(Long)->Unit){val p=pc.controller;Column(Modifier.fillMaxSize(),horizontalAlignment=Alignment.CenterHorizontally){Spacer(Modifier.height(8.dp));if(!t?.artwork.isNullOrBlank())Card(Modifier.fillMaxWidth().heightIn(max=360.dp).aspectRatio(1f)){AsyncImage(model=t?.artwork,contentDescription="Capa de ${t?.album.orEmpty()}",modifier=Modifier.fillMaxSize(),contentScale=ContentScale.Crop)} else Card(Modifier.fillMaxWidth().heightIn(max=360.dp).aspectRatio(1f)){Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Icon(Icons.Default.Album,null,Modifier.size(190.dp),tint=MaterialTheme.colorScheme.primary)}};Spacer(Modifier.height(8.dp));VisualizerView();Spacer(Modifier.height(8.dp));Text(t?.title?:"Nada tocando",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold,maxLines=2,overflow=TextOverflow.Ellipsis);Text(t?.artist?:"",style=MaterialTheme.typography.titleMedium,maxLines=1,overflow=TextOverflow.Ellipsis);Text(t?.album?:"",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis);Slider(pos.coerceAtMost(dur.coerceAtLeast(1)).toFloat(),{setPos(it.toLong())},onValueChangeFinished={pc.seek(pos)},valueRange=0f..dur.coerceAtLeast(1).toFloat());Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(fmt(pos));Text("-"+fmt((dur-pos).coerceAtLeast(0)))};Surface(tonalElevation=4.dp,shape=MaterialTheme.shapes.large,modifier=Modifier.fillMaxWidth().height(92.dp)){Row(Modifier.fillMaxSize().padding(horizontal=6.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceEvenly){IconButton({pc.shuffle()},Modifier.size(56.dp)){Icon(Icons.Default.Shuffle,"Aleatório",Modifier.size(30.dp))};IconButton({pc.previous()},Modifier.size(68.dp)){Icon(Icons.Default.SkipPrevious,"Anterior",Modifier.size(46.dp))};FilledIconButton({pc.toggle()},Modifier.size(76.dp)){Icon(if(p?.isPlaying==true)Icons.Default.Pause else Icons.Default.PlayArrow,"Reproduzir",Modifier.size(50.dp))};IconButton({pc.next()},Modifier.size(68.dp)){Icon(Icons.Default.SkipNext,"Próxima",Modifier.size(46.dp))};IconButton({pc.repeat()},Modifier.size(56.dp)){Icon(Icons.Default.Repeat,"Repetição",Modifier.size(30.dp))}}};Spacer(Modifier.height(8.dp))}}
@Composable private fun LyricsPage(t:Track?,pos:Long){val lrc=t?.lrc;var offset by remember(t?.id){mutableLongStateOf(0L)};if(!lrc.isNullOrBlank()){val lines=remember(lrc){LrcParser.parse(lrc)};val current=LrcParser.current(lines,(pos+offset).coerceAtLeast(0));val state=androidx.compose.foundation.lazy.rememberLazyListState();LaunchedEffect(current){if(current>=0)state.animateScrollToItem(current)};Column(Modifier.fillMaxSize()){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically){IconButton({offset-=500}){Icon(Icons.Default.Remove,"-0,5 s")};Text("Sincronia "+(if(offset>=0) "+" else "")+(offset/1000.0)+"s");IconButton({offset+=500}){Icon(Icons.Default.Add,"+0,5 s")}};LazyColumn(state=state,modifier=Modifier.fillMaxSize(),contentPadding=PaddingValues(vertical=24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){itemsIndexed(lines){i,line->Text(line.text,style=if(i==current)MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,fontWeight=if(i==current)FontWeight.Bold else FontWeight.Normal,color=if(i==current)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)}}}}else Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Text(t?.lyrics?:"Nenhuma letra disponível para esta faixa.",Modifier.padding(24.dp))}}
@Composable private fun QueuePage(pc:PlaybackController){val p=pc.controller;val entries=(0 until (p?.mediaItemCount?:0)).toList();LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(vertical=12.dp)){items(entries){i->val item=p?.getMediaItemAt(i);ListItem(headlineContent={Text(item?.mediaMetadata?.title?.toString()?:"Faixa")},supportingContent={Text(item?.mediaMetadata?.artist?.toString().orEmpty())},leadingContent={Text((i+1).toString())},trailingContent={if(i==p?.currentMediaItemIndex)Icon(Icons.Default.GraphicEq,"Tocando")},modifier=Modifier.clickable{p?.seekToDefaultPosition(i);p?.play()})};if(entries.isEmpty())item{Text("A fila está vazia.",Modifier.padding(24.dp))}}}
@Composable private fun VisualizerView(){
 var values by remember{mutableStateOf(emptyList<Int>())};var enabled by remember{mutableStateOf(PlaybackService.visualizerEnabled)}
 LaunchedEffect(Unit){while(true){enabled=PlaybackService.visualizerEnabled;values=PlaybackService.spectrum;delay(35)}}
 if(enabled) Row(Modifier.fillMaxWidth().height(58.dp),horizontalArrangement=Arrangement.spacedBy(3.dp),verticalAlignment=Alignment.Bottom){
  val bars=if(values.isNotEmpty())values else List(24){0}
  bars.forEach{v->val level=(v.coerceIn(0,128)/128f);Box(Modifier.weight(1f).height((4f+level*52f).dp).background(MaterialTheme.colorScheme.primary))}
 }
}
@Composable private fun AudioPage(pc:PlaybackController,requestVisualizerAccess:((Boolean)->Unit)->Unit){
 var refresh by remember{mutableIntStateOf(0)}; val available=PlaybackService.equalizerAvailable; val presets=PlaybackService.presetNames;val player=pc.controller;var volume by remember{mutableFloatStateOf(player?.volume?:1f)};var visualizerEnabled by remember{mutableStateOf(PlaybackService.visualizerEnabled)}
 LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(vertical=16.dp)){
  item{Text("Volume",fontWeight=FontWeight.Bold);Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.VolumeDown,null);Slider(volume,{volume=it;player?.volume=it},Modifier.weight(1f),valueRange=0f..1f);Icon(Icons.Default.VolumeUp,null)};Text("Velocidade",fontWeight=FontWeight.Bold);Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf(.75f,1f,1.25f,1.5f).forEach{s->FilterChip(selected=kotlin.math.abs((player?.playbackParameters?.speed?:1f)-s)<.01f,onClick={pc.setSpeed(s);refresh++},label={Text("${s}x")})}};Text("Timer",fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=8.dp));Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf(15,30,60).forEach{m->AssistChip(onClick={pc.sleepTimer(m)},label={Text("$m min")})}};Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("Espectro em tempo real",fontWeight=FontWeight.Bold);Text("Pode ser desativado para reduzir processamento.",style=MaterialTheme.typography.bodySmall)};Switch(checked=visualizerEnabled,onCheckedChange={enabled->
 if(!enabled){visualizerEnabled=false;PlaybackService.setVisualizerEnabled(false);refresh++}
 else requestVisualizerAccess{granted->visualizerEnabled=granted;PlaybackService.setVisualizerEnabled(granted);refresh++}
})};HorizontalDivider();Spacer(Modifier.height(12.dp));Text("Equalizador",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);Text(if(available)"Ligado à sessão de áudio atual." else "O equalizador será disponibilizado quando o dispositivo abrir uma sessão de áudio compatível.",style=MaterialTheme.typography.bodyMedium);Spacer(Modifier.height(12.dp))}
  if(available)items(presets.indices.toList()){i->ListItem(headlineContent={Text(presets[i])},leadingContent={Icon(Icons.Default.Equalizer,null)},trailingContent={if(PlaybackService.currentPreset.toInt()==i)Icon(Icons.Default.Check,null)},modifier=Modifier.clickable{PlaybackService.setEqualizerPreset(i);refresh++})}
  item{TextButton(onClick={PlaybackService.disableEqualizer();refresh++},enabled=available){Text("Desativar equalizador")}}
 }
}
private fun fmt(ms:Long):String{val s=ms.coerceAtLeast(0)/1000;return "%d:%02d".format(s/60,s%60)}
