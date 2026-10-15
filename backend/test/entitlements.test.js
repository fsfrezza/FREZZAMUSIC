import test from "node:test";
import assert from "node:assert/strict";
import {createAppServer} from "../src/server.js";
import {PostgresOrderStore} from "../src/postgres-orders.js";

test("entitlements query filters revoked grants and unpaid orders at SQL level",async()=>{
 let statement="",args;
 const pool={connect:async()=>({query:async()=>({rows:[]}),release(){}}),query:async(sql,params)=>{statement=sql;args=params;return {rows:[{track_id:"song-a"},{track_id:"song-b"}]};}};
 const result=await new PostgresOrderStore(pool).entitlementsForUser("customer-1");
 assert.deepEqual(result,{trackIds:["song-a","song-b"]});
 assert.deepEqual(args,["customer-1"]);
 assert.match(statement,/e\.user_id=\$1/);
 assert.match(statement,/revoked_at IS NULL/);
 assert.match(statement,/o\.status='paid'/);
});
test("entitlements API requires identity and returns only current user's grants",async()=>{
 const seen=[];
 const server=createAppServer([],{verifyIdentity:async header=>header==="Bearer valid"?{userId:"customer-1"}:null,
  store:{entitlementsForUser:async userId=>{seen.push(userId);return {trackIds:["song-a"]};}}});
 await new Promise(resolve=>server.listen(0,"127.0.0.1",resolve));
 const url="http://127.0.0.1:"+server.address().port+"/v1/me/entitlements";
 try{
  assert.equal((await fetch(url)).status,401);
  assert.equal((await fetch(url,{headers:{authorization:"Bearer bad"}})).status,401);
  const response=await fetch(url,{headers:{authorization:"Bearer valid"}});
  assert.equal(response.status,200);
  assert.deepEqual(await response.json(),{trackIds:["song-a"]});
  assert.deepEqual(seen,["customer-1"]);
  assert.equal((await fetch(url,{method:"POST",headers:{authorization:"Bearer valid"}})).status,405);
 }finally{await new Promise(resolve=>server.close(resolve));}
});
test("entitlements endpoint stays unavailable without purchase database",async()=>{
 const server=createAppServer([],{verifyIdentity:async()=>({userId:"customer-1"}),store:{findForUser:async()=>null}});
 await new Promise(resolve=>server.listen(0,"127.0.0.1",resolve));
 try{
  const response=await fetch("http://127.0.0.1:"+server.address().port+"/v1/me/entitlements",{headers:{authorization:"Bearer valid"}});
  assert.equal(response.status,503);
 }finally{await new Promise(resolve=>server.close(resolve));}
});
