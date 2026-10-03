package com.frezzamusic.app.ui
import androidx.compose.foundation.clickable
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
import com.frezzamusic.app.data.LrcParser
import com.frezzamusic.app.model.Track
import com.frezzamusic.app.player.PlaybackController
import kotlinx.coroutines.delay

@Composable fun FullPlayer(track:Track?,pc:PlaybackController,onClose:()->Unit){
 val p=pc.controller; var page by remember{mutableIntStateOf(0)}; var pos by remember{mutableLongStateOf(p?.currentPosition?:0L)}; var support by remember{mutableStateOf(false)}
 LaunchedEffect(p?.isPlaying,p?.currentMediaItemIndex){while(true){pos=p?.currentPosition?:0L;delay(500)}}
 val dur=(p?.duration?:0L).coerceAtLeast(0L)
 Column(Modifier.fillMaxSize().padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally){
  Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){IconButton(onClose){Icon(Icons.Default.KeyboardArrowDown,"Fechar")};Text("Tocando agora",Modifier.weight(1f),fontWeight=FontWeight.Bold);if(track?.remote==true)TextButton({support=true}){Icon(Icons.Default.Download,null);Text(" Apoiar e baixar")}}
  TabRow(page){Tab(page==0,{page=0},text={Text("Player")});Tab(page==1,{page=1},text={Text("Letras")});Tab(page==2,{page=2},text={Text("Fila")})}
  when(page){0->PlayerPage(track,pc,pos,dur){pos=it};1->LyricsPage(track,pos);else->QueuePage(pc)}
 }
 if(support)AlertDialog(onDismissRequest={support=false},icon={Icon(Icons.Default.Favorite,null)},title={Text("Gostou desta música? Apoie o projeto")},text={Text("Todas as músicas podem ser ouvidas gratuitamente. Sua contribuição ajuda a manter o aplicativo e a produção de novas músicas. Como agradecimento, o download em alta qualidade será liberado quando o sistema de contribuições estiver ativo.\n\nFaixa: R$ 3,90. Álbuns terão preço reduzido conforme a quantidade de faixas.")},confirmButton={TextButton({support=false}){Text("Entendi")}},dismissButton={TextButton({support=false}){Text("Agora não")}})
}
@Composable private fun PlayerPage(t:Track?,pc:PlaybackController,pos:Long,dur:Long,setPos:(Long)->Unit){val p=pc.controller;Column(Modifier.fillMaxSize(),horizontalAlignment=Alignment.CenterHorizontally){Spacer(Modifier.weight(1f));Icon(Icons.Default.Album,null,Modifier.size(190.dp),tint=MaterialTheme.colorScheme.primary);Spacer(Modifier.height(20.dp));Text(t?.title?:"Nada tocando",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Text(t?.artist?:"");Text(t?.album?:"",style=MaterialTheme.typography.bodySmall);Slider(pos.coerceAtMost(dur.coerceAtLeast(1)).toFloat(),{setPos(it.toLong())},onValueChangeFinished={pc.seek(pos)},valueRange=0f..dur.coerceAtLeast(1).toFloat());Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(fmt(pos));Text("-"+fmt((dur-pos).coerceAtLeast(0)))};Row(verticalAlignment=Alignment.CenterVertically){IconButton({pc.shuffle()}){Icon(Icons.Default.Shuffle,"Aleatório")};IconButton({pc.previous()}){Icon(Icons.Default.SkipPrevious,"Anterior")};FilledIconButton({pc.toggle()},Modifier.size(64.dp)){Icon(if(p?.isPlaying==true)Icons.Default.Pause else Icons.Default.PlayArrow,"Reproduzir")};IconButton({pc.next()}){Icon(Icons.Default.SkipNext,"Próxima")};IconButton({pc.repeat()}){Icon(Icons.Default.Repeat,"Repetição")}};Spacer(Modifier.weight(1f))}}
@Composable private fun LyricsPage(t:Track?,pos:Long){val lrc=t?.lrc;if(!lrc.isNullOrBlank()){val lines=remember(lrc){LrcParser.parse(lrc)};val current=LrcParser.current(lines,pos);LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(vertical=24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){itemsIndexed(lines){i,line->Text(line.text,style=if(i==current)MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,fontWeight=if(i==current)FontWeight.Bold else FontWeight.Normal,color=if(i==current)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)}}}else Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Text(t?.lyrics?:"Nenhuma letra disponível para esta faixa.",Modifier.padding(24.dp))}}
@Composable private fun QueuePage(pc:PlaybackController){val p=pc.controller;val entries=(0 until (p?.mediaItemCount?:0)).toList();LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(vertical=12.dp)){items(entries){i->val item=p?.getMediaItemAt(i);ListItem(headlineContent={Text(item?.mediaMetadata?.title?.toString()?:"Faixa")},supportingContent={Text(item?.mediaMetadata?.artist?.toString().orEmpty())},leadingContent={Text((i+1).toString())},trailingContent={if(i==p?.currentMediaItemIndex)Icon(Icons.Default.GraphicEq,"Tocando")},modifier=Modifier.clickable{p?.seekToDefaultPosition(i);p?.play()})};if(entries.isEmpty())item{Text("A fila está vazia.",Modifier.padding(24.dp))}}}
private fun fmt(ms:Long):String{val s=ms.coerceAtLeast(0)/1000;return "%d:%02d".format(s/60,s%60)}
