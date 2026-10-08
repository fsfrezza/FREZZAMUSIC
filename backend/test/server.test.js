import test from "node:test";
import assert from "node:assert/strict";
import {createAppServer} from "../src/server.js";

async function withServer(catalog, fn) {
 const server=createAppServer(catalog);
 await new Promise(resolve=>server.listen(0,"127.0.0.1",resolve));
 try {return await fn("http://127.0.0.1:"+server.address().port);}
 finally {await new Promise((resolve,reject)=>server.close(error=>error?reject(error):resolve()));}
}
test("health responds 200",()=>withServer([],async base=>{
 const res=await fetch(base+"/health");assert.equal(res.status,200);assert.deepEqual(await res.json(),{ok:true});
}));
test("no catalog returns 503",()=>withServer([],async base=>{
 const res=await fetch(base+"/v1/checkout/quote",{method:"POST",body:JSON.stringify({trackIds:["x"]})});
 assert.equal(res.status,503);
}));
test("server calculates album price and ignores client total",()=>withServer([{id:"a",tracks:[{id:"x"},{id:"y"}]}],async base=>{
 const res=await fetch(base+"/v1/checkout/quote",{method:"POST",body:JSON.stringify({trackIds:["x","y"],totalCents:1})});
 assert.equal(res.status,200);assert.equal((await res.json()).totalCents,1499);
}));
test("unknown track rejected",()=>withServer([{id:"a",tracks:[{id:"x"}]}],async base=>{
 const res=await fetch(base+"/v1/checkout/quote",{method:"POST",body:JSON.stringify({trackIds:["unknown"]})});
 assert.equal(res.status,400);
}));
test("invalid JSON rejected",()=>withServer([{id:"a",tracks:[{id:"x"}]}],async base=>{
 const res=await fetch(base+"/v1/checkout/quote",{method:"POST",body:"{invalid"});
 assert.equal(res.status,400);
}));
test("oversized request rejected",()=>withServer([{id:"a",tracks:[{id:"x"}]}],async base=>{
 const res=await fetch(base+"/v1/checkout/quote",{method:"POST",body:JSON.stringify({trackIds:["x"],padding:"x".repeat(70000)})});
 assert.equal(res.status,413);
}));

test("creating an order without verified identity returns 401",()=>withServer([{id:"a",tracks:[{id:"x"}]}],async base=>{
 const res=await fetch(base+"/v1/checkout/orders",{method:"POST",headers:{"content-type":"application/json","x-user-id":"forged"},body:JSON.stringify({trackIds:["x"]})});
 assert.equal(res.status,401);
}));
test("reading an order without verified identity returns 401",()=>withServer([],async base=>{
 const res=await fetch(base+"/v1/orders/guess",{headers:{"x-user-id":"forged"}});
 assert.equal(res.status,401);
}));
