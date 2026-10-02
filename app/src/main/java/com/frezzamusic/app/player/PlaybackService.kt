package com.frezzamusic.app.player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.*
class PlaybackService:MediaSessionService(){private var session:MediaSession?=null;override fun onCreate(){super.onCreate();val p=ExoPlayer.Builder(this).build();session=MediaSession.Builder(this,p).build()}override fun onGetSession(controllerInfo:MediaSession.ControllerInfo)=session;override fun onTaskRemoved(rootIntent:android.content.Intent?){if(session?.player?.playWhenReady!=true)stopSelf()}override fun onDestroy(){session?.run{player.release();release()};session=null;super.onDestroy()}}
