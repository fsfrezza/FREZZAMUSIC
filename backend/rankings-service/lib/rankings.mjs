import { verifyFirebaseIdentity } from "./firebase-identity.mjs";
const APPS = new Set(['FREZZAMUSIC', 'SOLASIAS', 'THEFREZZA']);
const ROOTS = { theFrezza: '1dg1NjJjaN050ewNOwRVMke9CK3Bc2wBa', Solasias: '1T0WhRmxjXus3UVdOZJmibRzZxY3kFnbO' };
const ID = /^[A-Za-z0-9_-]{10,100}$/;
const UUID = /^[a-f0-9-]{36}$/i;
const json = (data, status = 200) => Response.json(data, { status, headers: { 'cache-control': 'no-store' } });
async function hash(s) { return Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(s)))).map(x => x.toString(16).padStart(2,'0')).join(''); }
function fail(status, message) { throw Object.assign(new Error(message), { status }); }
const title = s => s.replace(/^\s*\d+\s*(?:[-.]\s*|\s+)/, '').replace(/\.mp3$/i, '').trim();
const decode = s => s.replace(/&#(x[0-9a-f]+|\d+);/gi, (_, v) => String.fromCodePoint(v[0].toLowerCase() === 'x' ? parseInt(v.slice(1),16) : parseInt(v,10))).replace(/&(amp|quot|apos|lt|gt);/g, (_, v) => ({amp:'&',quot:'"',apos:"'",lt:'<',gt:'>'})[v]);
export function parseFolder(html) {
  if (!html.includes('class="flip-entries"')) fail(502, 'Listagem pública indisponível');
  const chunks = html.split('<div class="flip-entry"').slice(1);
  if (chunks.length >= 100) fail(502, 'Listagem potencialmente incompleta');
  return chunks.map(c => {
    const id = /id="entry-([A-Za-z0-9_-]+)"/.exec(c)?.[1];
    const name = /class="flip-entry-title">([\s\S]*?)<\/div>/.exec(c)?.[1];
    const url = /<a href="(https:\/\/drive.google.com\/[^\"]+)"/.exec(c)?.[1];
    if (!id || !name || !url) fail(502, 'Item inválido');
    return { id, name:decode(name.replace(/<[^>]+>/g,'')), folder:url.includes('/drive/folders/') };
  });
}
async function folder(id) {
  const r = await fetch(`https://drive.google.com/embeddedfolderview?id=${id}&hl=en`, {signal:AbortSignal.timeout(15000)});
  if (!r.ok || new URL(r.url).hostname !== 'drive.google.com') fail(502, 'Drive indisponível');
  const html = await r.text(); if (html.length > 2_000_000) fail(502,'Listagem grande demais');
  return parseFolder(html);
}
export async function resolveTrack(input) {
  if (!ROOTS[input.artist] || !ID.test(input.driveId ?? '') || !ID.test(input.folderId ?? '')) fail(400,'Música inválida');
  const albums = await folder(ROOTS[input.artist]);
  const album = albums.find(x => x.folder && x.id === input.folderId);
  if (!album) fail(404,'Álbum fora do catálogo oficial');
  const files = await folder(input.folderId);
  const track = files.find(x => !x.folder && x.id === input.driveId && /\.mp3$/i.test(x.name));
  if (!track) fail(404,'Música fora do catálogo oficial');
  const images = files.filter(x => !x.folder && /\.(png|jpe?g|webp)$/i.test(x.name));
  const cover = images.find(x => /capa|cover|front/i.test(x.name)) ?? images[0];
  return { driveId:track.id, folderId:album.id, artist:input.artist, title:title(track.name), album:title(album.name), coverId:cover?.id ?? null };
}
async function body(req) {
  const raw = await req.text(); if (raw.length > 8192) fail(413,'Evento grande demais');
  try { return JSON.parse(raw); } catch { fail(400,'JSON inválido'); }
}
async function limit(db, key, window, max) {
  const row = await db.prepare('INSERT INTO rate_limits (rate_key, window, count) VALUES (?, ?, 1) ON CONFLICT(rate_key, window) DO UPDATE SET count = count + 1 RETURNING count').bind(key,window).first();
  if (row.count > max) fail(429,'Tente novamente mais tarde');
}
async function identity(req,db,now) {
  const token = req.headers.get('authorization')?.replace(/^Bearer /,'');
  if (!/^[a-f0-9]{64}$/.test(token ?? '')) fail(401,'Identificação ausente');
  const key = await hash(token);
  if (!await db.prepare('SELECT token_hash FROM listeners WHERE token_hash = ?').bind(key).first()) fail(401,'Identificação inválida');
  await limit(db,key,Math.floor(now/60000),30);
  return key;
}
async function ensureTrack(db,input,now,resolve) {
  if (!ROOTS[input.artist] || !ID.test(input.driveId ?? '') || !ID.test(input.folderId ?? '')) fail(400,'Música inválida');
  const cached = await db.prepare('SELECT * FROM tracks WHERE drive_id = ?').bind(input.driveId).first();
  if (cached && cached.artist === input.artist && cached.folder_id === input.folderId && now - cached.verified_at < 3_600_000) return cached;
  const t = await resolve(input);
  await db.prepare('INSERT INTO tracks (drive_id, folder_id, artist, title, album, cover_id, verified_at) VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT(drive_id) DO UPDATE SET folder_id=excluded.folder_id,artist=excluded.artist,title=excluded.title,album=excluded.album,cover_id=excluded.cover_id,verified_at=excluded.verified_at').bind(t.driveId,t.folderId,t.artist,t.title,t.album,t.coverId,now).run();
  return t;
}
export async function handle(req,db,{now=Date.now(),resolve=resolveTrack,projectId = /** @type {string | undefined} */ (undefined),verifyIdentity=verifyFirebaseIdentity}={}) {
  try {
    if (!db) fail(503,'Base de rankings indisponível');
    const path = new URL(req.url).pathname;
    if (req.method === 'GET' && path === '/api/rankings') {
      const scopes = {};
      for (const [scope,artist] of [['general',null],['solasias','Solasias'],['thefrezza','theFrezza']]) {
        const where = artist ? ' WHERE t.artist = ?' : '';
        const select = 't.drive_id AS driveId,t.folder_id AS folderId,t.artist,t.title,t.album,t.cover_id AS coverId';
        const query = (kind) => db.prepare(`SELECT ${select},${kind === 'played' ? 'COUNT(*)' : 'COUNT(DISTINCT COALESCE(s.user_id,s.listener))'} AS total FROM ${kind === 'played' ? 'plays' : 'likes'} s JOIN tracks t ON t.drive_id=s.track_id${kind === 'liked' ? ' AND s.active=1' : ''}${where} GROUP BY t.drive_id ORDER BY total DESC,t.title COLLATE NOCASE,t.drive_id LIMIT 10`);
        const played = artist ? await query('played').bind(artist).all() : await query('played').all();
        const liked = artist ? await query('liked').bind(artist).all() : await query('liked').all();
        scopes[scope] = { played:played.results, liked:liked.results };
      }
      return json({scopes,updatedAt:new Date(now).toISOString(),listeningThresholdSeconds:30,likesUnit:'user-or-anonymous-installation'});
    }
    if (req.method !== 'POST') fail(405,'Método não permitido');
    if (path === '/api/register') {
      const data = await body(req); if (!APPS.has(data.app)) fail(400,'Aplicativo inválido');
      const ip = req.headers.get('cf-connecting-ip') ?? 'unknown';
      await limit(db,await hash(ip+Math.floor(now/86400000)),Math.floor(now/86400000),50);
      const token = Array.from(crypto.getRandomValues(new Uint8Array(32))).map(x=>x.toString(16).padStart(2,'0')).join('');
      await db.prepare('INSERT INTO listeners (token_hash, app, created_at) VALUES (?, ?, ?)').bind(await hash(token),data.app,now).run();
      return json({token},201);
    }
    const listener = await identity(req,db,now); const data = await body(req);
    let userId=null;
    if(data.firebaseIdToken && projectId) {
      const profile=await verifyIdentity(data.firebaseIdToken,projectId,{now});
      if(!profile)fail(401,'Login Firebase inválido');
      userId=profile.uid;
      await db.prepare('INSERT INTO profiles (user_id,email,email_verified,display_name,created_at,last_seen_at) VALUES (?,?,?,?,?,?) ON CONFLICT(user_id) DO UPDATE SET email=excluded.email,email_verified=excluded.email_verified,display_name=excluded.display_name,last_seen_at=excluded.last_seen_at').bind(profile.uid,profile.email,profile.emailVerified?1:0,profile.name,now,now).run();
      await db.prepare('INSERT INTO listener_logins (listener,user_id,linked_at) VALUES (?,?,?) ON CONFLICT(listener,user_id) DO UPDATE SET linked_at=excluded.linked_at').bind(listener,userId,now).run();
    }
    if(path === '/api/identify') {
      if(!projectId)fail(503,'Projeto Firebase ainda não configurado');
      if(!userId)fail(401,'Login Firebase necessário');
      return json({linked:true});
    }
    if (path === '/api/like') {
      if (typeof data.liked !== 'boolean' || !Number.isSafeInteger(data.sequence) || data.sequence < 1) fail(400,'Curtida inválida');
      await ensureTrack(db,data,now,resolve);
      await db.prepare('INSERT INTO likes (listener,track_id,active,sequence,user_id) VALUES (?,?,?,?,?) ON CONFLICT(listener,track_id) DO UPDATE SET active=excluded.active,sequence=excluded.sequence,user_id=excluded.user_id WHERE excluded.sequence > likes.sequence').bind(listener,data.driveId,data.liked ? 1 : 0,data.sequence,userId).run();
      return json({ok:true});
    }
    if (path === '/api/listen/start') {
      if (!UUID.test(data.eventId ?? '')) fail(400,'Evento inválido');
      await ensureTrack(db,data,now,resolve);
      await db.prepare('INSERT OR IGNORE INTO listening_sessions (event_id,listener,track_id,started_at,user_id) VALUES (?,?,?,?,?)').bind(data.eventId,listener,data.driveId,now,userId).run();
      return json({ok:true});
    }
    if (path === '/api/listen/complete') {
      if (!UUID.test(data.eventId ?? '') || !Number.isFinite(data.listenedSeconds) || data.listenedSeconds < 30) fail(400,'Reprodução insuficiente');
      const session = await db.prepare('SELECT * FROM listening_sessions WHERE event_id=? AND listener=?').bind(data.eventId,listener).first();
      if (!session) fail(404,'Sessão não encontrada');
      if (now-session.started_at < 30000) fail(409,'Reprodução insuficiente');
      if (now-session.started_at > 86400000) fail(410,'Sessão expirada');
      await db.prepare('INSERT OR IGNORE INTO plays (event_id,listener,track_id,created_at,user_id) VALUES (?,?,?,?,?)').bind(data.eventId,listener,session.track_id,now,session.user_id).run();
      return json({ok:true});
    }
    fail(404,'Rota não encontrada');
  } catch (e) { return json({error:e.status ? e.message : 'Serviço temporariamente indisponível'},e.status ?? 503); }
}
