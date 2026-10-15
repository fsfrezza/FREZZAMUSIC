package com.frezzamusic.app.data
import org.junit.Test
import org.junit.Assert.*
import com.frezzamusic.app.model.Track
class AlbumFolderOrderTest {
 private fun names(vararg n:String)=n.mapIndexed{i,v->i.toString() to v}
 @Test fun recognizesCompleteSiblingSeries(){assertEquals(mapOf("0" to 3,"1" to 1,"2" to 2),AlbumFolderOrder.detect(names("03 - Third","01 - First","02 - Second")))}
 @Test fun currentDriveRootsAreRecognized(){for((fixture,count) in listOf("root-frezza" to 14,"root-solasias" to 9)){val folders=DriveFolderParser.parse(javaClass.getResource("/$fixture.html")!!.readText()).filter{it.folder};assertEquals(count,AlbumFolderOrder.detect(folders.map{it.id to it.name}).size)}}
 @Test fun rejectsIncidentalNumbers(){for(n in listOf(names("1 - Alone"),names("1 - A","2 - B"),names("01 - A","03 - B","04 - C"),names("2020 - A","2021 - B","2022 - C"),names("01 - A","02 - B","Other"),names("01 - A","02. B","03 - C"),names("01 - A","01 - B","02 - C")))assertTrue(AlbumFolderOrder.detect(n).isEmpty())}
 @Test fun deviceRequiresSiblingAlbumsAndPreservesOtherDates(){val tracks=(1..3).map{Track("$it","Song","Band","Album $it","",dateMs=(4-it)*1000L)};val paths=tracks.associate{it.id to "Music/Band/0${it.id} - Album ${it.id}/"};val annotated=AlbumFolderOrder.device(tracks,paths);assertEquals(listOf(1,2,3),annotated.map{it.albumOrder});assertEquals(listOf("1","2","3"),AlbumFolderOrder.local(annotated.map{listOf(it)}).map{it.first().id});assertEquals(listOf("3","2","1"),AlbumFolderOrder.local(tracks.map{listOf(it)}).map{it.first().id});assertTrue(AlbumFolderOrder.device(tracks.map{it.copy(album="Same")},paths).all{it.albumOrder==null})}
}
