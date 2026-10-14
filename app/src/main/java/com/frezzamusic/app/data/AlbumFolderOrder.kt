package com.frezzamusic.app.data

import com.frezzamusic.app.model.Album
import com.frezzamusic.app.model.Track

/** Recognizes a complete series of sibling albums, never a lone numeric title. */
internal object AlbumFolderOrder {
    private val prefix=Regex("^([0-9]{1,3})(\\s*[-.]\\s+)(\\S.*)$")
    fun detect(names: List<Pair<String,String>>): Map<String,Int> {
        if(names.size<3)return emptyMap()
        val parsed=names.map { (id,name)-> val m=prefix.matchEntire(name.trim())?:return emptyMap(); Triple(id,m.groupValues[1],m.groupValues[2].trim()) }
        if(parsed.map{it.third}.distinct().size!=1)return emptyMap()
        val digits=parsed.map{it.second}
        if(digits.any{it.startsWith('0')} && digits.map{it.length}.distinct().size!=1)return emptyMap()
        val numbers=digits.map{it.toInt()}
        if(numbers.sorted()!=(1..names.size).toList())return emptyMap()
        return parsed.mapIndexed{i,v->v.first to numbers[i]}.toMap()
    }
    private fun <T> reorder(base:List<T>,group:(T)->String?,order:(T)->Int?):List<T> {
        val result=base.toMutableList()
        base.indices.filter{group(base[it])!=null && order(base[it])!=null}.groupBy{group(base[it])}.values.forEach { slots ->
            val items=slots.map{base[it]}.sortedBy{order(it)}
            slots.forEachIndexed{i,slot->result[slot]=items[i]}
        }
        return result
    }
    fun albums(albums:List<Album>)=reorder(albums.sortedWith(compareBy<Album>{it.releaseDate.orEmpty()}.thenBy{it.title.lowercase(java.util.Locale.ROOT)}),{it.albumOrderGroup},{it.albumOrder})
    fun local(groups:List<List<Track>>):List<List<Track>> {
        fun identity(g:List<Track>):Pair<String,Int>? {
            val ids=g.map{it.albumOrderGroup to it.albumOrder}.distinct()
            val v=ids.singleOrNull()?:return null
            return if(v.first!=null && v.second!=null) v.first!! to v.second!! else null
        }
        return reorder(groups.sortedBy{g->g.maxOfOrNull{it.dateMs?:0L}?:0L},{identity(it)?.first},{identity(it)?.second})
    }
    fun device(tracks:List<Track>,paths:Map<String,String>):List<Track> {
        val folders=tracks.filter{paths[it.id]!=null}.groupBy{paths.getValue(it.id).trimEnd('/')}
        val assigned=mutableMapOf<String,Pair<String,Int>>()
        folders.keys.groupBy{it.substringBeforeLast('/',"")}.forEach { (parent,children)->
            val albums=children.map { path->folders.getValue(path).map{it.artist to it.album}.distinct() }
            if(albums.any{it.size!=1} || albums.flatten().distinct().size!=children.size)return@forEach
            val order=detect(children.map{it to it.substringAfterLast('/')})
            order.forEach{(path,n)->folders.getValue(path).forEach{assigned[it.id]=("device:$parent" to n)}}
        }
        return tracks.map { t->assigned[t.id]?.let{t.copy(albumOrderGroup=it.first,albumOrder=it.second)}?:t }
    }
}
