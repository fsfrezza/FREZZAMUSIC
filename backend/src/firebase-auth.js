/**
 * Verify Firebase ID tokens using the Firebase Admin SDK.
 * Firebase project configuration and credentials belong to the server environment.
 */
export async function createFirebaseIdentityVerifier({projectId,adminAuth}={}) {
  if(typeof projectId!=="string" || !projectId.trim()) throw new TypeError("FIREBASE_PROJECT_ID obrigatório");
  let auth=adminAuth;
  if(!auth) {
    const {initializeApp,getApps,applicationDefault}=await import("firebase-admin/app");
    const {getAuth}=await import("firebase-admin/auth");
    const app=getApps().find(app=>app.name==="frezzamusic-backend") ??
      initializeApp({credential:applicationDefault(),projectId}, "frezzamusic-backend");
    auth=getAuth(app);
  }
  if(!auth || typeof auth.verifyIdToken!=="function") throw new TypeError("Firebase Auth inválido");
  return async function verifyFirebaseIdentity(authorization) {
    if(typeof authorization!=="string" || !/^Bearer [^\s]+$/.test(authorization)) return null;
    const token=authorization.slice(7);
    try {
      const decoded=await auth.verifyIdToken(token,true);
      if(typeof decoded.uid!=="string" || !decoded.uid || decoded.aud!==projectId ||
         decoded.iss!==`https://securetoken.google.com/${projectId}`) return null;
      return {userId:decoded.uid};
    }catch {
      return null;
    }
  };
}
