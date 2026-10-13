import { Buffer } from 'node:buffer';

let cachedKeys;
let cacheUntil = 0;
async function googleKeys() {
 if (cachedKeys && Date.now() < cacheUntil) return cachedKeys;
 const r = await fetch('https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com', {signal:AbortSignal.timeout(10000)});
 if (!r.ok) throw new Error('Firebase keys unavailable');
 const raw = await r.text(); if(raw.length>64000) throw new Error('Invalid key response');
 const data=JSON.parse(raw); if(!Array.isArray(data.keys))throw new Error('Invalid keys');
 cachedKeys=data.keys; cacheUntil=Date.now()+Math.min(3600,Number(/max-age=(\d+)/.exec(r.headers.get('cache-control')??'')?.[1]??300))*1000;
 return cachedKeys;
}
/** Validates signature and Firebase project claims; no password or raw token is persisted. */
export async function verifyFirebaseIdentity(token, projectId, {now=Date.now(),keys}={}) {
 if(typeof projectId!=='string' || !projectId || typeof token!=='string' || token.length>6000)return null;
 let header,claims,parts;
 try {parts=token.split('.');if(parts.length!==3)return null;header=JSON.parse(Buffer.from(parts[0],'base64url'));claims=JSON.parse(Buffer.from(parts[1],'base64url'));}catch{return null;}
 const seconds=Math.floor(now/1000);
 if(header.alg!=='RS256' || typeof header.kid!=='string' || !header.kid || claims.aud!==projectId || claims.iss!==`https://securetoken.google.com/${projectId}` ||
    typeof claims.sub!=='string' || !claims.sub || claims.sub.length>128 || !Number.isFinite(claims.exp) || claims.exp<=seconds ||
    !Number.isFinite(claims.iat) || claims.iat>seconds+30 || !Number.isFinite(claims.auth_time) || claims.auth_time>seconds+30 || claims.auth_time<0)return null;
 const available=keys ?? await googleKeys();
 const jwk=available.find(k=>k.kid===header.kid && k.kty==='RSA');if(!jwk)return null;
 try {
  const publicKey=await crypto.subtle.importKey('jwk',jwk,{name:'RSASSA-PKCS1-v1_5',hash:'SHA-256'},false,['verify']);
  if(!await crypto.subtle.verify('RSASSA-PKCS1-v1_5',publicKey,Buffer.from(parts[2],'base64url'),new TextEncoder().encode(parts[0]+'.'+parts[1])))return null;
 }catch{return null;}
 return {uid:claims.sub,email:typeof claims.email==='string' && claims.email.length<=320?claims.email:null,emailVerified:claims.email_verified===true,name:typeof claims.name==='string'?claims.name.slice(0,100):null};
}
