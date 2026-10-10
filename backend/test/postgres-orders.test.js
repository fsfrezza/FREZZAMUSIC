import test from "node:test";
import assert from "node:assert/strict";
import {PostgresOrderStore} from "../src/postgres-orders.js";

const quote={currency:"BRL",totalCents:199,items:[{type:"track",id:"track-1",amountCents:199,trackIds:["track-1"]}]};
const row={id:"11111111-1111-4111-8111-111111111111",user_id:"user-1",idempotency_key:"key-123456",status:"pending",currency:"BRL",amount_cents:"199",created_at:"2026-10-08T12:00:00.000Z"};

function mockPool({existing=[],amountCents="199",failInsert=false}={}) {
  const calls=[];
  const client={
    async query(sql,params=[]) {
      calls.push({sql,params});
      if(sql==="BEGIN"||sql==="COMMIT"||sql==="ROLLBACK") return {rows:[]};
      if(sql.startsWith("INSERT INTO app_users")) return {rows:[]};
      if(sql.startsWith("INSERT INTO purchase_orders")) return {rows:[{...row,amount_cents:amountCents}]};
      if(sql.startsWith("SELECT item_type")) return {rows:existing};
      if(sql.startsWith("INSERT INTO purchase_order_items")) {
        if(failInsert) throw new Error("database error");
        return {rows:[]};
      }
      if(sql.startsWith("SELECT id,user_id")) return {rows:[row]};
      throw new Error("Unexpected SQL: "+sql);
    },
    release(){calls.push({sql:"RELEASE"});}
  };
  return {calls,connect:async()=>client,query:(sql,params)=>client.query(sql,params)};
}
test("PostgreSQL store inserts order items inside a transaction",async()=>{
 const pool=mockPool();
 const order=await new PostgresOrderStore(pool).createPending({userId:"user-1",idempotencyKey:"key-123456",quote});
 assert.equal(order.amountCents,199);
 assert.deepEqual(order.items,quote.items);
 assert(pool.calls.some(x=>x.sql.startsWith("INSERT INTO purchase_order_items")));
 assert(pool.calls.some(x=>x.sql==="COMMIT"));
 assert.equal(pool.calls.at(-1).sql,"RELEASE");
});
test("PostgreSQL store reuses identical idempotent order",async()=>{
 const pool=mockPool({existing:[{item_type:"track",item_id:"track-1",amount_cents:"199",track_ids:["track-1"]}]});
 const order=await new PostgresOrderStore(pool).createPending({userId:"user-1",idempotencyKey:"key-123456",quote});
 assert.equal(order.id,row.id);
 assert(!pool.calls.some(x=>x.sql.startsWith("INSERT INTO purchase_order_items")));
});
test("PostgreSQL store rejects changed selection and rolls back",async()=>{
 const pool=mockPool({existing:[{item_type:"track",item_id:"different",amount_cents:"199",track_ids:["different"]}]});
 await assert.rejects(()=>new PostgresOrderStore(pool).createPending({userId:"user-1",idempotencyKey:"key-123456",quote}),{code:"IDEMPOTENCY_CONFLICT"});
 assert(pool.calls.some(x=>x.sql==="ROLLBACK"));
 assert(!pool.calls.some(x=>x.sql==="COMMIT"));
});
test("PostgreSQL store rolls back failed writes and releases client",async()=>{
 const pool=mockPool({failInsert:true});
 await assert.rejects(()=>new PostgresOrderStore(pool).createPending({userId:"user-1",idempotencyKey:"key-123456",quote}),/database error/);
 assert(pool.calls.some(x=>x.sql==="ROLLBACK"));
 assert.equal(pool.calls.at(-1).sql,"RELEASE");
});
test("PostgreSQL store returns only user's order and its items",async()=>{
 const pool=mockPool({existing:[{item_type:"track",item_id:"track-1",amount_cents:"199",track_ids:["track-1"]}]});
 const order=await new PostgresOrderStore(pool).findForUser(row.id,"user-1");
 assert.equal(order.userId,"user-1");
 assert.deepEqual(order.items,quote.items);
 assert(pool.calls.some(x=>x.sql.includes("user_id=$2")&&x.params[1]==="user-1"));
});
