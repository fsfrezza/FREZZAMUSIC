package com.frezzamusic.app.tv

import android.content.Context
import android.net.Uri
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import android.net.wifi.WifiManager
import android.text.format.Formatter
import org.json.JSONObject
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.SecureRandom
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

/** LAN-only HTTP receiver and control endpoint. No Google Cast dependency. */
class LanTvServer(private val context: Context) {
    private val executor = Executors.newCachedThreadPool()
    private val current = AtomicReference(JSONObject())
    private val token = (100000 + SecureRandom().nextInt(900000)).toString()
    private var server: ServerSocket? = null
    private val audioUri = AtomicReference<String?>(null)
    private val audioId = AtomicReference("")
    private val paired = java.util.concurrent.atomic.AtomicBoolean(false)
    fun hasPairedReceiver(): Boolean = paired.get()
    fun setAudioSource(uri: String?) { if (audioUri.getAndSet(uri) != uri) audioId.set(java.util.UUID.randomUUID().toString()) }
    val address: String? get() {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return null
        @Suppress("DEPRECATION") val ip = Formatter.formatIpAddress(wm.connectionInfo.ipAddress)
        if (ip == "0.0.0.0") return null
        return "http://$ip:${server?.localPort ?: return null}"
    }
    val pairingCode: String get() = token

    fun start(): Boolean = try {
        server = ServerSocket(0)
        executor.execute { while (server?.isClosed == false) {
            val client = runCatching { server?.accept() }.getOrNull() ?: break
            executor.execute { serve(client) }
        }}
        true
    } catch (_: Exception) { false }

    fun publish(page: Int, title: String, artist: String, album: String, lrc: String?, positionMs: Long, playing: Boolean, tvAudio: Boolean) {
        current.set(JSONObject().put("page", when(page){1->"lyrics";2->"queue";3->"audio";else->"playing"})
            .put("title",title).put("artist",artist).put("album",album)
            .put("lrc",lrc?.take(60000).orEmpty()).put("positionMs",positionMs)
            .put("playing",playing).put("tvAudio",tvAudio).put("audioId",audioId.get()))
    }

    fun stop() { runCatching { server?.close() }; executor.shutdownNow() }

