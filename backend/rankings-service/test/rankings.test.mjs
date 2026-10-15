import test from 'node:test';
import assert from 'node:assert/strict';
import { DatabaseSync } from 'node:sqlite';
import { readFileSync, readdirSync } from 'node:fs';
import { handle, parseFolder } from '../lib/rankings.mjs';

function database() {
 const sqlite = new DatabaseSync(':memory:');
 sqlite.exec('PRAGMA foreign_keys=ON');
 for (const name of readdirSync(new URL('../drizzle/',import.meta.url)).filter(n=>n.endsWith('.sql')).sort()) sqlite.exec(readFileSync(new URL('../drizzle/'+name,import.meta.url),'utf8'));
 return {prepare(sql){let values=[];return {bind(...v){values=v;return this},async first(){return sqlite.prepare(sql).get(...values) ?? null},async all(){return {results:sqlite.prepare(sql).all(...values)}},async run(){return sqlite.prepare(sql).run(...values)}}}};
}
const input={driveId:'drive-track-123',folderId:'album-folder-123',artist:'theFrezza'};
const resolve=async data=>({...data,title:'Real title',album:'Real album',coverId:null});
function request(path,data,token) {return new Request('https://rankings.test'+path,{method:data?'POST':'GET',headers:{'content-type':'application/json',...(token?{authorization:'Bearer '+token}:{})},...(data?{body:JSON.stringify(data)}:{})});}
async function register(db,app='FREZZAMUSIC') {const r=await handle(request('/api/register',{app}),db,{now:100000,resolve});assert.equal(r.status,201);return (await r.json()).token;}

test('fresh database publishes empty rankings, not invented counts',async()=>{
 const r=await handle(request('/api/rankings'),database());assert.equal(r.status,200);
 const d=await r.json();for(const scope of Object.values(d.scopes)){assert.deepEqual(scope.played,[]);assert.deepEqual(scope.liked,[])}
});
test('likes from all apps aggregate; repeated requests and stale unlike do not inflate or erase votes',async()=>{
 const db=database();const tokens=[];for(const app of ['FREZZAMUSIC','SOLASIAS','THEFREZZA'])tokens.push(await register(db,app));
 for(const token of tokens){for(let n=0;n<2;n++){const r=await handle(request('/api/like',{...input,liked:true,sequence:2},token),db,{now:101000,resolve});assert.equal(r.status,200)}}
 await handle(request('/api/like',{...input,liked:false,sequence:1},tokens[0]),db,{now:102000,resolve});
 let ranks=await (await handle(request('/api/rankings'),db)).json();assert.equal(ranks.scopes.general.liked[0].total,3);assert.equal(ranks.scopes.thefrezza.liked[0].total,3);assert.equal(ranks.scopes.solasias.liked.length,0);
 await handle(request('/api/like',{...input,liked:false,sequence:3},tokens[0]),db,{now:103000,resolve});
 ranks=await (await handle(request('/api/rankings'),db)).json();assert.equal(ranks.scopes.general.liked[0].total,2);
});
test('a listen needs 30 server seconds and counts once even when delivery is retried',async()=>{
 const db=database();const token=await register(db);const eventId=crypto.randomUUID();
 assert.equal((await handle(request('/api/listen/start',{...input,eventId},token),db,{now:110000,resolve})).status,200);
 assert.equal((await handle(request('/api/listen/complete',{eventId,listenedSeconds:30},token),db,{now:139999,resolve})).status,409);
 assert.equal((await handle(request('/api/listen/complete',{eventId,listenedSeconds:29},token),db,{now:140000,resolve})).status,400);
 for(let n=0;n<2;n++)assert.equal((await handle(request('/api/listen/complete',{eventId,listenedSeconds:30},token),db,{now:140000,resolve})).status,200);
 const r=await (await handle(request('/api/rankings'),db)).json();assert.equal(r.scopes.general.played[0].total,1);
 const other=await register(db,'SOLASIAS');assert.equal((await handle(request('/api/listen/complete',{eventId,listenedSeconds:30},other),db,{now:140000,resolve})).status,404);
});
test('anonymous reads expose aggregates only; writes require a registered token and approved catalog',async()=>{
 const db=database();assert.equal((await handle(request('/api/like',{...input,liked:true,sequence:1}),db,{resolve})).status,401);
 const token=await register(db);assert.equal((await handle(request('/api/like',{...input,artist:'Unknown',liked:true,sequence:1},token),db,{resolve})).status,400);
 const r=await handle(request('/api/like',{...input,liked:true,sequence:1},token),db,{resolve:async()=>{throw Object.assign(new Error('Not official'),{status:404})}});assert.equal(r.status,404);
});
test('public folder parser matches real MP3 and folder rows',()=>{
 const root=readFileSync(new URL('./fixtures/root-frezza.html',import.meta.url),'utf8');const files=readFileSync(new URL('./fixtures/newalbum.html',import.meta.url),'utf8');
 assert.equal(parseFolder(root).filter(x=>x.folder).length,14);assert.equal(parseFolder(files).filter(x=>/\.mp3$/i.test(x.name)).length,11);
});

