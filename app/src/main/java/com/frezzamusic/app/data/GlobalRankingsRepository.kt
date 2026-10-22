package com.frezzamusic.app.data

import android.content.Context
import com.frezzamusic.app.BuildConfig
import com.frezzamusic.app.model.Track
import com.frezzamusic.app.model.TrackSource
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

internal data class RankedMusic(val track: Track, val total: Long)
internal data class RankingLists(val played: List<RankedMusic>, val liked: List<RankedMusic>)
internal data class RankingSnapshot(val scopes: Map<String, RankingLists>)

/** One shared API for all flavors. Only official music metadata is sent; local music is excluded. */
class GlobalRankingsRepository private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("global_rankings_v1", Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder().connectTimeout(10,TimeUnit.SECONDS).readTimeout(20,TimeUnit.SECONDS).callTimeout(25,TimeUnit.SECONDS).build()
    @Volatile private var account: PurchaseIdentity? = PurchaseIdentity(context.applicationContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val flushLock = Mutex()
    private val registrationLock = Mutex()
    companion object {
        @Volatile private var instance: GlobalRankingsRepository? = null
        fun get(context: Context): GlobalRankingsRepository = instance ?: synchronized(this) {
            instance ?: GlobalRankingsRepository(context).also { instance = it }
        }
    }
    init { scope.launch { while (isActive) { runCatching { flush() }; delay(30_000) } } }
    private fun metadata(track: Track): JSONObject? {
        val artist = track.id.removePrefix("remote:").substringBefore(':')
        if (track.source != TrackSource.FREZZAMUSIC_STREAM || artist !in listOf("Solasias","theFrezza") || track.remoteFileId.isNullOrBlank() || track.folder.isNullOrBlank()) return null
        return JSONObject().put("driveId",track.remoteFileId).put("folderId",track.folder).put("artist",artist).put("accountUid",account?.userId() ?: JSONObject.NULL)
    }
    fun queueLike(track: Track, liked: Boolean) {
        val data = metadata(track) ?: return
        synchronized(this) {
            val sequence = prefs.getLong("sequence",0) + 1
            val queue = JSONObject(prefs.getString("likes","{}")!!)
            queue.put(track.remoteFileId!!,data.put("liked",liked).put("sequence",sequence))
            prefs.edit().putLong("sequence",sequence).putString("likes",queue.toString()).commit()
        }
        scope.launch { runCatching { flush() } }
    }
    fun queueStart(track: Track, eventId: String) {
        val data = metadata(track) ?: return
        synchronized(this) {
            val queue = JSONObject(prefs.getString("plays","{}")!!)
            if (!queue.has(eventId)) queue.put(eventId,data.put("eventId",eventId).put("qualified",false).put("startSent",false).put("createdAt",System.currentTimeMillis()))
            prefs.edit().putString("plays",queue.toString()).commit()
        }
        scope.launch { runCatching { flush() } }
    }
    fun qualify(eventId: String) {
        synchronized(this) {
            val queue = JSONObject(prefs.getString("plays","{}")!!)
            queue.optJSONObject(eventId)?.put("qualified",true)
            prefs.edit().putString("plays",queue.toString()).commit()
        }
        scope.launch { runCatching { flush() } }
    }
    private suspend fun token(): String = registrationLock.withLock {
        prefs.getString("token",null)?.let { return@withLock it }
        val body = JSONObject().put("app",BuildConfig.PROJECT_MODE)
        val result = request("/api/register",body,null)
        result.getString("token").also { prefs.edit().putString("token",it).commit() }
    }
    fun configureIdentity(identity: PurchaseIdentity) { account=identity }
    fun syncLogin() {
        scope.launch {
            if(account?.userId()!=null) runCatching { request("/api/identify",JSONObject(),token()) }
        }
    }
    private suspend fun request(path: String, body: JSONObject?, token: String?): JSONObject {
        val builder = Request.Builder().url(BuildConfig.RANKINGS_API_URL + path)
        val payload=body?.let { JSONObject(it.toString()) }
        if(payload!=null && token!=null && path in listOf("/api/identify","/api/listen/start","/api/like")) {
            val identity=account
            val uid=identity?.userId()
            val expected=if(payload.has("accountUid")) if(payload.isNull("accountUid")) null else payload.getString("accountUid") else uid
            if(uid!=null && uid==expected) {
                val idToken=runCatching { identity?.idToken() }.getOrNull()
                if(idToken!=null && identity?.userId()==uid)payload.put("firebaseIdToken",idToken)
            }
        }
        if (payload != null) builder.post(payload.toString().toRequestBody("application/json".toMediaType()))
        if (token != null) builder.header("Authorization","Bearer $token")
        client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) throw RankingHttpError(response.code)
            val raw = response.body?.string() ?: throw IOException("Resposta vazia")
            if (raw.length > 512_000) throw IOException("Resposta inválida")
            return JSONObject(raw)
        }
    }
    private class RankingHttpError(val status: Int): IOException("Serviço indisponível: $status")
    private fun queue(name: String): JSONObject = synchronized(this) { JSONObject(prefs.getString(name,"{}")!!) }
    private fun change(name: String, key: String, action: (JSONObject,JSONObject?) -> Unit) = synchronized(this) {
        val q = JSONObject(prefs.getString(name,"{}")!!); action(q,q.optJSONObject(key)); prefs.edit().putString(name,q.toString()).commit(); Unit
    }
    suspend fun flush() = withContext(Dispatchers.IO) {
        flushLock.withLock {
            val likes = queue("likes"); val plays = queue("plays")
            if (likes.length()==0 && plays.length()==0) return@withLock
            val auth = token()
            for (id in likes.keys().asSequence().take(20).toList()) {
                val event = likes.getJSONObject(id)
                try {
                    request("/api/like",event,auth)
                    change("likes",id) { q,current -> if (current?.optLong("sequence") == event.optLong("sequence")) q.remove(id) }
                } catch (e: RankingHttpError) { if(e.status==404) change("likes",id) { q,_ -> q.remove(id) } else throw e }
            }
            for (id in plays.keys().asSequence().take(20).toList()) {
                val event = plays.getJSONObject(id)
                if (System.currentTimeMillis()-event.optLong("createdAt") > 86_400_000) { change("plays",id) { q,_ -> q.remove(id) }; continue }
                try {
                    if (!event.optBoolean("startSent")) {
                        request("/api/listen/start",event,auth)
                        change("plays",id) { _,current -> current?.put("startSent",true) }
                    }
                    if (event.optBoolean("qualified")) {
                        request("/api/listen/complete",JSONObject().put("eventId",id).put("listenedSeconds",30),auth)
                        change("plays",id) { q,_ -> q.remove(id) }
                    }
                } catch (e: RankingHttpError) {
                    if (e.status in listOf(404,410)) change("plays",id) { q,_ -> q.remove(id) }
                    else if (e.status!=409) throw e
                }
            }
        }
    }
    internal suspend fun rankings(tracks: List<Track>): RankingSnapshot = withContext(Dispatchers.IO) {
        val raw = request("/api/rankings",null,null)
        val lookup = tracks.filter { it.source==TrackSource.FREZZAMUSIC_STREAM }.associateBy { it.remoteFileId }
        val scopes = raw.getJSONObject("scopes")
        val result = scopes.keys().asSequence().associateWith { key ->
            val lists = scopes.getJSONObject(key)
            fun read(kind: String): List<RankedMusic> {
                val rows = lists.getJSONArray(kind)
                return (0 until rows.length()).mapNotNull { i ->
                    val row=rows.getJSONObject(i); val track=lookup[row.getString("driveId")]
                    if(track==null || row.optLong("total")<=0) null else RankedMusic(track,row.getLong("total"))
                }.take(10)
            }
            RankingLists(read("played"),read("liked"))
        }
        RankingSnapshot(result)
    }
}
