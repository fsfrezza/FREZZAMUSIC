declare namespace Cloudflare {
  interface Env {
    DB?: D1Database;
    FIREBASE_PROJECT_ID?: string;
    BUCKET?: R2Bucket;
  }
}
