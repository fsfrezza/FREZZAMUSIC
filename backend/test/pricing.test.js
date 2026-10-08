import test from "node:test";
import assert from "node:assert/strict";
import {quoteSelection} from "../src/pricing.js";

const catalog=[{id:"a",tracks:[{id:"a1"},{id:"a2"},{id:"a3"}]},{id:"b",tracks:Array.from({length:12},(_,i)=>({id:"b"+i}))}];
test("one track costs 199 cents",()=>assert.equal(quoteSelection(catalog,{trackIds:["a1"]}).totalCents,199));
test("full album costs exactly 1499 cents",()=>assert.equal(quoteSelection(catalog,{albumIds:["a"]}).totalCents,1499));
test("all individual tracks become a full album",()=>assert.equal(quoteSelection(catalog,{trackIds:["a1","a2","a3"]}).totalCents,1499));
test("partial album costs 199 per track",()=>assert.equal(quoteSelection(catalog,{trackIds:["a1","a2"]}).totalCents,398));
test("album plus one track",()=>assert.equal(quoteSelection(catalog,{albumIds:["a"],trackIds:["b0"]}).totalCents,1698));
test("overlap is not charged twice",()=>assert.equal(quoteSelection(catalog,{albumIds:["a"],trackIds:["a1"]}).totalCents,1499));
test("unknown tracks rejected",()=>assert.throws(()=>quoteSelection(catalog,{trackIds:["fake"]}),RangeError));
test("empty selection rejected",()=>assert.throws(()=>quoteSelection(catalog,{}),RangeError));
test("client prices ignored",()=>assert.equal(quoteSelection(catalog,{trackIds:["a1"],totalCents:1}).totalCents,199));
