import test from "node:test";
import assert from "node:assert/strict";
import {mkdtempSync,mkdirSync,writeFileSync,symlinkSync,rmSync} from "node:fs";
import {tmpdir} from "node:os";
import {join} from "node:path";
import {createDownloadSigner} from "../src/download-signer.js";
import {createPrivateDownloadGateway} from "../src/private-download-gateway.js";

test("private gateway serves only correctly signed and unexpired file URLs",async()=>{
 const root=mkdtempSync(join(tmpdir(),"frezzamusic-private-"));
 mkdirSync(join(root,"album"));
 writeFileSync(join(root,"album","song.mp3"),Buffer.from("private-audio-test"));
 const secret="private-gateway-shared-key-at-least-32-bytes";
 const now=()=>1000000000000;
 const gateway=createPrivateDownloadGateway({rootDir:root,secret,now});
 await new Promise(resolve=>gateway.listen(0,"127.0.0.1",resolve));
 try{
  const origin="http://127.0.0.1:"+gateway.address().port;
  const signer=createDownloadSigner({baseUrl:"https://downloads.example.invalid/private",secret,objects:{"track":"album/song.mp3"},now});
  const signed=await signer({trackId:"track"});
  const url=origin+new URL(signed.url).pathname+new URL(signed.url).search;
  const valid=await fetch(url);
  assert.equal(valid.status,200);
  assert.equal(await valid.text(),"private-audio-test");
  assert.equal((await fetch(origin+"/private/album/song.mp3")).status,403);
  const tampered=new URL(url);tampered.searchParams.set("signature","0".repeat(64));
  assert.equal((await fetch(tampered)).status,403);
  const expired=createPrivateDownloadGateway({rootDir:root,secret,now:()=>1000000500000});
  await new Promise(resolve=>expired.listen(0,"127.0.0.1",resolve));
  try{
   const old=await fetch("http://127.0.0.1:"+expired.address().port+new URL(signed.url).pathname+new URL(signed.url).search);
   assert.equal(old.status,403);
  }finally{await new Promise(resolve=>expired.close(resolve));}
  symlinkSync(join(root,"album","song.mp3"),join(root,"album","alias.mp3"));
  const aliasSigner=createDownloadSigner({baseUrl:"https://downloads.example.invalid/private",secret,objects:{"alias":"album/alias.mp3"},now});
  const alias=await aliasSigner({trackId:"alias"});
  assert.equal((await fetch(origin+new URL(alias.url).pathname+new URL(alias.url).search)).status,403);
 }finally{await new Promise(resolve=>gateway.close(resolve));rmSync(root,{recursive:true,force:true});}
});
