package com.frezzamusic.app.data

import com.frezzamusic.app.model.*

interface RemoteCatalog { suspend fun artists():List<Artist>; suspend fun tracks()=artists().flatMap{it.albums}.flatMap{it.tracks} }

internal data class RawAlbum(val id:String,val title:String,val folderId:String,val coverId:String?,val tracks:List<Pair<String,String>>)
internal fun album(artist:String,r:RawAlbum):Album {
 val cover=r.coverId?.let{"https://drive.google.com/thumbnail?id=$it&sz=w1200"}
 val genre=if(artist.equals("Solasias",true)) "Metal Cristão" else if(artist.equals("theFrezza",true)) "Metal" else null
 return Album(r.id,r.title,artist,cover,r.tracks.mapIndexed{i,(fid,name)->
  Track("remote:$artist:${r.id}:$fid",name.substringAfter(" - ").substringAfter(". ").removeSuffix(".mp3"),artist,r.title,"",artwork=cover,source=TrackSource.FREZZAMUSIC_STREAM,trackNumber=i+1,remoteFileId=fid,genre=genre)
 })
}

/** Catálogo real inicial extraído das pastas fornecidas pelo proprietário. Novos discos podem ser acrescentados sem alterar o player. */
class FrezzaDriveCatalog:RemoteCatalog {
 override suspend fun artists():List<Artist> {
  val frezza=listOf(
   RawAlbum("frezza","Frezza","1xL2kza5-2I7cYn8z8FAnZsH0rkjgFckU","15rtsXVkX044aDWPlPwf0oQcacXV5Gvf3",listOf(
    "1pRyjLH9umdmC4oZHpOoIMS0IpWX-mrVy" to "01 - Arrow Of Hope.mp3","1_QMolAkecxNVbdTcWsLSxTb9UcMJCtQ_" to "02 - Dead Guy (They Called Him Dead).mp3","1wVgzv7MBa6X7WOCaBgjy97rXvztGg7mj" to "03 - Survivor.mp3","12QEyqUBy8uz8OkhPkBAa0llkdmKboNcp" to "04 - The Thompsons.mp3","1RemCrEdDJgBC-G73l4P1i7Gqk_dWI0o3" to "05 - Freezer.mp3","1U8_kvPf0r_iCqHIgNX1YB6EfdJi8RjQx" to "06 - A Symphony For The Contrasts' Land.mp3","1AKvuwAjQgRfHSLeTFObkcGJzjqjVHoVV" to "07 - Northern Fire.mp3","1m1MVTc_q3sMKowrPfAOyxIbaFI82aodk" to "08 - Coffee in the Bottle.mp3","1Mf8o5jf4ye0eavqTkoQ1-kB6XP_7YZH5" to "09 - The Phantoms Of Gluttony.mp3","1ye4-iNasRmTlr9vwovuFIsCP1VtFI4T6" to "10 - The Ancient Tree.mp3","180gjMIYe2nKC8jdF8yAHYUh4xfDwYsUb" to "11 - Três, Três, Quatro. Vai!.mp3","1-4a9Ws1OvERXx6B_pvIXdDvzSlsOqUkV" to "12 - Frezza (english version).mp3","1_1eHorj9QdfkW-y918E2qfqr9ftv9ue-" to "13 - Frezza (Bônus - versão brasileira).mp3","1c_38g3pNU17fXbB4JBzcZA-CaEoi1eJP" to "14 - Frezza (Bônus - versão latim).mp3")),
   RawAlbum("distentio-animae","Distentio Animae","1ZkhWZ9r5SVZSsbhfYSyA19JcFjM2qRWg","1-OpD2KwHHI90YsZ5iQaAl19zN8FJlWXn",listOf("18yXHUzaG9Xvzn8jn1u3oetR_PepOE2F2" to "01 - Ante Tempus.mp3","1d9F9aXSS-PndrMRAkG3oMk39ZUYAzM7C" to "02 - Nunc Stans.mp3","1bxkj8zLgGihQERSMWLF9TCeJR44YawKo" to "03 - Cum Tempore.mp3","1VlxxtFW0MoPLH9zNlzU4SWXeZTAN96k3" to "04 - Children of Change.mp3","18hDDUSLBs2BJZKGVcC5bXLbXvvovuYar" to "05 - The Vanishing Now.mp3","1V3Cysruq-8lSYmlLHVrYBDAT_UUXSbYN" to "06 - Distentio Animae.mp3","1X3XOY-vL2Qson04uF__0dxPBH0l9a36v" to "07 - Memoria.mp3","1j5CHsOhFj97QuJ60HWLEaj_XVffVhp9x" to "08 - Attentio.mp3","121KeHnwUPF_HK786CAhWWJtpUiWjTAdI" to "09 - Expectatio.mp3","1C0BpLA0AeH7qr-gOSbYuga7mm45oBvhq" to "10 - Tres Praesentia.mp3","1qYFE6va1eW3y56UYs7OxOgzISH78rnOS" to "11 - Ad Aeternitatem.mp3")),
   RawAlbum("entropistan","Entropistan","1yo2YYB1uCjbRAOmfE6nWbE-7E8-NzewU","14R8quDh72sAlcfRHqA1dk-Cq7o1h1-XK",listOf("1wHiSzWrwyHWxzMX6FVZYB9kDSUjOSPo4" to "01 - The Watermelon Brigade.mp3","1FkxzZ7-KBvTH8-4i9hEWZUSXddmsc1PQ" to "02 - The False Bard.mp3","1cc-8FbEvaZcQigBzDJ7MDVDEtWgTJA4h" to "03 - Panis Carus Et Circus Tristis.mp3","1ZPice_NiP5NCtbr8toNxNOkoLRg3Qbfx" to "04 - The Frog Who Owned The Pond.mp3","1oUKQTsVIMO0LbRvGFd_sh5BG8BtP4s32" to "05 - Is She Alive.mp3","1m8hefEBzvSPF1uWUiUljiAgWwPkah08c" to "06 - Rocambole From Hell.mp3","1J4OlwDS4twf5YXe8amCssp9Ogud95wvq" to "07 - The Do-It-All Democrat.mp3","1wEjfbCW94JI68Ke7LujzexNEW7PGEohd" to "08 - The Mirror Mourns.mp3","1r-2Cs2zIhyPkz7lrOxkUNZ45h1ucX-_J" to "09 - Cakes Of Trotsky.mp3","1BIPIbfr5YQt9H3P95ShRFfES07yqglWK" to "10 - Pandæmonium.mp3","18udixqxuNLbKFd4oywGukO8stvo-i_yn" to "11 - Disorder And Entropy.mp3"))
  )
  val solasias=listOf(
   RawAlbum("genesis","Gênesis","1EEhJqN1k36sCVvnDLHcAzcr6OpgQ9SgO","1TMdUvN5D-lO8kyYzw7gPiTm2zEQlLww6",listOf("1Zbjh9GxBkqXCex4kztBYUYR8j9ExdDgw" to "01 - CRIAÇÃO.mp3","1gHE2YezWs_NY8vby0JoI9m05lBzKC51W" to "02 - MULHER.mp3","1UdWx4mwx5ksqUnIUEIUg4pntaRPVPc0l" to "03 - QUEDA.mp3","1PxXQrq-LttqvOMWhpsNKPFbEeP6OwhEo" to "04 - SEMENTES.mp3","1WVCGxjNW6FF8hGpTTO_2C7Q2tT9cwbI5" to "05 - RENOVAÇÃO.mp3","1XuCl42GBeZaK3YVWUxsIakfM4cfL4aV_" to "06 - BABEL.mp3","1vg3f1NIFqOb3wAQ7Q6b9B9Tye8YbGxau" to "07 - PAI DE NAÇÕES.mp3","14fnPNnaPJPj5LSYah0mx1AM6RPe7eVrW" to "08 - PROMETIDO.mp3","1RBpBbfCurJefRqPw-ZGn8mzQzQCQW6rG" to "09 - ELEITO.mp3","1Vhcfqf8EGETv82m174PB9ZJlIotrgQ4f" to "10 - SONHADOR.mp3","1EiixV7RteYC4h_esaPSRYQ1gC0l53P_3" to "11 - GÊNESIS.mp3")),
   RawAlbum("reforma-metal","Reforma Metal","13xpkuefYVwB-ns4a5hYIAkk4smy0Ixyc","1evH1ZyekL8rKgiXw9yjm4RxCFBeJAGWZ",listOf("1f6sVvupRR8pyTWz8ICiR3RbKbXRvCfSh" to "01 - POST TENEBRAS LUX.mp3","1PBd8_PAKKey-11s9JKmlKhV8ZyZ_k31p" to "02 - O MONGE REFORMADOR.mp3","19jlIwu30iptKV9SPTDZyrqDxXCJ8ufiD" to "03 - SOMENTE PELA GRAÇA.mp3","1YN_ddDwcfS8bGonkRvWM-hmSHMmSs9YB" to "04 - O DESPERTAR DE WITTENBERG.mp3","1DK3TZzvXL2ZtcYw1tePPLOevOYvNmg6u" to "05 - SOMENTE A ESCRITURA.mp3","1J15EI2Krq0EPOHkelQtb-u8HmgjLmeMp" to "06 - A PALAVRA FEZ TUDO.mp3","1prXTMuonZRl7C0JHjWx_3Kg1ma0jWj5B" to "07 - CORDEIRO MEDIADOR.mp3","1H8aiUs1ZcDQvsjJckQpFaQim6i7jLawP" to "08 - HERDEIROS DA PALAVRA.mp3","16XpWBK3r89jVyrcBI-LZv6RBT3Jd6IMC" to "09 - PELA FÉ.mp3","16DHdOIJrEUwutGZ9kHqJzzL1vkeBcv05" to "10 - ECO DAS NAÇÕES.mp3","1hK8Er-OyZZ1mjFJ6_wWQDSJnB7hR4osD" to "11 - SOMENTE A DEUS A GLÓRIA.mp3","1jkW-Pm71_1JMM_vNYFaA48TFZ-vZhuu5" to "12 - CASTELO FORTE (cover).mp3"))
  )
  val allFrezza = frezza + CatalogExpansion.theFrezza()
  val allSolasias = solasias + CatalogExpansion.solasias()
  return listOf(Artist("thefrezza","theFrezza",allFrezza.map{album("theFrezza",it)}, virtualProject=true, description="Projeto musical virtual"),Artist("solasias","Solasias",allSolasias.map{album("Solasias",it)}, virtualProject=true, description="Projeto musical virtual"))
 }
}
