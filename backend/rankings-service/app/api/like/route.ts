import { env } from "cloudflare:workers";
import { handle } from "@/lib/rankings.mjs";
export const dynamic = "force-dynamic";
export async function POST(request: Request) { return handle(request, env.DB, { projectId: env.FIREBASE_PROJECT_ID }); }
