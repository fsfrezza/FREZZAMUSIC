import {createServer} from "node:http";
import {quoteSelection} from "./pricing.js";
import {loadCatalog} from "./catalog.js";
import {verifyAccessToken} from "./auth.js";

export function createAppServer(catalog = loadCatalog()) {
  return createServer(async (req,res)=>{
    const respond=(status,data)=>{
      res.writeHead(status,{"content-type":"application/json; charset=utf-8","cache-control":"no-store"});
      res.end(JSON.stringify(data));
    };
    if(req.method==="GET" && req.url==="/health") return respond(200,{ok:true});
    // Never trust a client-supplied user ID: order endpoints require verified server authentication.
    if(req.url==="/v1/checkout/orders" || /^\/v1\/orders\/[^/]+$/.test(req.url ?? "")) {
      const secret=process.env.ACCESS_TOKEN_SECRET;
      if(!secret) return respond(401,{error:"Autenticação indisponível; acesso negado"});
      const identity=verifyAccessToken(req.headers.authorization,secret);
      if(!identity) return respond(401,{error:"Token de acesso inválido ou ausente"});
      return respond(501,{error:"Persistência de pedidos ainda não conectada à API"});
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
  createAppServer().listen(port,()=>console.log("FREZZAMUSIC backend listening on "+port));
}
