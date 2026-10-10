import {createServer} from "node:http";
import {quoteSelection} from "./pricing.js";
import {loadCatalog} from "./catalog.js";
import {verifyAccessToken} from "./auth.js";
import {OrderStore} from "./orders.js";
import {PostgresOrderStore} from "./postgres-orders.js";
import {createFirebaseIdentityVerifier} from "./firebase-auth.js";
import {createDownloadSigner} from "./download-signer.js";

function publicOrder(order) {
  const {id,status,currency,amountCents,items,createdAt}=order;
  return {id,status,currency,amountCents,items,createdAt};
}

export function createAppServer(catalog = loadCatalog(), options = {}) {
  const store = options.store ?? (process.env.ORDER_STORE_FILE ? new OrderStore(process.env.ORDER_STORE_FILE) : null);
  const authSecret = options.authSecret ?? process.env.ACCESS_TOKEN_SECRET;
  const verifyIdentity = options.verifyIdentity;
  const authorizeDownload = options.authorizeDownload;
  return createServer(async (req,res)=>{
    const respond=(status,data)=>{
      res.writeHead(status,{"content-type":"application/json; charset=utf-8","cache-control":"no-store"});
      res.end(JSON.stringify(data));
    };
    if(req.method==="GET" && req.url==="/health") return respond(200,{ok:true});
    // Never trust a client-supplied user ID: order endpoints require verified server authentication.
    if(req.url==="/v1/me/entitlements" || req.url==="/v1/checkout/orders" || /^\/v1\/orders\/[^/]+$/.test(req.url ?? "") || /^\/v1\/downloads\/[^/]+\/authorize$/.test(req.url ?? "")) {
      if(typeof req.headers.authorization!=="string" || !req.headers.authorization.startsWith("Bearer ")) return respond(401,{error:"Token de acesso inválido ou ausente"});
      if(!verifyIdentity && (typeof authSecret!=="string" || Buffer.byteLength(authSecret)<32)) return respond(503,{error:"Autenticação não configurada"});
      let identity;
      try {identity=verifyIdentity ? await verifyIdentity(req.headers.authorization) : verifyAccessToken(req.headers.authorization,authSecret);}
      catch {return respond(503,{error:"Falha na verificação de autenticação"});}
      if(!identity) return respond(401,{error:"Token de acesso inválido ou ausente"});
      if(!store) return respond(503,{error:"Armazenamento de pedidos não configurado"});
      if(/^\/v1\/downloads\/[^/]+\/authorize$/.test(req.url)) {
        if(req.method!=="POST") return respond(405,{error:"Método não permitido"});
        const encodedId=req.url.slice("/v1/downloads/".length,-"/authorize".length);
        let trackId;
        try {trackId=decodeURIComponent(encodedId);}catch{return respond(400,{error:"Identificador inválido"});}
        if(!trackId || trackId.length>256 || trackId.includes("/")) return respond(400,{error:"Identificador inválido"});
        if(typeof store.canDownloadTrack!=="function") return respond(503,{error:"Direitos de download não configurados"});
        try {
          const allowed=await store.canDownloadTrack(identity.userId,trackId);
          if(!allowed) return respond(403,{error:"Download não autorizado"});
          if(typeof authorizeDownload!=="function") return respond(503,{error:"Entrega de arquivos não configurada"});
          const result=await authorizeDownload({userId:identity.userId,trackId});
          if(!result || typeof result.url!=="string" || !/^https:\/\//.test(result.url) ||
             typeof result.expiresAt!=="string") return respond(503,{error:"Entrega de arquivos não configurada"});
          return respond(200,{url:result.url,expiresAt:result.expiresAt});
        }catch{return respond(500,{error:"Erro interno"});}
      }
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
    let authorizeDownload;
    if(process.env.PRIVATE_DOWNLOAD_BASE_URL || process.env.PRIVATE_DOWNLOAD_SIGNING_KEY || process.env.PRIVATE_DOWNLOAD_OBJECTS_FILE) {
      if(!process.env.PRIVATE_DOWNLOAD_BASE_URL || !process.env.PRIVATE_DOWNLOAD_SIGNING_KEY || !process.env.PRIVATE_DOWNLOAD_OBJECTS_FILE)
        throw new Error("Configuração de entrega privada incompleta");
      const {readFileSync}=await import("node:fs");
      const objects=JSON.parse(readFileSync(process.env.PRIVATE_DOWNLOAD_OBJECTS_FILE,"utf8"));
      authorizeDownload=createDownloadSigner({
        baseUrl:process.env.PRIVATE_DOWNLOAD_BASE_URL,
        secret:process.env.PRIVATE_DOWNLOAD_SIGNING_KEY,
        objects
      });
    }
    const server=createAppServer(loadCatalog(),{store,verifyIdentity,authorizeDownload});
    server.listen(port,()=>console.log("FREZZAMUSIC backend listening on "+port));
    if(pool) server.on("close",()=>{void pool.end();});
  }
  start().catch(error=>{console.error("Backend startup failed:",error.message);process.exitCode=1;});
}
