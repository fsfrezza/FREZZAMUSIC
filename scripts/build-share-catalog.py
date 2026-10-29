#!/usr/bin/env python3
"""Refresh public presentation metadata. Generate presentation metadata and individual browser preview descriptors."""
import base64,hashlib,html,json,re,urllib.request,concurrent.futures
from pathlib import Path
ROOTS={'solasias':'1T0WhRmxjXus3UVdOZJmibRzZxY3kFnbO','thefrezza':'1dg1NjJjaN050ewNOwRVMke9CK3Bc2wBa'}
def code(artist,kind,id):
 return ('s' if artist=='solasias' else 'f')+('a' if kind=='album' else 't')+base64.urlsafe_b64encode(hashlib.sha256(f'{artist}|{kind}|{id}'.encode()).digest()[:12]).decode().rstrip('=')
def listing(id):
 u=f'https://drive.google.com/embeddedfolderview?id={id}&hl=pt-BR'
 s=urllib.request.urlopen(u,timeout=30).read().decode()
 if 'flip-entries' not in s: raise ValueError('Public listing unavailable')
 rows=re.split(r'<div class="flip-entry"',s)[1:]
 if len(rows)>=100: raise ValueError('Unconfirmed complete listing')
 out=[]
 for row in rows:
  mid=re.search(r'id="entry-([A-Za-z0-9_-]+)"',row);name=re.search(r'<div class="flip-entry-title">(.*?)</div>',row,re.S)
  if not mid or not name: raise ValueError('Invalid listing entry')
  out.append((mid[1],html.unescape(re.sub('<[^>]+>','',name[1])),'https://drive.google.com/drive/folders/' in row))
 return out
def title(name):return re.sub(r'^\s*\d+\s*(?:[-.]\s*|\s+)','',name).strip()
def album_job(job):
 artist,folder,name=job;files=listing(folder)
 tracks=sorted([(id,n) for id,n,isfolder in files if not isfolder and n.lower().endswith('.mp3')],key=lambda x:x[1].casefold())
 images=[(id,n) for id,n,isfolder in files if not isfolder and n.lower().endswith(('.png','.jpg','.jpeg','.webp'))]
 candidates=[x for x in images if x[1].lower().endswith('.png')] or images
 cover=next((id for id,n in candidates if any(x in n.lower() for x in ['cover','capa','front'])),candidates[0][0] if candidates else None)
 data={'artist':artist,'album':title(name),'cover':f'https://drive.google.com/uc?export=download&id={cover}' if cover else None}
 preview_dir=Path(__file__).resolve().parents[1]/'web/listen';preview_dir.mkdir(exist_ok=True)
 for kind,target,track in [('album',folder,tracks[0] if tracks else None)]+[('track',tid,(tid,n)) for tid,n in tracks]:
  if track:
   descriptor={'title':re.sub(r'(?i)\.mp3$','',title(track[1])),'src':'https://drive.usercontent.google.com/download?export=download&id='+track[0]}
   dest=preview_dir/(code(artist,kind,target)+'.json');temp=dest.with_suffix('.tmp');temp.write_text(json.dumps(descriptor,ensure_ascii=False));temp.replace(dest)
 entries=[('album',folder,title(name))]+[('track',id,re.sub(r'(?i)\.mp3$','',title(n))) for id,n in tracks]
 return {code(artist,kind,id):dict(data,kind=kind,identity=hashlib.sha256(f'{artist}|{kind}|{id}'.encode()).hexdigest(),title=t) for kind,id,t in entries}
if __name__=='__main__':
 path=Path(__file__).resolve().parents[1]/'web/catalog.json'
 catalog=json.loads(path.read_text()) if path.exists() else {}
 # Migrate old snapshots without retaining raw audio or folder identifiers.
 for entry in catalog.values():
  if 'id' in entry:
   entry['identity']=hashlib.sha256((entry['artist']+'|'+entry['kind']+'|'+entry.pop('id')).encode()).hexdigest()
 jobs=[(artist,id,name) for artist,root in ROOTS.items() for id,name,isfolder in listing(root) if isfolder]
 with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
  for entries in pool.map(album_job,jobs):
   for key,entry in entries.items():
    if key in catalog and any(catalog[key][f]!=entry[f] for f in ['artist','kind','identity']): raise ValueError('Short code collision')
    catalog[key]=entry
 temp=path.with_suffix('.tmp');temp.write_text(json.dumps(catalog,ensure_ascii=False,sort_keys=True,separators=(',',':')));temp.replace(path)
 print(f'Share catalog: {len(catalog)} entries (old codes retained).')
