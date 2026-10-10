import {createHmac,randomUUID} from "node:crypto";

/**
 * Signs private-object download URLs using a trusted CDN signing service.
 * No signing key or object path is ever accepted from the Android client.
 * The CDN must verify signatures and expiration before serving a private object.
 */
export function createDownloadSigner({baseUrl,secret,objects,ttlSeconds=120,now=()=>Date.now()}) {
  if(typeof baseUrl!=="string") throw new TypeError("URL de entrega obrigatória");
  const parsed=new URL(baseUrl);
  if(parsed.protocol!=="https:" || parsed.username || parsed.password || parsed.search || parsed.hash) throw new TypeError("URL HTTPS limpa obrigatória");
  if(typeof secret!=="string" || Buffer.byteLength(secret)<32) throw new TypeError("Chave de assinatura insuficiente");
  if(!objects || typeof objects!=="object" || Array.isArray(objects)) throw new TypeError("Mapa privado de arquivos obrigatório");
  if(!Number.isInteger(ttlSeconds) || ttlSeconds<30 || ttlSeconds>300) throw new RangeError("Validade deve ficar entre 30 e 300 segundos");
  return async ({trackId})=>{
    if(typeof trackId!=="string" || !Object.hasOwn(objects,trackId)) return null;
    const objectKey=objects[trackId];
    if(typeof objectKey!=="string" || !/^[a-zA-Z0-9][a-zA-Z0-9/_-]{0,255}\.[a-zA-Z0-9]{2,8}$/.test(objectKey) || objectKey.includes("..") || objectKey.includes("//")) throw new TypeError("Objeto privado inválido");
    const expires=Math.floor(now()/1000)+ttlSeconds;
    const nonce=randomUUID();
    const path="/"+objectKey.split("/").map(encodeURIComponent).join("/");
    const payload=`GET\n${path}\n${expires}\n${nonce}`;
    const signature=createHmac("sha256",secret).update(payload).digest("hex");
    const url=new URL(parsed.toString());
    url.pathname=url.pathname.replace(/\/$/,"")+path;
    url.searchParams.set("expires",String(expires));
    url.searchParams.set("nonce",nonce);
    url.searchParams.set("signature",signature);
    return {url:url.toString(),expiresAt:new Date(expires*1000).toISOString()};
  };
}
