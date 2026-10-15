import test from 'node:test';
import assert from 'node:assert/strict';
import { Buffer } from 'node:buffer';
import { verifyFirebaseIdentity } from '../lib/firebase-identity.mjs';

const now=1_800_000_000_000;
const pair=await crypto.subtle.generateKey({name:'RSASSA-PKCS1-v1_5',modulusLength:2048,publicExponent:new Uint8Array([1,0,1]),hash:'SHA-256'},true,['sign','verify']);
const jwk={...await crypto.subtle.exportKey('jwk',pair.publicKey),kid:'test-key'};
const base={aud:'test-project',iss:'https://securetoken.google.com/test-project',sub:'verified-user',exp:Math.floor(now/1000)+3600,iat:Math.floor(now/1000)-10,auth_time:Math.floor(now/1000)-10,email:'user@example.test',email_verified:true,name:'User'};
async function token(claims=base,header={alg:'RS256',kid:'test-key'}) {
 const parts=[Buffer.from(JSON.stringify(header)).toString('base64url'),Buffer.from(JSON.stringify(claims)).toString('base64url')];
 const signed=await crypto.subtle.sign('RSASSA-PKCS1-v1_5',pair.privateKey,new TextEncoder().encode(parts.join('.')));
 return parts.join('.')+'.'+Buffer.from(signed).toString('base64url');
}
test('verified identity comes only from a correctly signed token for the configured project',async()=>{
 const result=await verifyFirebaseIdentity(await token(),'test-project',{now,keys:[jwk]});
 assert.deepEqual(result,{uid:'verified-user',email:'user@example.test',emailVerified:true,name:'User'});
 assert.equal(await verifyFirebaseIdentity(await token(),'other-project',{now,keys:[jwk]}),null);
});
test('expired, wrong issuer, future auth_time, missing sub, and none algorithm are rejected',async()=>{
 for(const fields of [{exp:Math.floor(now/1000)},{iss:'https://attacker.test'},{auth_time:Math.floor(now/1000)+1000},{sub:''}]) {
  assert.equal(await verifyFirebaseIdentity(await token({...base,...fields}),'test-project',{now,keys:[jwk]}),null);
 }
 assert.equal(await verifyFirebaseIdentity(await token(base,{alg:'none',kid:'test-key'}),'test-project',{now,keys:[jwk]}),null);
});
test('forged signatures and unconfigured projects never create an identity',async()=>{
 const valid=await token();const pieces=valid.split('.');pieces[1]=Buffer.from(JSON.stringify({...base,email:'forged@example.test'})).toString('base64url');
 assert.equal(await verifyFirebaseIdentity(pieces.join('.'),'test-project',{now,keys:[jwk]}),null);
 assert.equal(await verifyFirebaseIdentity(valid,'',{now,keys:[jwk]}),null);
});
