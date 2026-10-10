package com.frezzamusic.app.player

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.frezzamusic.app.data.SettingsRepository
import com.frezzamusic.app.data.OfflineDownloadRepository
import com.frezzamusic.app.data.StreamResolver
import com.frezzamusic.app.data.UserLibraryRepository
import com.frezzamusic.app.model.Track
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.*

class PlaybackController(
    private val context: Context,
    private val resolver: StreamResolver,
    private val user: UserLibraryRepository
) {
    private var future: ListenableFuture<MediaController>? = null
    var controller: MediaController? = null
        private set
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val settings = SettingsRepository(context)
    private val offline = OfflineDownloadRepository(context, resolver)
    var onChanged: (() -> Unit)? = null
    private var lastRecorded: String? = null
    private var checkpointJob: Job? = null

    fun connect() {
        if (future != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        future = MediaController.Builder(context, token).buildAsync().also { f ->
            f.addListener({
                controller = f.get().also { c ->
                    c.shuffleModeEnabled = settings.shuffle
                    c.repeatMode = settings.repeatMode
                    c.setPlaybackSpeed(settings.speed)
                    c.addListener(object : Player.Listener {
                        override fun onEvents(player: Player, events: Player.Events) {
                            val id = player.currentMediaItem?.mediaId
                            if (id != null) {
                                settings.lastTrackId = id
                                settings.lastPositionMs = player.currentPosition.coerceAtLeast(0L)
                                if (player.isPlaying && id != lastRecorded) {
                                    lastRecorded = id
                                    user.recordPlay(id)
                                }
                            }
                            onChanged?.invoke()
                        }
                    })
                }
                startPositionCheckpoints()
                onChanged?.invoke()
            }, ContextCompat.getMainExecutor(context))
        }
    }

    private fun startPositionCheckpoints() {
        checkpointJob?.cancel()
        checkpointJob = scope.launch {
            while (isActive) {
                delay(5_000L)
                controller?.let { c ->
                    c.currentMediaItem?.mediaId?.let { settings.lastTrackId = it }
                    if (c.currentMediaItem != null) settings.lastPositionMs = c.currentPosition.coerceAtLeast(0L)
                }
            }
        }
    }

    fun release() {
        checkpointJob?.cancel()
        controller?.let { c ->
            c.currentMediaItem?.mediaId?.let { settings.lastTrackId = it }
            settings.lastPositionMs = c.currentPosition.coerceAtLeast(0L)
        }
        future?.let { MediaController.releaseFuture(it) }
        future = null
        controller = null
        scope.cancel()
    }

    fun play(track: Track, queue: List<Track>, positionMs: Long = 0L) {
        scope.launch {
            val start = queue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
            val items = queue.map { toItem(it) }
            controller?.apply {
                setMediaItems(items, start, positionMs.coerceAtLeast(0L))
                prepare()
                play()
            }
        }
    }

    fun resumePosition(trackId: String): Long =
        if (settings.lastTrackId == trackId) settings.lastPositionMs else 0L

    fun lastTrackId(): String? = settings.lastTrackId
    fun toggle() { controller?.let { if (it.isPlaying) it.pause() else it.play() } }
    fun next() { controller?.seekToNextMediaItem() }
    fun previous() { controller?.seekToPreviousMediaItem() }
    fun seek(ms: Long) { controller?.seekTo(ms) }
    fun shuffle() { controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled; settings.shuffle = it.shuffleModeEnabled }; onChanged?.invoke() }
    fun repeat() {
        controller?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
        controller?.let { settings.repeatMode = it.repeatMode }
        onChanged?.invoke()
    }
    fun setSpeed(speed: Float) { val value=speed.coerceIn(.5f,2f); settings.speed=value; controller?.setPlaybackSpeed(value); onChanged?.invoke() }
    fun sleepTimer(minutes: Int) {
        scope.launch {
            delay(minutes.coerceAtLeast(1) * 60_000L)
            controller?.pause()
            onChanged?.invoke()
        }
    }

    private suspend fun toItem(t: Track): MediaItem {
        val u = if (t.remote) offline.localUri(t) ?: resolver.resolve(t).url else t.uri
        return MediaItem.Builder()
            .setMediaId(t.id)
            .setUri(u)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(t.title)
                    .setArtist(t.artist)
                    .setAlbumTitle(t.album)
                    .setArtworkUri(t.artwork?.let(android.net.Uri::parse))
                    .build()
            )
            .build()
    }
}
