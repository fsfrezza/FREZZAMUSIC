/**
 * PostgreSQL order repository. Requires an injected pg-compatible Pool.
 * The caller owns pool lifecycle; transactions are committed before returning.
 */
import {randomUUID} from "node:crypto";

export class PostgresOrderStore {
  constructor(pool) {
    if(!pool || typeof pool.connect!=="function") throw new TypeError("Pool PostgreSQL obrigatório");
    this.pool=pool;
  }
  async createPending({userId,quote,idempotencyKey}) {
    if(typeof userId!=="string"||!userId || typeof idempotencyKey!=="string"||!idempotencyKey) throw new TypeError("Identidade ou chave inválida");
    if(!quote || quote.currency!=="BRL" || !Number.isSafeInteger(quote.totalCents) || quote.totalCents<=0 || !Array.isArray(quote.items) || !quote.items.length) throw new TypeError("Orçamento inválido");
    const client=await this.pool.connect();
    try {
      await client.query("BEGIN");
      await client.query("INSERT INTO app_users(id) VALUES($1) ON CONFLICT(id) DO NOTHING",[userId]);
      const {rows:[row]}=await client.query(
        `INSERT INTO purchase_orders(id,user_id,idempotency_key,amount_cents)
         VALUES($1,$2,$3,$4)
         ON CONFLICT(user_id,idempotency_key) DO UPDATE
           SET idempotency_key=EXCLUDED.idempotency_key
         RETURNING id,user_id,idempotency_key,status,currency,amount_cents,created_at`,
        [randomUUID(),userId,idempotencyKey,quote.totalCents]
      );
      const {rows:existing}=await client.query(
        "SELECT item_type,item_id,amount_cents,track_ids FROM purchase_order_items WHERE order_id=$1 ORDER BY item_type,item_id",[row.id]
      );
      const normalized=quote.items.map(item=>({
        type:item.type,id:item.id,amountCents:item.amountCents,trackIds:item.trackIds
      })).sort((a,b)=>(a.type+":"+a.id).localeCompare(b.type+":"+b.id));
      if(existing.length) {
        const stored=existing.map(item=>({type:item.item_type,id:item.item_id,amountCents:Number(item.amount_cents),trackIds:item.track_ids}))
          .sort((a,b)=>(a.type+":"+a.id).localeCompare(b.type+":"+b.id));
        if(Number(row.amount_cents)!==quote.totalCents || JSON.stringify(stored)!==JSON.stringify(normalized)) {
          const error=new Error("Chave de idempotência reutilizada com seleção diferente");
          error.code="IDEMPOTENCY_CONFLICT";
          throw error;
        }
      }else {
        if(Number(row.amount_cents)!==quote.totalCents) {
          const error=new Error("Chave de idempotência reutilizada com seleção diferente");
          error.code="IDEMPOTENCY_CONFLICT";
          throw error;
        }
        for(const item of normalized) {
          await client.query(
            "INSERT INTO purchase_order_items(order_id,item_type,item_id,amount_cents,track_ids) VALUES($1,$2,$3,$4,$5::jsonb)",
            [row.id,item.type,item.id,item.amountCents,JSON.stringify(item.trackIds)]
          );
        }
      }
      await client.query("COMMIT");
      return {id:row.id,userId:row.user_id,idempotencyKey:row.idempotency_key,status:row.status,currency:row.currency,amountCents:Number(row.amount_cents),items:normalized,createdAt:new Date(row.created_at).toISOString()};
    }catch(error) {
      await client.query("ROLLBACK");
      throw error;
    }finally{client.release();}
  }
  async findForUser(orderId,userId) {
    const {rows}=await this.pool.query(
      "SELECT id,user_id,idempotency_key,status,currency,amount_cents,created_at FROM purchase_orders WHERE id=$1 AND user_id=$2",[orderId,userId]
    );
    if(!rows.length) return null;
    const row=rows[0];
    const {rows:items}=await this.pool.query(
      "SELECT item_type,item_id,amount_cents,track_ids FROM purchase_order_items WHERE order_id=$1 ORDER BY item_type,item_id",[row.id]
    );
    return {id:row.id,userId:row.user_id,idempotencyKey:row.idempotency_key,status:row.status,currency:row.currency,amountCents:Number(row.amount_cents),items:items.map(item=>({type:item.item_type,id:item.item_id,amountCents:Number(item.amount_cents),trackIds:item.track_ids})),createdAt:new Date(row.created_at).toISOString()};
  }
}
