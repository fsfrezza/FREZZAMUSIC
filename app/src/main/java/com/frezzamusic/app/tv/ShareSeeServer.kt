package com.frezzamusic.app.tv

import android.content.Context
import android.net.wifi.WifiManager
import android.text.format.Formatter
import androidx.media3.common.MediaItem
import java.net.*
import java.io.*
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

/**
 * Experimental UPnP AV MediaServer (DMS) for TCL Share&See.
 * Browse exposes only the current player queue, not every file on the handset.
 * Keep the phone and television on a trusted LAN.
 */
class ShareSeeServer(private val context: Context) {
    private val workers = Executors.newCachedThreadPool()
    private var http: ServerSocket? = null
    private var multicast: MulticastSocket? = null
    private val tracks = AtomicReference<List<MediaItem>>(emptyList())
    private val udn = "uuid:" + UUID.nameUUIDFromBytes(("frezzamusic:" + context.packageName).toByteArray()).toString()
    @Volatile private var running = false
    val address: String? get() {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return null
        @Suppress("DEPRECATION") val ip = Formatter.formatIpAddress(wifi.connectionInfo.ipAddress)
        return if(ip=="0.0.0.0" || !running) null else "http://$ip:${http?.localPort}"
    }
    fun update(items: List<MediaItem>) { tracks.set(items.take(300).filter { it.localConfiguration?.uri != null }) }
    fun start(): Boolean = try {
        http = ServerSocket(0)
        running = true
        workers.execute { while(running) { val client=runCatching { http?.accept() }.getOrNull() ?: break; workers.execute { handle(client) } } }
        workers.execute { advertise() }
        true
    } catch (_:Exception) { stop();false }
    fun stop() { running=false;runCatching { multicast?.close() };runCatching { http?.close() };workers.shutdownNow() }

