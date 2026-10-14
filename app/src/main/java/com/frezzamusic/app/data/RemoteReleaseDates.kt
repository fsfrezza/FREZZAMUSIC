package com.frezzamusic.app.data
import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.*
import java.util.concurrent.TimeUnit
import java.io.ByteArrayOutputStream

/** Reads bounded ID3 headers, never saves MP3s or mistakes file timestamps for releases. */
internal class RemoteReleaseDates(context:Context) {
 private val cache=context.applicationContext.getSharedPreferences("release_tag_dates_v1",Context.MODE_PRIVATE)
 private val slots=Semaphore(8)
 private val unavailable=java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
 fun beginCheck(){unavailable.clear()}
 fun unavailableCount()=unavailable.size
 private val client=OkHttpClient.Builder().connectTimeout(4,TimeUnit.SECONDS).readTimeout(5,TimeUnit.SECONDS).callTimeout(8,TimeUnit.SECONDS).build()
 private suspend fun read(id:String,force:Boolean):String? = slots.withPermit {
  val now=System.currentTimeMillis()
  if(!force && now-cache.getLong("at:$id",0)<86_400_000)return@withPermit cache.getString(id,null)?.takeIf{it.isNotEmpty()}
  try {
   val req=Request.Builder().url("https://drive.google.com/uc?export=download&id=$id").header("Range","bytes=0-65535").build()
   val date=client.newCall(req).execute().use { response->
    if(!response.isSuccessful)throw java.io.IOException("Tags indisponíveis")
    response.body?.byteStream()?.use{input->val out=ByteArrayOutputStream();val b=ByteArray(4096);while(out.size()<65536){val n=input.read(b,0,minOf(b.size,65536-out.size()));if(n<0)break;out.write(b,0,n)};ReleaseDates.fromId3(out.toByteArray())}
   }
   cache.edit().putString(id,date.orEmpty()).putLong("at:$id",now).apply();date
  }catch(e:CancellationException){throw e}catch(_:Exception){
   unavailable.add(id)
   if(force){cache.edit().remove(id).remove("at:$id").apply();null}
   else cache.getString(id,null)?.takeIf{it.isNotEmpty()}
  }
 }
 suspend fun album(album:RawAlbum,force:Boolean):RawAlbum {
  val dates=mutableMapOf<String,String>()
  for((id,_) in album.tracks){
   read(id,force)?.let{dates[id]=it}
   val values=album.tracks.map{dates[it.first]}
   if(ReleaseDates.detailed(ReleaseDates.majority(values)))break
   // Once over half the files have only the same year, a detailed-date majority is impossible.
   if(dates.values.count{!ReleaseDates.detailed(it)}>album.tracks.size/2)break
  }
  return album.copy(releaseDates=dates)
 }
}
