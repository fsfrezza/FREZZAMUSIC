package com.frezzamusic.app.data

data class LrcLine(val timeMs:Long,val text:String)
object LrcParser {
    private val stamp = Regex("\\[(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?]")
    fun parse(text:String):List<LrcLine> = text.lineSequence().flatMap { line ->
        val matches=stamp.findAll(line).toList()
        val lyric=line.replace(stamp, "").trim()
        matches.asSequence().map { m ->
            val min=m.groupValues[1].toLong(); val sec=m.groupValues[2].toLong()
            val frac=m.groupValues[3].ifBlank{"0"}.let { if(it.length==2) it.toLong()*10 else it.padEnd(3,'0').take(3).toLong() }
            LrcLine((min*60+sec)*1000+frac, lyric)
        }
    }.sortedBy { it.timeMs }.toList()
    fun current(lines:List<LrcLine>,positionMs:Long,offsetMs:Long=0):Int = lines.indexOfLast { it.timeMs <= positionMs + offsetMs }
}
