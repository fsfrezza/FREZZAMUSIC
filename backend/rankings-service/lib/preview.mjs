import previews from './preview-catalog.json' with {type:'json'};
const origin='https://fsfrezza.github.io';
export async function preview(request,code,fetcher=fetch){
 const headers={'Access-Control-Allow-Origin':origin,'Access-Control-Allow-Methods':'GET,HEAD,OPTIONS','Access-Control-Allow-Headers':'Range','Access-Control-Expose-Headers':'Content-Length,Content-Range,Accept-Ranges','Vary':'Origin'};
 if(request.method==='OPTIONS')return new Response(null,{status:204,headers});
 if(!['GET','HEAD'].includes(request.method))return new Response('Method not allowed',{status:405,headers});
 if(!/^[sf][at][A-Za-z0-9_-]{16}$/.test(code))return new Response('Not found',{status:404,headers});
 const range=request.headers.get('Range')||'bytes=0-';
 if(!/^bytes=\d+-\d*$/.test(range))return new Response('Invalid range',{status:416,headers});
 try{
  let src=Object.hasOwn(previews,code)?previews[code]:null;
  if(!src){
   const descriptor=await fetcher('https://fsfrezza.github.io/FREZZAMUSIC/listen/'+code+'.json',{signal:AbortSignal.timeout(10000)});
   if(!descriptor.ok)return new Response('Not found',{status:404,headers});
   const entry=await descriptor.json();const url=new URL(entry.src);
   if(url.protocol!=='https:'||url.hostname!=='drive.usercontent.google.com'||url.pathname!=='/download'||!(/^[A-Za-z0-9_-]+$/).test(url.searchParams.get('id')||''))return new Response('Invalid source',{status:502,headers});
   src=url.href;
  }
  const upstream=await fetcher(src,{headers:{Range:range},signal:AbortSignal.timeout(25000)});
  if(![200,206].includes(upstream.status)||!/^audio\//i.test(upstream.headers.get('content-type')||'')){await upstream.body?.cancel();return new Response('Audio unavailable',{status:502,headers});}
  const out=new Headers(headers);out.set('Content-Type','audio/mpeg');out.set('Cache-Control','public, max-age=3600');out.set('Accept-Ranges','bytes');
  for(const name of ['Content-Length','Content-Range']){const value=upstream.headers.get(name);if(value)out.set(name,value);}
  if(request.method==='HEAD'){await upstream.body?.cancel();return new Response(null,{status:upstream.status,headers:out});}
  return new Response(upstream.body,{status:upstream.status,headers:out});
 }catch{return new Response('Audio temporarily unavailable',{status:502,headers});}
}
