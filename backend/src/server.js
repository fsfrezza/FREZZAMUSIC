import {createServer} from "node:http";
import {quoteSelection} from "./pricing.js";
import {loadCatalog} from "./catalog.js";

const port = Number(process.env.PORT ?? 8080);
const catalog = loadCatalog();
const server = createServer(async (req,res)=>{
  const respond=(status,data)=>{res.writeHead(status,{"content-type":"application/json; charset=utf-8","cache-control":"no-store"});res.end(JSON.stringify(data));};
  if(req.method==="GET" && req.url==="/health") return respond(200,{ok:true});
  if(req.method==="POST" && req.url==="/v1/checkout/quote"){
    let body="";
    try {
      for await (const chunk of req) {body+=chunk;if(body.length>65536) return respond(413,{error:"Payload muito grande"});}
      const selection=JSON.parse(body);
      // No authoritative catalog is configured: reject rather than trusting client-provided prices or items.
      if(catalog.length===0) return respond(503,{error:"Catálogo do servidor ainda não configurado"});
      return respond(200,quoteSelection(catalog,selection));
    }catch(e){return respond(400,{error:e.message});}
  }
  return respond(404,{error:"Rota não encontrada"});
});
server.listen(port,()=>console.log("FREZZAMUSIC backend listening on "+port));