test('login saves verified profile data but never the raw token; completed plays retain the identity at start',async()=>{
 const db=database();const token=await register(db);
 const options={now:110000,resolve,projectId:'project',verifyIdentity:async()=>({uid:'uid-a',email:'user@example.test',emailVerified:true,name:'User'})};
 assert.equal((await handle(request('/api/identify',{firebaseIdToken:'signed-token'},token),db,options)).status,200);
 const profile=await db.prepare('SELECT * FROM profiles WHERE user_id=?').bind('uid-a').first();assert.equal(profile.email,'user@example.test');assert.equal(profile.email_verified,1);assert.equal(Object.hasOwn(profile,'token'),false);
 const eventId=crypto.randomUUID();
 await handle(request('/api/listen/start',{...input,eventId,firebaseIdToken:'signed-token'},token),db,options);
 await handle(request('/api/listen/complete',{eventId,listenedSeconds:30},token),db,{now:140000,resolve});
 assert.equal((await db.prepare('SELECT user_id FROM plays WHERE event_id=?').bind(eventId).first()).user_id,'uid-a');
 assert.equal((await handle(request('/api/identify',{firebaseIdToken:'signed-token'},token),db,{now:140000,resolve})).status,503);
 assert.equal((await handle(request('/api/identify',{firebaseIdToken:'fake'},token),db,{...options,verifyIdentity:async()=>null})).status,401);
});

test('each leaderboard is capped at ten and separates SOLASIAS from theFrezza',async()=>{
 const db=database();const token=await register(db);
 for(let i=0;i<15;i++) {
  const artist=i<12?'theFrezza':'Solasias';const data={...input,artist,driveId:'drive-track-'+(100+i),liked:true,sequence:i+1};
  const r=await handle(request('/api/like',data,token),db,{now:101000,resolve:async x=>({...x,title:'Song '+String(i).padStart(2,'0'),album:'Album',coverId:null})});assert.equal(r.status,200);
 }
 const ranks=await (await handle(request('/api/rankings'),db)).json();assert.equal(ranks.scopes.general.liked.length,10);assert.equal(ranks.scopes.thefrezza.liked.length,10);assert.equal(ranks.scopes.solasias.liked.length,3);
 assert.ok(ranks.scopes.solasias.liked.every(x=>x.artist==='Solasias'));assert.ok(ranks.scopes.thefrezza.liked.every(x=>x.artist==='theFrezza'));
});

test('a verified account liking through several apps counts as one person',async()=>{
 const db=database();for(const app of ['FREZZAMUSIC','SOLASIAS','THEFREZZA']){
  const token=await register(db,app);
  const response=await handle(request('/api/like',{...input,liked:true,sequence:1,firebaseIdToken:'signed-token'},token),db,{now:110000,resolve,projectId:'project',verifyIdentity:async()=>({uid:'same-user',email:'user@example.test',emailVerified:true,name:'User'})});assert.equal(response.status,200);
 }
 const ranks=await (await handle(request('/api/rankings'),db)).json();assert.equal(ranks.scopes.general.liked[0].total,1);
});
