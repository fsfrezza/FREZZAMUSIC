package com.frezzamusic.app.player

/** Measures real time spent playing; paused/buffering intervals do not count. */
internal class ListeningCounter {
    private var previousTime = 0L
    private var previouslyPlaying = false
    private var heardMs = 0L
    private var reported = false
    fun reset(now: Long) { previousTime=now; previouslyPlaying=false; heardMs=0; reported=false }
    fun sample(now: Long, playing: Boolean): Boolean {
        val delta=(now-previousTime).coerceIn(0,2000)
        if(playing && previouslyPlaying) heardMs+=delta
        previousTime=now; previouslyPlaying=playing
        if(!reported && heardMs>=30_000) { reported=true; return true }
        return false
    }
}
