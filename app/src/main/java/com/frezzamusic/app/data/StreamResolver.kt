package com.frezzamusic.app.data

import com.frezzamusic.app.model.Track
import com.frezzamusic.app.model.TrackSource

data class StreamAuthorization(val url:String,val expiresAtEpochMs:Long?=null)
interface StreamResolver { suspend fun resolve(track:Track):StreamAuthorization }
class DevelopmentDriveStreamResolver:StreamResolver {
    override suspend fun resolve(track:Track):StreamAuthorization {
        require(track.source==TrackSource.ONLINE_FREE) { "HQ precisa de autorização do backend" }
        val id=requireNotNull(track.remoteFileId)
        return StreamAuthorization("https://drive.google.com/uc?export=download&id=$id")
    }
}