    private fun advertise() {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val lock = wifi?.createMulticastLock("frezzamusic-dlna")?.apply { setReferenceCounted(false);acquire() }
        try {
            MulticastSocket(1900).use { socket ->
                multicast=socket
                socket.reuseAddress=true
                socket.soTimeout=2500
                socket.joinGroup(InetAddress.getByName("239.255.255.250"))
                var last=0L
                while(running) {
                    if(System.currentTimeMillis()-last>25000) { notifyAlive(socket);last=System.currentTimeMillis() }
                    try {
                        val b=ByteArray(8192);val request=DatagramPacket(b,b.size);socket.receive(request)
                        val message=String(b,0,request.length)
                        if(message.startsWith("M-SEARCH",true) && message.contains("ssdp:discover",true)) {
                            val st=Regex("(?im)^ST:\\s*(\\S+)").find(message)?.groupValues?.get(1).orEmpty()
                            if(st=="ssdp:all" || st.contains("MediaServer") || st=="upnp:rootdevice") {
                                respond(socket,request.address,request.port,if(st=="ssdp:all") "urn:schemas-upnp-org:device:MediaServer:1" else st)
                            }
                        }
                    } catch (_:SocketTimeoutException) { }
                }
            }
        } catch (_:Exception) { } finally { runCatching{lock?.release()} }
    }
    private fun notifyAlive(s:MulticastSocket) {
        val base=address ?: return
        for(nt in listOf("upnp:rootdevice",udn,"urn:schemas-upnp-org:device:MediaServer:1")){
            val msg="NOTIFY * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nCACHE-CONTROL: max-age=1800\r\nLOCATION: $base/device.xml\r\nNT: $nt\r\nNTS: ssdp:alive\r\nSERVER: Android/1.0 UPnP/1.0 FREZZAMUSIC/1.0\r\nUSN: $udn::$nt\r\n\r\n"
            val data=msg.toByteArray();s.send(DatagramPacket(data,data.size,InetAddress.getByName("239.255.255.250"),1900))
        }
    }
    private fun respond(s:MulticastSocket,host:InetAddress,port:Int,st:String){
        val msg="HTTP/1.1 200 OK\r\nCACHE-CONTROL: max-age=1800\r\nEXT:\r\nLOCATION: ${address}/device.xml\r\nSERVER: Android/1.0 UPnP/1.0 FREZZAMUSIC/1.0\r\nST: $st\r\nUSN: $udn::$st\r\n\r\n"
        val bytes=msg.toByteArray();s.send(DatagramPacket(bytes,bytes.size,host,port))
    }
    private fun handle(socket:Socket)=socket.use { c ->
        try {
            c.soTimeout=10000
            val input=c.getInputStream().bufferedReader()
            val first=input.readLine() ?: return@use
            val method=first.substringBefore(' ')
            val path=first.split(' ').getOrNull(1)?.substringBefore('?') ?: "/"
            var length=0;var range:String?=null;var soapAction=""
            while(true){
                val line=input.readLine() ?: return@use
                if(line.isEmpty())break
                when {
                    line.startsWith("content-length:",true)->length=line.substringAfter(':').trim().toIntOrNull()?.coerceIn(0,65536)?:0
                    line.startsWith("range:",true)->range=line.substringAfter(':').trim()
                    line.startsWith("soapaction:",true)->soapAction=line.substringAfter(':').trim()
                }
            }
            if(path.startsWith("/media/") && method=="GET") { serveMedia(c,path,range);return@use }
            val requestBody=if(method=="POST"&&length>0) CharArray(length).also {
                var n=0;while(n<it.size){val read=input.read(it,n,it.size-n);if(read<0)break;n+=read}
            }.concatToString() else ""
            val content=when(path) {
                "/device.xml"->deviceXml()
                "/content.xml"->serviceXml()
                "/connection.xml"->connectionXml()
                "/control"->soap(requestBody,soapAction)
                else -> ""
            }
            val status=if(content.isEmpty())"404 Not Found" else "200 OK"
            val type=if(path=="/control")"text/xml; charset=utf-8" else "text/xml; charset=utf-8"
            send(c,status,type,content.toByteArray())
        }catch(_:Exception){}
    }
    private fun send(s:Socket,status:String,mime:String,bytes:ByteArray){
        val out=s.getOutputStream()
        out.write(("HTTP/1.1 $status\r\nContent-Type: $mime\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n").toByteArray())
        out.write(bytes);out.flush()
    }
    private fun deviceXml():String="""<?xml version="1.0"?>
<root xmlns="urn:schemas-upnp-org:device-1-0"><specVersion><major>1</major><minor>0</minor></specVersion><device>
<deviceType>urn:schemas-upnp-org:device:MediaServer:1</deviceType><friendlyName>FREZZAMUSIC - Biblioteca</friendlyName>
<manufacturer>FREZZAMUSIC</manufacturer><modelName>FREZZAMUSIC DLNA</modelName><UDN>$udn</UDN>
<serviceList>
<service><serviceType>urn:schemas-upnp-org:service:ContentDirectory:1</serviceType><serviceId>urn:upnp-org:serviceId:ContentDirectory</serviceId><SCPDURL>/content.xml</SCPDURL><controlURL>/control</controlURL><eventSubURL>/events</eventSubURL></service>
<service><serviceType>urn:schemas-upnp-org:service:ConnectionManager:1</serviceType><serviceId>urn:upnp-org:serviceId:ConnectionManager</serviceId><SCPDURL>/connection.xml</SCPDURL><controlURL>/control</controlURL><eventSubURL>/events</eventSubURL></service>
</serviceList></device></root>"""
    private fun serviceXml():String="""<?xml version="1.0"?><scpd xmlns="urn:schemas-upnp-org:service-1-0"><specVersion><major>1</major><minor>0</minor></specVersion><actionList>
<action><name>Browse</name><argumentList><argument><name>ObjectID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_ObjectID</relatedStateVariable></argument><argument><name>BrowseFlag</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_BrowseFlag</relatedStateVariable></argument><argument><name>Filter</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_Filter</relatedStateVariable></argument><argument><name>StartingIndex</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_Index</relatedStateVariable></argument><argument><name>RequestedCount</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_Count</relatedStateVariable></argument><argument><name>SortCriteria</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_SortCriteria</relatedStateVariable></argument><argument><name>Result</name><direction>out</direction><relatedStateVariable>A_ARG_TYPE_Result</relatedStateVariable></argument><argument><name>NumberReturned</name><direction>out</direction><relatedStateVariable>A_ARG_TYPE_Count</relatedStateVariable></argument><argument><name>TotalMatches</name><direction>out</direction><relatedStateVariable>A_ARG_TYPE_Count</relatedStateVariable></argument><argument><name>UpdateID</name><direction>out</direction><relatedStateVariable>A_ARG_TYPE_UpdateID</relatedStateVariable></argument></argumentList></action>
<action><name>GetSystemUpdateID</name></action><action><name>GetSearchCapabilities</name></action><action><name>GetSortCapabilities</name></action></actionList>
<serviceStateTable>${listOf("ObjectID","BrowseFlag","Filter","Index","Count","SortCriteria","Result","UpdateID").joinToString(""){"<stateVariable sendEvents=\"no\"><name>A_ARG_TYPE_$it</name><dataType>string</dataType></stateVariable>"}}</serviceStateTable></scpd>"""
    private fun connectionXml()="""<?xml version="1.0"?><scpd xmlns="urn:schemas-upnp-org:service-1-0"><specVersion><major>1</major><minor>0</minor></specVersion><actionList><action><name>GetProtocolInfo</name></action></actionList><serviceStateTable></serviceStateTable></scpd>"""
    private fun soap(body:String,action:String):String {
        val result=when {
            action.contains("GetProtocolInfo")->"<Source>http-get:*:audio/mpeg:*,http-get:*:audio/mp4:*</Source><Sink></Sink>"
            action.contains("GetSystemUpdateID")->"<Id>1</Id>"
            action.contains("GetSearchCapabilities")->"<SearchCaps></SearchCaps>"
            action.contains("GetSortCapabilities")->"<SortCaps></SortCaps>"
            action.contains("Browse")-> {
                val objectId=Regex("<(?:\\w+:)?ObjectID>([^<]+)").find(body)?.groupValues?.get(1) ?: "0"
                val start=Regex("<(?:\\w+:)?StartingIndex>(\\d+)").find(body)?.groupValues?.get(1)?.toIntOrNull()?.coerceAtLeast(0) ?: 0
                val max=Regex("<(?:\\w+:)?RequestedCount>(\\d+)").find(body)?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(0,100) ?: 0
                val all=tracks.get()
                val metadata = body.contains("BrowseMetadata")
                val root = """<container id="0" parentID="-1" restricted="1" childCount="${all.size}" searchable="0"><dc:title>FREZZAMUSIC - Biblioteca</dc:title><upnp:class>object.container.storageFolder</upnp:class></container>"""
                val entries=if(metadata && objectId=="0") root else if(objectId=="0") all.drop(start).take(if(max==0)100 else max).mapIndexed { n,item ->
                    val idx=start+n
                    val title=escape(item.mediaMetadata.title?.toString() ?: "Música ${idx+1}")
                    val artist=escape(item.mediaMetadata.artist?.toString() ?: "")
                    val uri="${address}/media/$idx"
                    """<item id="$idx" parentID="0" restricted="1"><dc:title>$title</dc:title><upnp:artist>$artist</upnp:artist><upnp:class>object.item.audioItem.musicTrack</upnp:class><res protocolInfo="http-get:*:audio/mpeg:*">$uri</res></item>"""
                }.joinToString("") else ""
                val didl="""<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/">$entries</DIDL-Lite>"""
                "<Result>${escape(didl)}</Result><NumberReturned>${if(metadata&&objectId=="0") 1 else if(objectId=="0") minOf(if(max==0)100 else max,(all.size-start).coerceAtLeast(0)) else 0}</NumberReturned><TotalMatches>${if(metadata&&objectId=="0") 1 else if(objectId=="0")all.size else 0}</TotalMatches><UpdateID>1</UpdateID>"
            }
            else -> ""
        }
        val name=action.substringAfter('#').trim('"').takeIf{it.isNotEmpty()} ?: "Browse"
        val service=if(name=="GetProtocolInfo")"ConnectionManager" else "ContentDirectory"
        return """<?xml version="1.0"?><s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"><s:Body><u:$name xmlns:u="urn:schemas-upnp-org:service:$service:1">$result</u:$name></s:Body></s:Envelope>"""
    }
    private fun escape(s:String)=s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;")
    private fun serveMedia(c:Socket,path:String,range:String?) {
        val index=path.substringAfterLast('/').toIntOrNull() ?: return
        val uri=tracks.get().getOrNull(index)?.localConfiguration?.uri ?: return
        val source=when(uri.scheme?.lowercase()){
            "http","https"->URL(uri.toString()).openStream()
            "content","file"->context.contentResolver.openInputStream(uri)
            else->null
        } ?: return
        source.use {
            val offset=Regex("bytes=(\\d+)-").find(range.orEmpty())?.groupValues?.get(1)?.toLongOrNull()?:0L
            var pending=offset
            while(pending>0){val skip=it.skip(pending);if(skip<=0)break;pending-=skip}
            val out=c.getOutputStream()
            out.write(("HTTP/1.1 ${if(offset>0)"206 Partial Content" else "200 OK"}\r\nContent-Type: audio/mpeg\r\nAccept-Ranges: bytes\r\nConnection: close\r\n\r\n").toByteArray())
            it.copyTo(out,32768);out.flush()
        }
    }
}
