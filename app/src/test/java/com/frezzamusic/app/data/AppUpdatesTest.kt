package com.frezzamusic.app.data

import org.junit.Assert.*
import org.junit.Test

class AppUpdatesTest {
    private fun manifest(pkg: String, code: Int, file: String) = """{"schema":1,"apps":{"$pkg":{"versionCode":$code,"versionName":"0.23.4","driveFileId":"$file"}}}"""
    @Test fun onlyStrictlyNewerVersionsAreOfferedAcrossAllFlavors() {
        AppUpdateManifest.files.forEach { (pkg, file) ->
            val json = manifest(pkg, 39, file)
            assertNull(AppUpdateManifest.newer(json, pkg, 39))
            assertNull(AppUpdateManifest.newer(json, pkg, 40))
            assertEquals(39, AppUpdateManifest.newer(json, pkg, 38)!!.versionCode)
            assertEquals("https://drive.google.com/file/d/$file/view", AppUpdateManifest.newer(json, pkg, 38)!!.downloadUrl)
        }
    }
    @Test fun anotherAppCannotOfferAnUpdate() {
        val pkg = "com.frezzamusic.app"
        val json = manifest(pkg, 99, AppUpdateManifest.files.getValue(pkg))
        assertNull(AppUpdateManifest.newer(json, "com.frezzamusic.solasias", 38))
        assertNull(AppUpdateManifest.newer(json, "unrecognized", 38))
    }
    @Test(expected = IllegalArgumentException::class) fun unexpectedDownloadIsRejected() {
        AppUpdateManifest.newer(manifest("com.frezzamusic.app", 99, "foreign-file"), "com.frezzamusic.app", 38)
    }
    @Test(expected = IllegalArgumentException::class) fun invalidVersionIsRejected() {
        AppUpdateManifest.newer(manifest("com.frezzamusic.app", 0, AppUpdateManifest.files.getValue("com.frezzamusic.app")), "com.frezzamusic.app", 38)
    }
}
