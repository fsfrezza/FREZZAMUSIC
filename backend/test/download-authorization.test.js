import test from "node:test";
import assert from "node:assert/strict";
import {createAppServer} from "../src/server.js";
import {PostgresOrderStore} from "../src/postgres-orders.js";

test("download rights query requires a paid non-revoked purchase",async()=>{
 let sql="",params;
 const pool={connect:async()=>({query:async()=>({rows:[]}),release(){}}),query:async(q,p)=>{sql=q;params=p;return {rows:[{allowed:true}]};}};
 const allowed=await new PostgresOrderStore(pool).canDownloadTrack("buyer","track-a");
 assert.equal(allowed,true);
 assert.deepEqual(params,["buyer","track-a"]);
 assert.match(sql,/revoked_at IS NULL/);
 assert.match(sql,/status='paid'/);
 assert.match(sql,/track_id=\$2/);
});
test("download authorization refuses unauthenticated, unpaid and unconfigured requests",async()=>{
 let entitled=false;
 const server=createAppServer([],{verifyIdentity:async auth=>auth==="Bearer valid"?{userId:"buyer"}:null,
  store:{canDownloadTrack:async(user,track)=>{assert.equal(user,"buyer");assert.equal(track,"track-a");return entitled;}}});
 await new Promise(resolve=>server.listen(0,"127.0.0.1",resolve));
 const url="http://127.0.0.1:"+server.address().port+"/v1/downloads/track-a/authorize";
 try{
  assert.equal((await fetch(url,{method:"POST"})).status,401);
  assert.equal((await fetch(url,{method:"POST",headers:{authorization:"Bearer valid"}})).status,403);
  entitled=true;
  assert.equal((await fetch(url,{method:"POST",headers:{authorization:"Bearer valid"}})).status,503);
 }finally{await new Promise(resolve=>server.close(resolve));}
});
test("authorized downloads only return HTTPS link after entitlement verification",async()=>{
 const seen=[];
 const server=createAppServer([],{verifyIdentity:async()=>({userId:"buyer"}),
  store:{canDownloadTrack:async()=>true},
  authorizeDownload:async args=>{seen.push(args);return {url:"https://example.invalid/temporary",expiresAt:"2026-10-10T00:00:00Z"};}});
 await new Promise(resolve=>server.listen(0,"127.0.0.1",resolve));
 try{
  const response=await fetch("http://127.0.0.1:"+server.address().port+"/v1/downloads/track-a/authorize",
    {method:"POST",headers:{authorization:"Bearer valid"}});
  assert.equal(response.status,200);
  assert.equal((await response.json()).url,"https://example.invalid/temporary");
  assert.deepEqual(seen,[{userId:"buyer",trackId:"track-a"}]);
 }finally{await new Promise(resolve=>server.close(resolve));}
});
