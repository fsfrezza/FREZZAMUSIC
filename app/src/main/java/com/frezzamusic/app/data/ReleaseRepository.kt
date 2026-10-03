package com.frezzamusic.app.data

import com.frezzamusic.app.model.ReleaseStatus

data class ReleaseAnnouncement(
    val id:String, val artist:String, val title:String, val releaseDate:String?,
    val status:ReleaseStatus, val message:String, val artwork:String?=null, val albumId:String?=null
)

/** Local seed for the UI. Production source will be remote JSON/API so announcements never require a new APK. */
class ReleaseRepository {
    fun announcements():List<ReleaseAnnouncement> = emptyList()
}
