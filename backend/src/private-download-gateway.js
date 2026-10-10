import {createHmac,timingSafeEqual} from "node:crypto";
import {createReadStream,lstatSync,realpathSync,statSync} from "node:fs";
import {createServer} from "node:http";
import {resolve,sep} from "node:path";

/**
 * Minimal private-file gateway compatible with download-signer.js.
 * Place behind TLS termination; never expose its private directory as static files.
 */
export function createPrivateDownloadGateway({rootDir,secret,prefix="/private",now=()=>Date.now()}) {
  if(typeof rootDir!=="string" || !rootDir) throw new TypeError("Diretório privado obrigatório");
  if(typeof secret!=="string" || Buffer.byteLength(secret)<32) throw new TypeError("Chave de assinatura insuficiente");
  if(!/^\/[a-zA-Z0-9/_-]*$/.test(prefix) || prefix.endsWith("/")) throw new TypeError("Prefixo inválido");
  const root=realpathSync(resolve(rootDir));
  return createServer((req,res)=>{
    const reject=(status)=>{res.writeHead(status,{"cache-control":"no-store","content-type":"application/json"});res.end(JSON.stringify({error:status===403?"Acesso negado":"Arquivo não encontrado"}));};
    if(req.method!=="GET") return reject(403);
    let url;
    try {url=new URL(req.url,"http://localhost");}catch{return reject(403);}
    if(!url.pathname.startsWith(prefix+"/")) return reject(404);
    const relative=url.pathname.slice(prefix.length+1);
    if(!/^[a-zA-Z0-9][a-zA-Z0-9/_-]{0,255}\.[a-zA-Z0-9]{2,8}$/.test(relative) || relative.includes("..") || relative.includes("//")) return reject(403);
    const expires=url.searchParams.get("expires");
    const nonce=url.searchParams.get("nonce");
    const signature=url.searchParams.get("signature");
    if(url.searchParams.size!==3 || !expires || !/^[0-9]{10,12}$/.test(expires) ||
       !nonce || !/^[0-9a-f-]{36}$/.test(nonce) ||
       !signature || !/^[a-f0-9]{64}$/.test(signature)) return reject(403);
    const expiry=Number(expires);
    const current=Math.floor(now()/1000);
    if(!Number.isSafeInteger(expiry) || expiry<current || expiry>current+300) return reject(403);
    const payload=`GET\n/${relative}\n${expires}\n${nonce}`;
    const expected=createHmac("sha256",secret).update(payload).digest();
    if(!timingSafeEqual(expected,Buffer.from(signature,"hex"))) return reject(403);
    const path=resolve(root,relative);
    if(!path.startsWith(root+sep)) return reject(403);
    let stat;
    try {
      const segments=relative.split("/");
      let cursor=root;
      for(const segment of segments) {
        cursor=resolve(cursor,segment);
        if(lstatSync(cursor).isSymbolicLink()) return reject(403);
      }
      if(realpathSync(path)!==path) return reject(403);
      stat=statSync(path);
      if(!stat.isFile()) return reject(404);
    }catch{return reject(404);}
    res.writeHead(200,{"content-type":"application/octet-stream","content-length":stat.size,
      "content-disposition":`attachment; filename="${relative.split("/").at(-1)}"`,
      "cache-control":"private, no-store","x-content-type-options":"nosniff"});
    const stream=createReadStream(path);
    stream.on("error",()=>res.destroy());
    stream.pipe(res);
  });
}

if(process.argv[1] && import.meta.url===new URL("file://"+process.argv[1]).href) {
  const rootDir=process.env.PRIVATE_DOWNLOAD_ROOT;
  const secret=process.env.PRIVATE_DOWNLOAD_SIGNING_KEY;
  const port=Number(process.env.PRIVATE_DOWNLOAD_PORT??8081);
  createPrivateDownloadGateway({rootDir,secret}).listen(port,"127.0.0.1",()=>console.log("Private download gateway listening on "+port));
}
