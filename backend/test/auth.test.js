import test from "node:test";
import assert from "node:assert/strict";
import {createHmac} from "node:crypto";
import {verifyAccessToken} from "../src/auth.js";
const secret="test-secret-32-characters-minimum-123456";
function sign(payload,algorithm="HS256") {
 const h=Buffer.from(JSON.stringify({alg:algorithm,typ:"JWT"})).toString("base64url");
 const p=Buffer.from(JSON.stringify(payload)).toString("base64url");
 const sig=createHmac("sha256",secret).update(h+"."+p).digest("base64url");
 return "Bearer "+h+"."+p+"."+sig;
}
const claims={sub:"user-123",iss:"frezzamusic",aud:"frezzamusic-api",exp:2000000000};
test("valid token returns verified user",()=>assert.deepEqual(verifyAccessToken(sign(claims),secret,1000000000),{userId:"user-123"}));
test("expired token rejected",()=>assert.equal(verifyAccessToken(sign({...claims,exp:999}),secret,1000),null));
test("invalid audience rejected",()=>assert.equal(verifyAccessToken(sign({...claims,aud:"other"}),secret,1000),null));
test("invalid issuer rejected",()=>assert.equal(verifyAccessToken(sign({...claims,iss:"other"}),secret,1000),null));
test("algorithm confusion rejected",()=>assert.equal(verifyAccessToken(sign(claims,"none"),secret,1000),null));
test("tampering rejected",()=>assert.equal(verifyAccessToken(sign(claims).replace("user-123","admin"),secret,1000),null));
test("missing token rejected",()=>assert.equal(verifyAccessToken(undefined,secret),null));
test("short secret rejected",()=>assert.throws(()=>verifyAccessToken(sign(claims),"weak")));
