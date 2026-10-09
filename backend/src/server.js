import {createServer} from "node:http";
import {quoteSelection} from "./pricing.js";
import {loadCatalog} from "./catalog.js";
import {verifyAccessToken} from "./auth.js";
import {OrderStore} from "./orders.js";
import {PostgresOrderStore} from "./postgres-orders.js";
import {createFirebaseIdentityVerifier} from "./firebase-auth.js";

function publicOrder(order) {
  const {id,status,currency,amountCents,items,createdAt}=order;
  return {id,status,currency,amountCents,items,createdAt};
}

export function createAppServer(catalog = loadCatalog(), options = {}) {
  const store = options.store ?? (process.env.ORDER_STORE_FILE ? new OrderStore(process.env.ORDER_STORE_FILE) : null);
  const authSecret = options.authSecret ?? process.env.ACCESS_TOKEN_SECRET;
  const verifyIdentity = options.verifyIdentity;
  return createServer(async (req,res)=>{
    const respond=(status,data)=>{
      res.writeHead(status,{"content-type":"application/json; charset=utf-8","cache-control":"no-store"});
      res.end(JSON.stringify(data));
    };
    if(req.method==="GET" && req.url==="/health") return respond(200,{ok:true});
    // Never trust a client-supplied user ID: order endpoints require verified server authentication.
    if(req.url==="/v1/me/entitlements" || req.url==="/v1/checkout/orders" || /^\/v1\/orders\/[^/]+$/.test(req.url ?? "")) {
      if(typeof req.headers.authorization!=="string" || !req.headers.authorization.startsWith("Bearer ")) return respond(401,{error:"Token de acesso inválido ou ausente"});
      if(!verifyIdentity && (typeof authSecret!=="string" || Buffer.byteLength(authSecret)<32)) return respond(503,{error:"Autenticação não configurada"});
      let identity;
      try {identity=verifyIdentity ? await verifyIdentity(req.headers.authorization) : verifyAccessToken(req.headers.authorization,authSecret);}
      catch {return respond(503,{error:"Falha na verificação de autenticação"});}
      if(!identity) return respond(401,{error:"Token de acesso inválido ou ausente"});
      if(!store) return respond(503,{error:"Armazenamento de pedidos não configurado"});
      if(req.url==="/v1/me/entitlements") {
        if(req.method!=="GET") return respond(405,{error:"Método não permitido"});
        if(typeof store.entitlementsForUser!=="function") return respond(503,{error:"Biblioteca de compras não configurada"});
        try {return respond(200,await store.entitlementsForUser(identity.userId));}
        catch {return respond(500,{error:"Erro interno"});}
      }
      if(req.method==="GET" && req.url.startsWith("/v1/orders/")) {
        const id=req.url.slice("/v1/orders/".length);
        if(!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(id)) return respond(404,{error:"Pedido não encontrado"});
        let order;
        try {order=await store.findForUser(id,identity.userId);}catch{return respond(500,{error:"Erro interno"});}
        return order ? respond(200,publicOrder(order)) : respond(404,{error:"Pedido não encontrado"});
      }
      if(req.method==="POST" && req.url==="/v1/checkout/orders") {
        if(catalog.length===0) return respond(503,{error:"Catálogo do servidor não configurado"});
        let body="";
        try {
          for await (const chunk of req) {
            body+=chunk;
            if(Buffer.byteLength(body,"utf8")>65536) return respond(413,{error:"Payload muito grande"});
          }
          const selection=JSON.parse(body);
          const quote=quoteSelection(catalog,selection);
          const key=req.headers["idempotency-key"];
          if(typeof key!=="string" || key.length<8 || key.length>128) return respond(400,{error:"Idempotency-Key inválida"});
          const order=await store.createPending({userId:identity.userId,idempotencyKey:key,quote});
          return respond(200,publicOrder(order));
        }catch(e) {
          if(e instanceof SyntaxError || e instanceof TypeError || e instanceof RangeError) return respond(400,{error:e.message});
          if(e.code==="IDEMPOTENCY_CONFLICT" || e.message==="Chave de idempotência reutilizada com seleção diferente") return respond(409,{error:e.message});
          return respond(500,{error:"Erro interno"});
        }
      }
      return respond(405,{error:"Método não permitido"});
    }
    if(req.method==="POST" && req.url==="/v1/checkout/quote"){
      let body="";
      try {
        for await (const chunk of req) {
          body+=chunk;
          if(Buffer.byteLength(body,"utf8")>65536) return respond(413,{error:"Payload muito grande"});
        }
        const selection=JSON.parse(body);
        if(catalog.length===0) return respond(503,{error:"Catálogo do servidor ainda não configurado"});
        return respond(200,quoteSelection(catalog,selection));
      }catch(e) {
        if(e instanceof SyntaxError || e instanceof TypeError || e instanceof RangeError) return respond(400,{error:e.message});
        return respond(500,{error:"Erro interno"});
      }
    }
    return respond(404,{error:"Rota não encontrada"});
  });
}
if(process.argv[1] && import.meta.url===new URL("file://"+process.argv[1]).href) {
  const port=Number(process.env.PORT ?? 8080);
  async function start() {
    let pool;
    let store;
    if(process.env.DATABASE_URL) {
      const {Pool}=await import("pg");
      pool=new Pool({connectionString:process.env.DATABASE_URL});
      await pool.query("SELECT 1");
      store=new PostgresOrderStore(pool);
    }else if(process.env.ORDER_STORE_FILE) {
      store=new OrderStore(process.env.ORDER_STORE_FILE);
    }
    if(!process.env.FIREBASE_PROJECT_ID && process.env.NODE_ENV==="production") throw new Error("FIREBASE_PROJECT_ID obrigatório em produção");
    const verifyIdentity=process.env.FIREBASE_PROJECT_ID
      ? await createFirebaseIdentityVerifier({projectId:process.env.FIREBASE_PROJECT_ID}) : undefined;
    const server=createAppServer(loadCatalog(),{store,verifyIdentity});
    server.listen(port,()=>console.log("FREZZAMUSIC backend listening on "+port));
    if(pool) server.on("close",()=>{void pool.end();});
  }
  start().catch(error=>{console.error("Backend startup failed:",error.message);process.exitCode=1;});
}
