import test from "node:test";
import assert from "node:assert/strict";
import {createHmac} from "node:crypto";
import {createDownloadSigner} from "../src/download-signer.js";

const secret="server-only-secret-at-least-32-bytes-long";
test("signer returns short-lived HTTPS link for server-mapped track",async()=>{
 const signer=createDownloadSigner({baseUrl:"https://cdn.example.invalid/private",secret,objects:{"track-a":"album/track-a.mp3"},now:()=>1000000000000});
 const result=await signer({trackId:"track-a"});
 const url=new URL(result.url);
 assert.equal(url.protocol,"https:");
 assert.equal(url.pathname,"/private/album/track-a.mp3");
 assert.equal(url.searchParams.get("expires"),"1000000120");
 assert.equal(result.expiresAt,new Date(1000000120*1000).toISOString());
 const payload=`GET\n/album/track-a.mp3\n1000000120\n${url.searchParams.get("nonce")}`;
 assert.equal(url.searchParams.get("signature"),createHmac("sha256",secret).update(payload).digest("hex"));
 assert.equal(await signer({trackId:"unknown"}),null);
});
test("signer rejects invalid configuration and traversal object keys",async()=>{
 assert.throws(()=>createDownloadSigner({baseUrl:"http://example.invalid",secret,objects:{}}),/HTTPS/);
 assert.throws(()=>createDownloadSigner({baseUrl:"https://example.invalid",secret:"short",objects:{}}),/insuficiente/);
 assert.throws(()=>createDownloadSigner({baseUrl:"https://example.invalid",secret,objects:{},ttlSeconds:3600}),/Validade/);
 const signer=createDownloadSigner({baseUrl:"https://example.invalid",secret,objects:{evil:"../secret.mp3"}});
 await assert.rejects(()=>signer({trackId:"evil"}),/inválido/);
});
