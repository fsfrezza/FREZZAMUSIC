package com.frezzamusic.app.tv

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.util.Xml
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.MulticastSocket
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

data class DlnaDevice(val name: String, val controlUrl: String, val usn: String)

/** Discover UPnP MediaRenderers on the local network using SSDP; no Chromecast needed. */
object DlnaDiscovery {
    private val io = Executors.newCachedThreadPool()
    private val main = Handler(Looper.getMainLooper())
    fun scan(context: Context, done: (List<DlnaDevice>, String?) -> Unit) {
        io.execute {
            val lock = runCatching {
                (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
                    .createMulticastLock("frezzamusic-ssdp").also { it.setReferenceCounted(false); it.acquire() }
            }.getOrNull()
            val devices = linkedMapOf<String, DlnaDevice>()
            var error: String? = null
            try {
                MulticastSocket().use { socket ->
                    socket.soTimeout = 1300
                    val targets = listOf("urn:schemas-upnp-org:device:MediaRenderer:1", "ssdp:all")
                    for (target in targets) {
                        val request = ("M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\nST: $target\r\n\r\n").toByteArray()
                        socket.send(DatagramPacket(request, request.size, InetAddress.getByName("239.255.255.250"), 1900))
                    }
                    val until = System.currentTimeMillis() + 4500L
                    while (System.currentTimeMillis() < until) {
                        try {
                            val data = ByteArray(8192)
                            val packet = DatagramPacket(data, data.size)
                            socket.receive(packet)
                            val response = String(packet.data, 0, packet.length)
                            val location = Regex("(?im)^LOCATION:\\s*(\\S+)").find(response)?.groupValues?.get(1) ?: continue
                            if (devices.containsKey(location)) continue
                            resolve(location)?.let { devices[location] = it }
                        } catch (_: java.net.SocketTimeoutException) { }
                    }
                }
            } catch (e: Exception) { error = e.message ?: "Falha na descoberta de dispositivos" }
            finally { runCatching { lock?.release() } }
            main.post { done(devices.values.distinctBy { it.usn }, error) }
        }
    }

    private fun resolve(location: String): DlnaDevice? = runCatching {
        val uri = java.net.URI(location)
        if (uri.scheme != "http" || uri.host.isNullOrBlank()) return@runCatching null
        val c = URL(location).openConnection() as HttpURLConnection
        c.connectTimeout = 1500; c.readTimeout = 1500
        val factory = DocumentBuilderFactory.newInstance()
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        val doc = c.inputStream.use { factory.newDocumentBuilder().parse(it) }
        val type = doc.getElementsByTagName("deviceType")
        if ((0 until type.length).none { type.item(it).textContent.contains("MediaRenderer") }) return@runCatching null
        val services = doc.getElementsByTagName("service")
        var control: String? = null
        for (i in 0 until services.length) {
            val e = services.item(i) as? Element ?: continue
            if (e.getElementsByTagName("serviceType").item(0)?.textContent?.contains("AVTransport") != true) continue
            control = e.getElementsByTagName("controlURL").item(0)?.textContent
            break
        }
        val url = control?.let { uri.resolve(it).toString() } ?: return@runCatching null
        val friendly = doc.getElementsByTagName("friendlyName").item(0)?.textContent?.trim().orEmpty().ifBlank { uri.host }
        val udn = doc.getElementsByTagName("UDN").item(0)?.textContent ?: url
        DlnaDevice(friendly, url, udn)
    }.getOrNull()
}

object DlnaTransport {
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    fun play(device: DlnaDevice, mediaUrl: String, title: String, result: (Boolean, String) -> Unit) {
        io.execute {
            val reply = runCatching {
                command(device, "SetAVTransportURI",
                    "<CurrentURI>${xml(mediaUrl)}</CurrentURI><CurrentURIMetaData></CurrentURIMetaData>")
                command(device, "Play", "<Speed>1</Speed>")
                true to "Reprodução enviada à TV"
            }.getOrElse { false to ("TV recusou a reprodução: " + (it.message ?: "erro de rede")) }
            main.post { result(reply.first, reply.second) }
        }
    }
    fun pause(device: DlnaDevice, result: (Boolean) -> Unit) = simple(device,"Pause", result)
    fun resume(device: DlnaDevice, result: (Boolean) -> Unit) = simple(device,"Play", result, "<Speed>1</Speed>")
    private fun simple(device:DlnaDevice,action:String,result:(Boolean)->Unit,extra:String="") {
        io.execute { val success=runCatching { command(device,action,extra);true }.getOrDefault(false);main.post{result(success)} }
    }
    private fun xml(s:String) = s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;")
    private fun command(device:DlnaDevice, action:String, parameters:String) {
        val soap = """<?xml version="1.0" encoding="utf-8"?><s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"><s:Body><u:$action xmlns:u="urn:schemas-upnp-org:service:AVTransport:1"><InstanceID>0</InstanceID>$parameters</u:$action></s:Body></s:Envelope>"""
        val conn = URL(device.controlUrl).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST";conn.doOutput = true
            conn.connectTimeout = 4000;conn.readTimeout = 5000
            conn.setRequestProperty("Content-Type","text/xml; charset=\"utf-8\"")
            conn.setRequestProperty("SOAPAction", "\"urn:schemas-upnp-org:service:AVTransport:1#$action\"")
            conn.outputStream.use { it.write(soap.toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode !in 200..299) throw IllegalStateException("HTTP ${conn.responseCode}")
        } finally { conn.disconnect() }
    }
}
