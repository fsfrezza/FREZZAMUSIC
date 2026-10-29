import { preview } from '@/lib/preview.mjs';
export const dynamic='force-dynamic';
const code=(request: Request)=>new URL(request.url).pathname.split('/').pop()||'';
export async function GET(request: Request){return preview(request,code(request));}
export async function HEAD(request: Request){return preview(request,code(request));}
export async function OPTIONS(request: Request){return preview(request,code(request));}
