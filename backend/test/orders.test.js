import test from "node:test";
import assert from "node:assert/strict";
import {mkdtempSync,rmSync} from "node:fs";
import {tmpdir} from "node:os";
import {join} from "node:path";
import {OrderStore} from "../src/orders.js";
import {quoteSelection} from "../src/pricing.js";

function withStore(fn) {
 const dir=mkdtempSync(join(tmpdir(),"frezzamusic-orders-"));
 try{return fn(join(dir,"orders.json"));}
 finally{rmSync(dir,{recursive:true,force:true});}
}
const quote=quoteSelection([{id:"a",tracks:[{id:"a1"},{id:"a2"}]}],{trackIds:["a1"]});
test("creates pending order and persists it",()=>withStore(path=>{
 const store=new OrderStore(path);
 const order=store.createPending({userId:"u1",idempotencyKey:"k1",quote});
 assert.equal(order.status,"pending");assert.equal(order.amountCents,199);
 assert.deepEqual(new OrderStore(path).findForUser(order.id,"u1"),order);
}));
test("same key and selection returns same order",()=>withStore(path=>{
 const store=new OrderStore(path);
 const args={userId:"u1",idempotencyKey:"k1",quote};
 assert.equal(store.createPending(args).id,store.createPending(args).id);
}));
test("same key and different selection rejected",()=>withStore(path=>{
 const store=new OrderStore(path);
 store.createPending({userId:"u1",idempotencyKey:"k1",quote});
 const different=quoteSelection([{id:"a",tracks:[{id:"a1"},{id:"a2"}]}],{albumIds:["a"]});
 assert.throws(()=>store.createPending({userId:"u1",idempotencyKey:"k1",quote:different}));
}));
test("users cannot access other users orders",()=>withStore(path=>{
 const store=new OrderStore(path);
 const order=store.createPending({userId:"u1",idempotencyKey:"k1",quote});
 assert.equal(store.findForUser(order.id,"u2"),null);
}));
test("invalid quote rejected",()=>withStore(path=>{
 assert.throws(()=>new OrderStore(path).createPending({userId:"u",idempotencyKey:"k",quote:{currency:"BRL",totalCents:0,items:[]}}));
}));

test("independent stores observe persisted orders and idempotency",()=>withStore(path=>{
 const first=new OrderStore(path);
 const second=new OrderStore(path);
 const args={userId:"u1",idempotencyKey:"same-key",quote};
 const order=first.createPending(args);
 assert.equal(second.findForUser(order.id,"u1").id,order.id);
 assert.equal(second.createPending(args).id,order.id);
}));
