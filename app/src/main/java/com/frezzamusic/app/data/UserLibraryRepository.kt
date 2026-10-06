package com.frezzamusic.app.data
import android.content.Context
import com.frezzamusic.app.model.*
import org.json.*
class UserLibraryRepository(context: Context) { private val p=context.getSharedPreferences("user_library",Context.MODE_PRIVATE)
 fun artistImage(artist:String):String? = p.getString("artist_image:"+artist.lowercase(),null)
 fun setArtistImage(artist:String,uri:String){p.edit().putString("artist_image:"+artist.lowercase(),uri).apply()}
 fun clearArtistImage(artist:String){p.edit().remove("artist_image:"+artist.lowercase()).apply()}
 fun favorites():Set<String> = p.getStringSet("favorites", emptySet()) ?: emptySet()
 fun toggleFavorite(id:String){val s=favorites().toMutableSet();if(!s.add(id))s.remove(id);p.edit().putStringSet("favorites",s).apply()}
 fun playlists()=read("playlists").map{UserPlaylist(it.first,it.second.first,it.second.second)}
 fun queues()=read("queues").map{MusicQueue(it.first,it.second.first,it.second.second)}
 fun savePlaylist(x:UserPlaylist)=save("playlists",x.id,x.name,x.trackIds); fun saveQueue(x:MusicQueue)=save("queues",x.id,x.name,x.trackIds)
 fun deletePlaylist(id:String)=delete("playlists",id); fun deleteQueue(id:String)=delete("queues",id)
 fun addToPlaylist(playlistId:String,trackId:String){playlists().find{it.id==playlistId}?.let{if(trackId !in it.trackIds)savePlaylist(it.copy(trackIds=it.trackIds+trackId))}}
 fun removeFromPlaylist(playlistId:String,trackId:String){playlists().find{it.id==playlistId}?.let{savePlaylist(it.copy(trackIds=it.trackIds.filterNot{x->x==trackId}))}}
 fun history():List<PlayHistory>{val a=JSONArray(p.getString("history","[]"));return (0 until a.length()).map{a.getJSONObject(it)}.map{PlayHistory(it.getString("id"),it.getLong("at"),it.getInt("count"))}.sortedByDescending{it.playedAt}}
 fun recordPlay(id:String){val h=history();val old=h.find{it.trackId==id};val n=PlayHistory(id,System.currentTimeMillis(),(old?.playCount?:0)+1);val all=listOf(n)+h.filter{it.trackId!=id};val a=JSONArray();all.take(250).forEach{a.put(JSONObject().put("id",it.trackId).put("at",it.playedAt).put("count",it.playCount))};p.edit().putString("history",a.toString()).apply()}
 private fun read(k:String):List<Pair<String,Pair<String,List<String>>>>{val a=JSONArray(p.getString(k,"[]"));return (0 until a.length()).map{a.getJSONObject(it)}.map{j->j.getString("id") to (j.getString("name") to (0 until j.getJSONArray("tracks").length()).map{j.getJSONArray("tracks").getString(it)})}}
 private fun save(k:String,id:String,name:String,t:List<String>){val old=JSONArray(p.getString(k,"[]"));val out=JSONArray();for(i in 0 until old.length()){val j=old.getJSONObject(i);if(j.getString("id")!=id)out.put(j)};out.put(JSONObject().put("id",id).put("name",name).put("tracks",JSONArray(t)));p.edit().putString(k,out.toString()).apply()}
 private fun delete(k:String,id:String){val old=JSONArray(p.getString(k,"[]"));val out=JSONArray();for(i in 0 until old.length()){val j=old.getJSONObject(i);if(j.getString("id")!=id)out.put(j)};p.edit().putString(k,out.toString()).apply()}
}
