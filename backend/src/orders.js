import {randomUUID} from "node:crypto";
import {mkdirSync,readFileSync,renameSync,writeFileSync,unlinkSync} from "node:fs";
import {dirname} from "node:path";

export class OrderStore {
  constructor(path) {
    if (!path) throw new TypeError("ORDER_STORE_FILE obrigatório");
    this.path=path;
    this.orders=this.#load();
  }
  #load() {
    try {
      const data=JSON.parse(readFileSync(this.path,"utf8"));
      if(!Array.isArray(data)) throw new TypeError("Arquivo de pedidos inválido");
      return data;
    }catch(error) {
      if(error.code==="ENOENT") return [];
      throw error;
    }
  }
  #persist() {
    mkdirSync(dirname(this.path),{recursive:true});
    const tmp=this.path+"."+randomUUID()+".tmp";
    try {
      writeFileSync(tmp,JSON.stringify(this.orders,null,2),{mode:0o600,flag:"wx"});
      renameSync(tmp,this.path);
    }catch(error) {
      try {unlinkSync(tmp);}catch{}
      throw error;
    }
  }
  createPending({userId,quote,idempotencyKey}) {
    if(typeof userId!=="string"||!userId || typeof idempotencyKey!=="string"||!idempotencyKey) throw new TypeError("Identidade ou chave inválida");
    // Reload before checking idempotency so independent store instances do not reuse stale state.
    this.orders=this.#load();
    if(!quote || quote.currency!=="BRL" || !Number.isSafeInteger(quote.totalCents) || quote.totalCents<=0 || !Array.isArray(quote.items) || quote.items.length===0) throw new TypeError("Orçamento inválido");
    const existing=this.orders.find(o=>o.userId===userId && o.idempotencyKey===idempotencyKey);
    if(existing) {
      if(JSON.stringify(existing.items)!==JSON.stringify(quote.items)||existing.amountCents!==quote.totalCents) throw new Error("Chave de idempotência reutilizada com seleção diferente");
      return existing;
    }
    const order={id:randomUUID(),userId,idempotencyKey,status:"pending",currency:"BRL",amountCents:quote.totalCents,items:structuredClone(quote.items),createdAt:new Date().toISOString()};
    this.orders.push(order);
    try {this.#persist();} catch(error) {this.orders.pop();throw error;}
    return order;
  }
  findForUser(orderId,userId) {
    this.orders=this.#load();
    return this.orders.find(o=>o.id===orderId && o.userId===userId)??null;
  }
}
