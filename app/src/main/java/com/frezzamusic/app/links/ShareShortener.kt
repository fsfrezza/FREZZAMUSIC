package com.frezzamusic.app.links

import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.concurrent.TimeUnit

internal object ShortLinkRules {
    fun accepted(value: String): String? = value.trim().takeIf { it.matches(Regex("https://da\\.gd/[A-Za-z0-9_-]{1,40}")) }
    fun requestUrl(canonical: String): String {
        require(SharedContent.parseUrl(canonical) != null)
        return "https://da.gd/s".toHttpUrl().newBuilder().addQueryParameter("url", canonical).build().toString()
    }
}

/** Called only on an explicit share tap. Cache permanent redirects rather than recreating them. */
internal object ShareShortener {
    private val client = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS).callTimeout(12, TimeUnit.SECONDS).build()
    private fun owner(context: Context): LifecycleOwner? {
        var current = context
        while (true) {
            if (current is LifecycleOwner) return current
            if (current !is ContextWrapper || current.baseContext === current) return null
            current = current.baseContext
        }
    }
    fun share(context: Context, title: String, canonical: String, deliver: (String) -> Unit) {
        val lifecycleOwner = owner(context)
        if (lifecycleOwner == null) { deliver(canonical); return }
        lifecycleOwner.lifecycleScope.launch {
            Toast.makeText(context, "Preparando link curto…", Toast.LENGTH_SHORT).show()
            val prefs = context.applicationContext.getSharedPreferences("share_short_links_v1", Context.MODE_PRIVATE)
            val cached = prefs.getString(canonical, null)?.let(ShortLinkRules::accepted)
            if (cached != null) { deliver(cached); return@launch }
            try {
                val short = withContext(Dispatchers.IO) {
                    client.newCall(Request.Builder().url(ShortLinkRules.requestUrl(canonical)).build()).execute().use { response ->
                        check(response.isSuccessful)
                        val source = response.body?.source() ?: error("Empty short link")
                        source.request(1025)
                        require(source.buffer.size <= 1024)
                        ShortLinkRules.accepted(source.readUtf8()) ?: error("Invalid short link")
                    }
                }
                prefs.edit().putString(canonical, short).apply()
                deliver(short)
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) {
                Toast.makeText(context, "Não foi possível encurtar agora. Compartilhando o link direto.", Toast.LENGTH_LONG).show()
                deliver(canonical)
            }
        }
    }
}
