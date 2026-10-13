package com.frezzamusic.app.player

import org.junit.Assert.*
import org.junit.Test

class ListeningCounterTest {
    @Test fun countsThirtySecondsOfPlayingOnceAndDoesNotCountPausedOrBufferingTime() {
        val counter=ListeningCounter();counter.reset(0)
        assertFalse(counter.sample(0,true))
        for(t in 1L..15L)assertFalse(counter.sample(t*1000,true))
        assertFalse(counter.sample(16000,false))
        for(t in 17L..100L)assertFalse(counter.sample(t*1000,false))
        assertFalse(counter.sample(101000,true))
        for(t in 102L..115L)assertFalse(counter.sample(t*1000,true))
        assertTrue(counter.sample(116000,true))
        for(t in 117L..150L)assertFalse(counter.sample(t*1000,true))
    }
    @Test fun aNewTrackOrRepeatStartsANewCountAndLargeTimeGapsDoNotFabricateListening() {
        val counter=ListeningCounter();counter.reset(0);counter.sample(0,true)
        assertFalse(counter.sample(120000,true))
        counter.reset(120000);counter.sample(120000,true)
        for(t in 1L..29L)assertFalse(counter.sample(120000+t*1000,true))
        assertTrue(counter.sample(150000,true))
        counter.reset(150000);assertFalse(counter.sample(150000,true))
        for(t in 1L..29L)assertFalse(counter.sample(150000+t*1000,true))
        assertTrue(counter.sample(180000,true))
    }
}
