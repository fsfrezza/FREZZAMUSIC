package com.frezzamusic.app.data

import java.time.LocalDate
import java.time.YearMonth
import java.nio.charset.Charset

internal object ReleaseDates {
 fun normalize(value:String?):String? {
  val s=value?.trim()?.trim('\u0000')?:return null
  return try {
   when {
    s.matches(Regex("[0-9]{4}"))->s.takeIf{it.toInt() in 1000..2999}
    s.matches(Regex("[0-9]{4}-[0-9]{2}"))->YearMonth.parse(s).toString()
    Regex("^[0-9]{4}-[0-9]{2}-[0-9]{2}(?:T.*)?$").matches(s)->LocalDate.parse(s.take(10)).toString()
    s.matches(Regex("[0-9]{2}/[0-9]{2}/[0-9]{4}"))->LocalDate.of(s.takeLast(4).toInt(),s.substring(3,5).toInt(),s.take(2).toInt()).toString()
    else->null
   }
  }catch(_:Exception){null}
 }
 fun detailed(value:String?)=normalize(value)?.length?.let{it>4}?:false
 /** Only a strictly greater-than-half agreement may represent the album. */
 fun majority(values:List<String?>):String? {
  if(values.isEmpty())return null
  val valid=values.mapNotNull(::normalize)
  val exact=valid.filter(::detailed).groupingBy{it}.eachCount().entries.firstOrNull{it.value>values.size/2}
  if(exact!=null)return exact.key
  return valid.groupingBy{it.take(4)}.eachCount().entries.firstOrNull{it.value>values.size/2}?.key
 }
 fun fromId3(bytes:ByteArray):String? {
  if(bytes.size<10 || String(bytes,0,3,Charsets.ISO_8859_1)!="ID3")return null
  val version=bytes[3].toInt();if(version !in 3..4 || (bytes[5].toInt() and 0x80)!=0)return null
  fun size(at:Int,sync:Boolean):Int? {if(at+4>bytes.size)return null;var n=0L;for(i in at until at+4){val v=bytes[i].toInt() and 255;if(sync && v>127)return null;n=(n shl if(sync)7 else 8)+v};return n.takeIf{it<=Int.MAX_VALUE}?.toInt()}
  var at=10
  if((bytes[5].toInt() and 0x40)!=0){val ext=size(at,version==4)?:return null;at+=if(version==3)ext+4 else ext}
  val fields=mutableMapOf<String,String>()
  while(at+10<=bytes.size){
   val id=String(bytes,at,4,Charsets.ISO_8859_1);if(!id.matches(Regex("[A-Z0-9]{4}")))break
   val n=size(at+4,version==4)?:break;if(n<=0 || n>bytes.size-at-10)break
   val flag=bytes[at+9].toInt() and 255
   if(id in setOf("TDRL","TDOR","TDRC","TYER","TDAT") && flag==0){
    val encoding=bytes[at+10].toInt();val charset=when(encoding){0->Charsets.ISO_8859_1;1->Charsets.UTF_16;2->Charsets.UTF_16BE;3->Charsets.UTF_8;else->null}
    if(charset!=null && n>1)fields[id]=String(bytes,at+11,n-1,charset).trim('\u0000',' ','\n','\r')
   }
   at+=10+n
  }
  for(id in listOf("TDRL","TDOR","TDRC")){normalize(fields[id])?.let{return it}}
  val year=normalize(fields["TYER"])?:return null
  val day=fields["TDAT"]
  if(year.length==4 && day?.matches(Regex("[0-9]{4}"))==true)normalize("$year-${day.takeLast(2)}-${day.take(2)}")?.let{return it}
  return year
 }
}