    private fun serve(socket: Socket) = socket.use { connection ->
        runCatching {
            connection.soTimeout = 5000
            val input = connection.getInputStream().bufferedReader()
            val request = input.readLine() ?: return@use
            val target = request.split(" ").getOrNull(1) ?: "/"
            var rangeHeader: String? = null
            while (true) {
                val header = input.readLine() ?: break
                if (header.isEmpty()) break
                if (header.startsWith("Range:",true)) rangeHeader = header.substringAfter(':').trim()
            }
            val query = target.substringAfter('?', "").split('&').mapNotNull {
                val parts = it.split('=',limit=2); if(parts.size==2) parts[0] to URLDecoder.decode(parts[1],"UTF-8") else null
            }.toMap()
            val ok = query["code"] == token
            val path = target.substringBefore('?')
            val mime: String
            val body: String
            val status: String
            if (path == "/audio" && ok) {
                streamAudio(connection, rangeHeader)
                return@use
            }
            if (path == "/") {
                mime="text/html; charset=utf-8";status="200 OK"
                body = """<!doctype html><html lang="pt-BR"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><style>body{background:#101724;color:white;font:24px sans-serif;text-align:center;padding:5vw}input,button{font:28px sans-serif;padding:12px;margin:8px}#line{font-size:4vw;margin:5vh}small{color:#bbb}</style><h1>FREZZAMUSIC TV</h1><p>Digite o código mostrado no celular</p><input id="code" maxlength="6" inputmode="numeric" placeholder="Código"><button onclick="go()">Conectar</button><div id="view" hidden><h2 id="title"></h2><p id="artist"></p><div id="line"></div><audio id="audio" controls></audio><small id="status"></small></div><script>let c='';function go(){c=document.getElementById('code').value;if(!/^\\d{6}$/.test(c))return;document.getElementById('view').hidden=false;document.getElementById('audio').src='/audio?code='+encodeURIComponent(c);refresh();setInterval(refresh,1000)}async function refresh(){if(!c)return;try{const r=await fetch('/state?code='+encodeURIComponent(c),{cache:'no-store'});if(!r.ok)throw Error();const d=await r.json();document.getElementById('title').textContent=d.title||'';document.getElementById('artist').textContent=d.artist||'';document.getElementById('status').textContent=(d.page||'')+' • '+(d.playing?'Reproduzindo':'Pausado');const a=document.getElementById('audio');if(d.audioId!==window.audioId){window.audioId=d.audioId;a.src='/audio?code='+encodeURIComponent(c)+'&v='+encodeURIComponent(d.audioId||'');if(d.tvAudio)a.play().catch(()=>{});}if(!d.tvAudio && !a.paused)a.pause();if(d.tvAudio&&a.paused)a.play().catch(()=>{});if(!d.tvAudio&&Number.isFinite(d.positionMs)&&Math.abs(a.currentTime-d.positionMs/1000)>2.5&&a.seekable.length){try{a.currentTime=d.positionMs/1000}catch(_){}}if(d.page==='lyrics'){const rows=(d.lrc||'').split(/\\r?\\n/).map(x=>{let m=x.match(/\\[(\\d+):(\\d+(?:\\.\\d+)?)\\]/);return m?{t:(Number(m[1])*60+Number(m[2]))*1000,text:x.replace(/\\[[^\\]]*\\]/g,'')}:null}).filter(Boolean).sort((a,b)=>a.t-b.t);document.getElementById('line').textContent=rows.filter(x=>x.t<=d.positionMs).at(-1)?.text||'';}else document.getElementById('line').textContent=d.album||'';}catch(e){document.getElementById('status').textContent='Não conectado / código inválido';}}</script></html>"""
            } else if(path=="/state" && ok) {
                mime="application/json; charset=utf-8";status="200 OK";paired.set(true);body=current.get().toString()
            } else {
                mime="text/plain; charset=utf-8";status="403 Forbidden";body="Código inválido"
            }
            val bytes=body.toByteArray(Charsets.UTF_8)
            val output=connection.getOutputStream()
            output.write(("HTTP/1.1 $status\r\nContent-Type: $mime\r\nCache-Control: no-store\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n").toByteArray())
            output.write(bytes);output.flush()
        }
    }
    private fun streamAudio(socket: Socket, range: String?) {
        val source = audioUri.get() ?: return
        val uri = Uri.parse(source)
        var stream: InputStream? = null
        var remote: HttpURLConnection? = null
        try {
            val offset = Regex("^bytes=(\\d+)-").find(range.orEmpty())?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            val length: Long
            val mime: String
            when (uri.scheme?.lowercase()) {
                "http","https" -> {
                    remote = URL(source).openConnection() as HttpURLConnection
                    remote.connectTimeout=10000
                    remote.readTimeout=15000
                    remote.instanceFollowRedirects=true
                    if(range!=null) remote.setRequestProperty("Range",range)
                    remote.connect()
                    if(remote.responseCode !in listOf(200,206)) return
                    stream=remote.inputStream
                    length=remote.contentLengthLong
                    mime=remote.contentType?.substringBefore(';') ?: "audio/mpeg"
                }
                "content","file" -> {
                    val afd=context.contentResolver.openAssetFileDescriptor(uri,"r") ?: return
                    length=afd.length
                    afd.close()
                    stream=context.contentResolver.openInputStream(uri) ?: return
                    var left=offset
                    while(left>0) { val n=stream.skip(left); if(n<=0) break;left-=n }
                    mime=context.contentResolver.getType(uri) ?: "audio/mpeg"
                }
                else -> return
            }
            val total = if(remote!=null) length else length
            val start=if(remote?.responseCode==206) offset else if(remote==null) offset else 0L
            val remaining=if(remote==null && total>=0) (total-start).coerceAtLeast(0) else length
            val partial=(remote?.responseCode==206)||(remote==null&&offset>0&&total>=0)
            val headers=buildString {
                append("HTTP/1.1 ${if(partial) "206 Partial Content" else "200 OK"}\r\n")
                append("Content-Type: $mime\r\n")
                append("Accept-Ranges: bytes\r\n")
                if(remaining>=0)append("Content-Length: $remaining\r\n")
                if(partial&&total>=0)append("Content-Range: bytes $start-${total-1}/$total\r\n")
                append("Cache-Control: no-store\r\nConnection: close\r\n\r\n")
            }
            val out=socket.getOutputStream()
            out.write(headers.toByteArray(Charsets.UTF_8))
            val buf=ByteArray(32*1024)
            var count=0L
            while(true) {
                val n=stream.read(buf)
                if(n<0)break
                if(remaining>=0 && count+n>remaining) {
                    val allowed=(remaining-count).toInt()
                    if(allowed>0)out.write(buf,0,allowed)
                    break
                }
                out.write(buf,0,n);count+=n
            }
            out.flush()
        } catch (_: Exception) {
        } finally {
            runCatching { stream?.close() }
            remote?.disconnect()
        }
    }

}
