import test from "node:test";
import assert from "node:assert/strict";
import {createHmac} from "node:crypto";
import {mkdtempSync,rmSync} from "node:fs";
import {tmpdir} from "node:os";
import {join} from "node:path";
import {OrderStore} from "../src/orders.js";
import {createAppServer} from "../src/server.js";

const secret="integration-secret-longer-than-32-characters";
function token(user) {
 const h=Buffer.from(JSON.stringify({alg:"HS256",typ:"JWT"})).toString("base64url");
 const p=Buffer.from(JSON.stringify({sub:user,iss:"frezzamusic",aud:"frezzamusic-api",exp:2000000000})).toString("base64url");
 return "Bearer "+h+"."+p+"."+createHmac("sha256",secret).update(h+"."+p).digest("base64url");
}
test("authenticated orders persist, are idempotent and user-scoped",async()=>{
 const old=process.env.ACCESS_TOKEN_SECRET;
 process.env.ACCESS_TOKEN_SECRET=secret;
 const dir=mkdtempSync(join(tmpdir(),"frezzamusic-api-orders-"));
 const server=createAppServer([{id:"album",tracks:[{id:"t1"},{id:"t2"}]}],{store:new OrderStore(join(dir,"orders.json"))});
 await new Promise(resolve=>server.listen(0,"127.0.0.1",resolve));
 const base="http://127.0.0.1:"+server.address().port;
 try {
  const post=(user,body,key="purchase-key-123")=>fetch(base+"/v1/checkout/orders",{method:"POST",headers:{"authorization":token(user),"idempotency-key":key,"content-type":"application/json"},body:JSON.stringify(body)});
  const first=await post("u1",{trackIds:["t1","t2"],totalCents:1});
  assert.equal(first.status,200);
  const order=await first.json();
  assert.equal(order.status,"pending");
  assert.equal(order.amountCents,1499);
  const repeated=await post("u1",{trackIds:["t1","t2"]});
  assert.equal((await repeated.json()).id,order.id);
  const conflict=await post("u1",{trackIds:["t1"]});
  assert.equal(conflict.status,409);
  const own=await fetch(base+"/v1/orders/"+order.id,{headers:{authorization:token("u1")}});
  assert.equal(own.status,200);
  const other=await fetch(base+"/v1/orders/"+order.id,{headers:{authorization:token("u2")}});
  assert.equal(other.status,404);
  const noAuth=await fetch(base+"/v1/orders/"+order.id);
  assert.equal(noAuth.status,401);
 }finally{
  await new Promise(resolve=>server.close(resolve));
  rmSync(dir,{recursive:true,force:true});
  if(old===undefined) delete process.env.ACCESS_TOKEN_SECRET;else process.env.ACCESS_TOKEN_SECRET=old;
 }
});
