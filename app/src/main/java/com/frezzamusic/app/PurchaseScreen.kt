package com.frezzamusic.app
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
import com.frezzamusic.app.model.*
import java.text.NumberFormat
import java.util.Locale
private const val TRACK_PRICE=1.99
private const val ALBUM_PRICE=14.99
@Composable fun PurchaseScreen(artists:List<Artist>,initialTrack:Track?,initialAlbum:Album?,onClose:()->Unit){
 val albums=remember(artists){artists.flatMap{it.albums}};val selected=remember{mutableStateMapOf<String,Boolean>()};val expanded=remember{mutableStateMapOf<String,Boolean>()};var showPaymentInfo by remember{mutableStateOf(false)}
 LaunchedEffect(initialTrack?.id,initialAlbum?.id){selected.clear();expanded.clear();if(initialTrack!=null){selected[initialTrack.id]=true;initialAlbum?.let{expanded[it.id]=true}}else if(initialAlbum!=null){initialAlbum.tracks.forEach{selected[it.id]=true};expanded[initialAlbum.id]=true}}
 val chosenIds=selected.filterValues{it}.keys
 val total=albums.sumOf{album->val count=album.tracks.count{it.id in chosenIds};when{count==0->0.0;count==album.tracks.size->ALBUM_PRICE;else->count*TRACK_PRICE}}
 val currency=remember{NumberFormat.getCurrencyInstance(Locale("pt","BR"))}
 Scaffold(topBar={Surface(tonalElevation=3.dp){Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically){IconButton(onClick=onClose){Icon(Icons.Default.ArrowBack,"Voltar")};Column{Text("Downloads em alta qualidade",fontWeight=FontWeight.Bold);Text("Escolha faixas ou álbuns",style=MaterialTheme.typography.bodySmall)}}}},bottomBar={Surface(tonalElevation=6.dp){Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("Total",style=MaterialTheme.typography.bodySmall);Text(currency.format(total),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)};Button(onClick={showPaymentInfo=true},enabled=chosenIds.isNotEmpty()){Icon(Icons.Default.Payment,null);Spacer(Modifier.width(8.dp));Text("Continuar")}}}}){pad->
  LazyColumn(Modifier.padding(pad),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
   item{Text("Apoie o projeto",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Text("Todas as músicas podem ser ouvidas gratuitamente. Se quiser apoiar o projeto, você pode adquirir o download em alta qualidade.",modifier=Modifier.padding(top=6.dp));Text("Faixa: "+currency.format(TRACK_PRICE)+"  •  Álbum completo: "+currency.format(ALBUM_PRICE)+"",color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold,modifier=Modifier.padding(vertical=10.dp));HorizontalDivider()}
   items(albums,key={it.id}){album->val albumIds=album.tracks.map{it.id};val count=albumIds.count{it in chosenIds};val all=count==albumIds.size&&albumIds.isNotEmpty();val partial=count>0&&!all
    Column{Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Checkbox(checked=all,onCheckedChange={checked->albumIds.forEach{selected[it]=checked}});Column(Modifier.weight(1f).clickable{expanded[album.id]=!(expanded[album.id]?:false)}){Text(album.title,fontWeight=FontWeight.Bold);Text(album.artist+" • "+album.tracks.size+" faixas"+if(partial)" • "+count+" selecionadas" else "",style=MaterialTheme.typography.bodySmall)};Text(if(all)currency.format(ALBUM_PRICE)else if(partial)currency.format(count*TRACK_PRICE)else currency.format(ALBUM_PRICE),style=MaterialTheme.typography.labelLarge);IconButton(onClick={expanded[album.id]=!(expanded[album.id]?:false)}){Icon(if(expanded[album.id]==true)Icons.Default.ExpandLess else Icons.Default.ExpandMore,null)}}
     if(expanded[album.id]==true)album.tracks.forEach{track->Row(Modifier.fillMaxWidth().padding(start=28.dp),verticalAlignment=Alignment.CenterVertically){Checkbox(checked=selected[track.id]==true,onCheckedChange={selected[track.id]=it});Column(Modifier.weight(1f)){Text(track.title);Text(track.artist,style=MaterialTheme.typography.bodySmall)};Text(currency.format(TRACK_PRICE),style=MaterialTheme.typography.bodySmall)}};HorizontalDivider()}
   }
  }
 }
 if(showPaymentInfo)AlertDialog(onDismissRequest={showPaymentInfo=false},title={Text("Pagamento")},text={Text("Seleção: "+chosenIds.size+" faixa(s)\nTotal: "+currency.format(total)+"\n\nA seleção já está pronta para ser enviada ao provedor de pagamento. A cobrança será ativada quando o Google Play Billing/PIX e a validação pelo servidor estiverem configurados.")},confirmButton={Button(onClick={showPaymentInfo=false}){Text("Entendi")}},dismissButton={OutlinedButton(onClick={showPaymentInfo=false}){Text("Voltar")}})
}