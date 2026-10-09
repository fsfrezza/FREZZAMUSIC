import test from "node:test";
import assert from "node:assert/strict";
import {createFirebaseIdentityVerifier} from "../src/firebase-auth.js";
import {createAppServer} from "../src/server.js";

const projectId="frezzamusic-test";
const claims={uid:"user-42",aud:projectId,iss:"https://securetoken.google.com/"+projectId};
test("Firebase verifier accepts matching project and verified UID",async()=>{
 const verify=await createFirebaseIdentityVerifier({projectId,adminAuth:{verifyIdToken:async(token,checkRevoked)=>{
  assert.equal(token,"valid");assert.equal(checkRevoked,true);return claims;
 }}});
 assert.deepEqual(await verify("Bearer valid"),{userId:"user-42"});
 assert.equal(await verify("Bearer invalid token"),null);
 assert.equal(await verify("Basic valid"),null);
});
test("Firebase verifier rejects mismatched project and revoked token",async()=>{
 const wrong=await createFirebaseIdentityVerifier({projectId,adminAuth:{verifyIdToken:async()=>({...claims,aud:"other"})}});
 assert.equal(await wrong("Bearer valid"),null);
 const revoked=await createFirebaseIdentityVerifier({projectId,adminAuth:{verifyIdToken:async()=>{throw Error("revoked");}}});
 assert.equal(await revoked("Bearer revoked"),null);
});
test("Firebase verifier is required when explicitly configured on order routes",async()=>{
 const server=createAppServer([],{verifyIdentity:async token=>token==="Bearer valid"?{userId:"user-42"}:null});
 await new Promise(resolve=>server.listen(0,"127.0.0.1",resolve));
 const base="http://127.0.0.1:"+server.address().port;
 try {
  const denied=await fetch(base+"/v1/checkout/orders",{method:"POST",headers:{authorization:"Bearer bad"}});
  assert.equal(denied.status,401);
  const accepted=await fetch(base+"/v1/checkout/orders",{method:"POST",headers:{authorization:"Bearer valid"}});
  assert.equal(accepted.status,503); // authenticated, but order storage is not configured
 }finally{await new Promise(resolve=>server.close(resolve));}
});
