package com.frezzamusic.app.data
import android.content.Context
class SettingsRepository(context:Context){private val p=context.getSharedPreferences("settings",Context.MODE_PRIVATE)
 var dark:Boolean get()=p.getBoolean("dark",true);set(v){p.edit().putBoolean("dark",v).apply()}
 var crossfadeSeconds:Int get()=p.getInt("crossfade",0);set(v){p.edit().putInt("crossfade",v.coerceIn(0,12)).apply()}
 var speed:Float get()=p.getFloat("speed",1f);set(v){p.edit().putFloat("speed",v.coerceIn(.5f,2f)).apply()}
 var lastTrackId:String? get()=p.getString("lastTrack",null);set(v){p.edit().putString("lastTrack",v).apply()}
 var lastPositionMs:Long get()=p.getLong("lastPositionMs",0L);set(v){p.edit().putLong("lastPositionMs",v.coerceAtLeast(0L)).apply()}
 var shuffle:Boolean get()=p.getBoolean("shuffle",false);set(v){p.edit().putBoolean("shuffle",v).apply()}
 var repeatMode:Int get()=p.getInt("repeatMode",0);set(v){p.edit().putInt("repeatMode",v).apply()}
}
