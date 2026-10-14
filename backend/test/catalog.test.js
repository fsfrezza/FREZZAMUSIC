import test from "node:test";
import assert from "node:assert/strict";
import {mkdtempSync,writeFileSync,rmSync} from "node:fs";
import {tmpdir} from "node:os";
import {join} from "node:path";
import {loadCatalog} from "../src/catalog.js";
function withCatalog(data,fn) {
 const dir=mkdtempSync(join(tmpdir(),"frezzamusic-"));
 try {const file=join(dir,"catalog.json");writeFileSync(file,JSON.stringify(data));return fn(file);}
 finally {rmSync(dir,{recursive:true,force:true});}
}
test("missing configuration disables quotes",()=>assert.deepEqual(loadCatalog(""),[]));
test("valid catalog exposes only stable IDs",()=>withCatalog([{id:"a",tracks:[{id:"a1",secret:"private"}]}],p=>assert.deepEqual(loadCatalog(p),[{id:"a",tracks:[{id:"a1"}]}])));
test("duplicate track IDs are rejected",()=>withCatalog([{id:"a",tracks:[{id:"x"},{id:"x"}]}],p=>assert.throws(()=>loadCatalog(p),TypeError)));
test("duplicate album IDs are rejected",()=>withCatalog([{id:"a",tracks:[{id:"x"}]},{id:"a",tracks:[{id:"y"}]}],p=>assert.throws(()=>loadCatalog(p),TypeError)));
test("empty album is rejected",()=>withCatalog([{id:"a",tracks:[]}],p=>assert.throws(()=>loadCatalog(p),TypeError)));
