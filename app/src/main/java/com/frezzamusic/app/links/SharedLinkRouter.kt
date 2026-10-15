package com.frezzamusic.app.links

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build

/** All installed variants agree on the same priority, even if Android picked a band app. */
object SharedLinkRouter {
    const val MAIN = "com.frezzamusic.app"
    fun packages(target: SharedContent.Target): List<String> = listOf(MAIN,
        if (target.artist == "solasias") "com.frezzamusic.solasias" else "com.frezzamusic.thefrezza")

    fun forward(context: Context, uri: Uri?): Boolean {
        val target = SharedContent.parse(uri) ?: return false
        return dispatch(target, context.packageName, canOpen = { pkg ->
            try {
                val info = context.packageManager.getPackageInfo(pkg, 0)
                val version = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
                version >= if (target.short) 36L else 35L
            } catch (_: android.content.pm.PackageManager.NameNotFoundException) { false }
        }, launch = { pkg ->
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply {
                    setPackage(pkg)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                })
                true
            } catch (_: ActivityNotFoundException) { false }
            catch (_: SecurityException) { false }
        })
    }
    internal fun dispatch(target: SharedContent.Target, current: String,
                          canOpen: (String) -> Boolean, launch: (String) -> Boolean): Boolean {
        for (pkg in packages(target)) {
            if (pkg == current) return false
            if (canOpen(pkg) && launch(pkg)) return true
        }
        return false
    }
}
