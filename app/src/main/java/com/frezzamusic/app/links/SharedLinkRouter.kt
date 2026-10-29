package com.frezzamusic.app.links

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build

/** All installed variants agree on the same priority, even if Android picked a band app. */
object SharedLinkRouter {
    const val MAIN = "com.frezzamusic.app"
    fun packages(target: SharedContent.Target): List<String> = listOf(
        if (target.artist == "solasias") "com.frezzamusic.solasias" else "com.frezzamusic.thefrezza", MAIN)

    fun forward(context: Context, uri: Uri?): Boolean {
        val target = SharedContent.parse(uri) ?: return false
        val routedUri = Uri.Builder().scheme("frezzamusic").authority("open").apply {
            if (target.short) appendQueryParameter("c", target.id)
            else { appendQueryParameter("artist", target.artist); appendQueryParameter("kind", target.kind); appendQueryParameter("id", target.id) }
        }.build()
        return dispatch(target, context.packageName, canOpen = { pkg ->
            try {
                val info = context.packageManager.getPackageInfo(pkg, 0)
                val version = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
                // Older band APKs forward back to MAIN; skip them to avoid a routing loop.
                version >= if (pkg != MAIN) 50L else if (target.short) 36L else 35L
            } catch (_: android.content.pm.PackageManager.NameNotFoundException) { false }
        }, launch = { pkg ->
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, routedUri).apply {
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
