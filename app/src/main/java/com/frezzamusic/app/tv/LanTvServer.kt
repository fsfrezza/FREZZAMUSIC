package com.frezzamusic.app.tv

import android.content.Context
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

    fun publish(page: Int, title: String, artist: String, album: String, lrc: String?, positionMs: Long, playing: Boolean) {
        current.set(JSONObject().put("page", when(page){1->"lyrics";2->"queue";3->"audio";else->"playing"})
            .put("title",title).put("artist",artist).put("album",album)
            .put("lrc",lrc?.take(60000).orEmpty()).put("positionMs",positionMs)
            .put("playing",playing))
    }

    fun stop() { runCatching { server?.close() }; executor.shutdownNow() }

    private fun serve(socket: Socket) = socket.use { connection ->
        runCatching {
            connection.soTimeout = 5000
            val input = connection.getInputStream().bufferedReader()
            val request = input.readLine() ?: return@use
            val target = request.split(" ").getOrNull(1) ?: "/"
            while (true) { val header = input.readLine() ?: break; if (header.isEmpty()) break }
            val query = target.substringAfter('?', "").split('&').mapNotNull {
                val parts = it.split('=',limit=2); if(parts.size==2) parts[0] to URLDecoder.decode(parts[1],"UTF-8") else null
            }.toMap()
            val ok = query["code"] == token
            val path = target.substringBefore('?')
            val mime: String
            val body: String
            val status: String
            if (path == "/") {
                mime="text/html; charset=utf-8";status="200 OK"
                body = """<!doctype html><html lang="pt-BR"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><style>body{background:#101724;color:white;font:24px sans-serif;text-align:center;padding:5vw}input,button{font:28px sans-serif;padding:12px;margin:8px}#line{font-size:4vw;margin:5vh}small{color:#bbb}</style><h1>FREZZAMUSIC TV</h1><p>Digite o código mostrado no celular</p><input id="code" maxlength="6" inputmode="numeric" placeholder="Código"><button onclick="go()">Conectar</button><div id="view" hidden><h2 id="title"></h2><p id="artist"></p><div id="line"></div><small id="status"></small></div><script>let c='';function go(){c=document.getElementById('code').value;if(!/^\\d{6}$/.test(c))return;document.getElementById('view').hidden=false;refresh();setInterval(refresh,1000)}async function refresh(){if(!c)return;try{const r=await fetch('/state?code='+encodeURIComponent(c),{cache:'no-store'});if(!r.ok)throw Error();const d=await r.json();document.getElementById('title').textContent=d.title||'';document.getElementById('artist').textContent=d.artist||'';document.getElementById('status').textContent=(d.page||'')+' • '+(d.playing?'Reproduzindo':'Pausado');if(d.page==='lyrics'){const rows=(d.lrc||'').split(/\\r?\\n/).map(x=>{let m=x.match(/\\[(\\d+):(\\d+(?:\\.\\d+)?)\\]/);return m?{t:(Number(m[1])*60+Number(m[2]))*1000,text:x.replace(/\\[[^\\]]*\\]/g,'')}:null}).filter(Boolean).sort((a,b)=>a.t-b.t);document.getElementById('line').textContent=rows.filter(x=>x.t<=d.positionMs).at(-1)?.text||'';}else document.getElementById('line').textContent=d.album||'';}catch(e){document.getElementById('status').textContent='Não conectado / código inválido';}}</script></html>"""
            } else if(path=="/state" && ok) {
                mime="application/json; charset=utf-8";status="200 OK";body=current.get().toString()
            } else {
                mime="text/plain; charset=utf-8";status="403 Forbidden";body="Código inválido"
            }
            val bytes=body.toByteArray(Charsets.UTF_8)
            val output=connection.getOutputStream()
            output.write(("HTTP/1.1 $status\r\nContent-Type: $mime\r\nCache-Control: no-store\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n").toByteArray())
            output.write(bytes);output.flush()
        }
    }
}
