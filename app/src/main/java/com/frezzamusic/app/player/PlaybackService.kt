package com.frezzamusic.app.player
import android.media.audiofx.Equalizer
import android.media.audiofx.Visualizer
import android.content.Context
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.*

class PlaybackService:MediaSessionService(){
 private var session:MediaSession?=null
 private var eq:Equalizer?=null
 private var visualizer:Visualizer?=null
 private var visualizerSessionId:Int=0
 companion object {
  @Volatile var audioSessionId:Int=0; private set
  @Volatile var equalizerAvailable:Boolean=false; private set
  @Volatile var currentPreset:Short=-1; private set
  @Volatile var presetNames:List<String> = emptyList(); private set
  @Volatile var spectrum:List<Int> = emptyList(); private set
  @Volatile var waveform:List<Int> = emptyList(); private set
  @Volatile var visualizerAvailable:Boolean=false; private set
  @Volatile var visualizerEnabled:Boolean=true; private set
  private const val VISUALIZER_PREFS="playback_visualizer"
  private const val VISUALIZER_ENABLED="enabled"
  private var instance:PlaybackService?=null
  fun setEqualizerPreset(index:Int):Boolean = instance?.applyPreset(index) ?: false
  fun disableEqualizer(){instance?.eq?.enabled=false;currentPreset=-1}
  fun setVisualizerEnabled(enabled:Boolean){
   visualizerEnabled=enabled
   instance?.getSharedPreferences(VISUALIZER_PREFS,Context.MODE_PRIVATE)?.edit()?.putBoolean(VISUALIZER_ENABLED,enabled)?.apply()
   instance?.updateVisualizerState()
  }
 }
 override fun onCreate(){
  super.onCreate();instance=this
  visualizerEnabled=getSharedPreferences(VISUALIZER_PREFS,Context.MODE_PRIVATE).getBoolean(VISUALIZER_ENABLED,true)
  val p=ExoPlayer.Builder(this).build()
  p.addListener(object:Player.Listener{
   override fun onPlaybackStateChanged(state:Int){if(state==Player.STATE_READY){attachEqualizer(p.audioSessionId);attachVisualizer(p.audioSessionId)}}
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
 private fun attachVisualizer(id:Int){
  if(id<=0)return
  if(id==visualizerSessionId&&visualizer!=null){updateVisualizerState();return}
  runCatching{
   visualizer?.release();visualizerSessionId=id
   visualizer=Visualizer(id).also{v->
    v.captureSize=Visualizer.getCaptureSizeRange()[0]
    v.setDataCaptureListener(object:Visualizer.OnDataCaptureListener{
     override fun onWaveFormDataCapture(vis:Visualizer?,data:ByteArray?,rate:Int){if(data!=null){val step=(data.size/48).coerceAtLeast(1);waveform=(0 until 48).map{i->data[(i*step).coerceAtMost(data.lastIndex)].toInt()}}}
     override fun onFftDataCapture(vis:Visualizer?,fft:ByteArray?,rate:Int){
      if(fft==null)return
      val bins=24;val step=(fft.size/2/bins).coerceAtLeast(1)
      spectrum=(0 until bins).map{b->
       val k=((b*step)*2).coerceIn(2,fft.size-2)
       val re=fft[k].toInt();val im=fft[k+1].toInt()
       kotlin.math.sqrt((re*re+im*im).toDouble()).toInt().coerceIn(0,128)
      }
     }
    },Visualizer.getMaxCaptureRate()/2,true,true)
    v.enabled=visualizerEnabled;visualizerAvailable=true
   }
  }.onFailure{visualizerAvailable=false;spectrum=emptyList()}
 }
 private fun updateVisualizerState(){
  runCatching{
   visualizer?.let { v -> if(v.enabled!=visualizerEnabled)v.enabled=visualizerEnabled }
  }.onFailure{visualizerAvailable=false;spectrum=emptyList();waveform=emptyList()}
  if(!visualizerEnabled){spectrum=emptyList();waveform=emptyList()}
 }
 private fun applyPreset(index:Int):Boolean{
  val e=eq?:return false
  if(index !in presetNames.indices)return false
  return runCatching{e.usePreset(index.toShort());e.enabled=true;currentPreset=index.toShort();true}.getOrDefault(false)
 }
 override fun onGetSession(controllerInfo:MediaSession.ControllerInfo)=session
 override fun onTaskRemoved(rootIntent:android.content.Intent?){if(session?.player?.playWhenReady!=true)stopSelf()}
 override fun onDestroy(){visualizer?.release();visualizer=null;visualizerSessionId=0;visualizerAvailable=false;spectrum=emptyList();waveform=emptyList();eq?.release();eq=null;equalizerAvailable=false;audioSessionId=0;instance=null;session?.run{player.release();release()};session=null;super.onDestroy()}
}
