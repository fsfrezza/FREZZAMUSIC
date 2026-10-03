package com.frezzamusic.app.player
import android.media.audiofx.Equalizer
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.*

class PlaybackService:MediaSessionService(){
 private var session:MediaSession?=null
 private var eq:Equalizer?=null
 companion object {
  @Volatile var audioSessionId:Int=0; private set
  @Volatile var equalizerAvailable:Boolean=false; private set
  @Volatile var currentPreset:Short=-1; private set
  @Volatile var presetNames:List<String> = emptyList(); private set
  private var instance:PlaybackService?=null
  fun setEqualizerPreset(index:Int):Boolean = instance?.applyPreset(index) ?: false
  fun disableEqualizer(){instance?.eq?.enabled=false;currentPreset=-1}
 }
 override fun onCreate(){
  super.onCreate();instance=this
  val p=ExoPlayer.Builder(this).build()
  p.addListener(object:Player.Listener{
   override fun onPlaybackStateChanged(state:Int){if(state==Player.STATE_READY) attachEqualizer(p.audioSessionId)}
  })
  session=MediaSession.Builder(this,p).build()
 }
 private fun attachEqualizer(id:Int){
  if(id<=0||id==audioSessionId&&eq!=null)return
  runCatching{
   eq?.release();audioSessionId=id
   eq=Equalizer(0,id).also{e->
    presetNames=(0 until e.numberOfPresets.toInt()).map{e.getPresetName(it.toShort())}
    e.enabled=true;equalizerAvailable=true
   }
  }.onFailure{equalizerAvailable=false;presetNames=emptyList()}
 }
 private fun applyPreset(index:Int):Boolean{
  val e=eq?:return false
  if(index !in presetNames.indices)return false
  return runCatching{e.usePreset(index.toShort());e.enabled=true;currentPreset=index.toShort();true}.getOrDefault(false)
 }
 override fun onGetSession(controllerInfo:MediaSession.ControllerInfo)=session
 override fun onTaskRemoved(rootIntent:android.content.Intent?){if(session?.player?.playWhenReady!=true)stopSelf()}
 override fun onDestroy(){eq?.release();eq=null;equalizerAvailable=false;audioSessionId=0;instance=null;session?.run{player.release();release()};session=null;super.onDestroy()}
}
