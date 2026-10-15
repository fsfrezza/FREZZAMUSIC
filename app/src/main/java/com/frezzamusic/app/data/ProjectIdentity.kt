package com.frezzamusic.app.data
data class ProjectIdentity(val name:String,val subtitle:String,val about:String,val editorialUrl:String?=null)
object ProjectIdentities{
 fun forMode(mode:String)=when(mode){
  "SOLASIAS"->ProjectIdentity("Solasias","Projeto musical virtual","Solasias é um projeto musical virtual. O aplicativo reúne música, discografia, letras, lançamentos e conteúdo editorial em uma experiência própria.",EditorialSources.SOLASIAS_BLOG_ALBUMS)
  "THEFREZZA"->ProjectIdentity("theFrezza","Projeto musical virtual","theFrezza é um projeto musical virtual. O aplicativo reúne seus álbuns, faixas, letras, lançamentos e materiais editoriais.")
  else->ProjectIdentity("FREZZAMUSIC","Player e plataforma musical","FREZZAMUSIC combina biblioteca local com streaming dos projetos oficiais. Solasias e theFrezza também podem ser distribuídos como aplicativos artísticos próprios.")
 }
}
