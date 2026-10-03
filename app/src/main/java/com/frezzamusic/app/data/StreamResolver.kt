package com.frezzamusic.app.data

import com.frezzamusic.app.model.Track
import com.frezzamusic.app.model.TrackSource

data class StreamAuthorization(val url:String,val expiresAtEpochMs:Long?=null)
interface StreamResolver { suspend fun resolve(track:Track):StreamAuthorization }

/** Development resolver. Drive is temporary; production will swap this for the FREZZAMUSIC API/R2 without changing the player. */
class DevelopmentDriveStreamResolver:StreamResolver {
    override suspend fun resolve(track:Track):StreamAuthorization {
        require(track.source == TrackSource.FREZZAMUSIC_STREAM || track.source == TrackSource.EXTERNAL_STREAM)
        track.uri.takeIf { it.startsWith("http") }?.let { return StreamAuthorization(it) }
        val id = requireNotNull(track.remoteFileId)
        return StreamAuthorization("https://drive.google.com/uc?export=download&id=$id")
    }
}
