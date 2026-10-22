import {createHmac,timingSafeEqual} from "node:crypto";

function decodeBase64Url(value) {
  if(typeof value!=="string" || !/^[A-Za-z0-9_-]+$/.test(value)) throw new Error("Token inválido");
  return Buffer.from(value,"base64url");
}

/** Verify a server-issued HS256 token. Never accept alg=none or user-supplied identities. */
export function verifyAccessToken(header, secret, nowSeconds=Math.floor(Date.now()/1000)) {
  if(typeof secret!=="string" || Buffer.byteLength(secret)<32) throw new Error("Segredo de autenticação não configurado");
  if(typeof header!=="string" || !header.startsWith("Bearer ")) return null;
  const token=header.slice(7);
  const parts=token.split(".");
  if(parts.length!==3) return null;
  try {
    const [h,p,s]=parts;
    const metadata=JSON.parse(decodeBase64Url(h).toString("utf8"));
    if(metadata.alg!=="HS256" || metadata.typ!=="JWT") return null;
    const signature=decodeBase64Url(s);
    const expected=createHmac("sha256",secret).update(h+"."+p).digest();
    if(signature.length!==expected.length || !timingSafeEqual(signature,expected)) return null;
    const payload=JSON.parse(decodeBase64Url(p).toString("utf8"));
    if(typeof payload.sub!=="string" || !payload.sub || !Number.isSafeInteger(payload.exp) || payload.exp<=nowSeconds) return null;
    if(payload.iss!=="frezzamusic" || payload.aud!=="frezzamusic-api") return null;
    if(payload.nbf!==undefined && (!Number.isSafeInteger(payload.nbf) || payload.nbf>nowSeconds)) return null;
    return {userId:payload.sub};
  }catch{return null;}
}
