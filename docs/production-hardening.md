# Production hardening (0.23.2)

Release builds enable R8 code optimization/obfuscation and resource shrinking, exclude debug tooling, and disable debugging. Low-level Android Log calls, PrintStream diagnostics and printStackTrace are removed by R8. No explicit logging was found in application Kotlin sources. Consumer rules from libraries remain in effect; avoid blanket keep-all rules.

The public sharing metadata snapshot contains titles, artwork and hashed identities, not audio IDs or album-folder IDs. Open buttons use compact codes. Legacy long links remain supported. Artwork IDs and APK downloads remain public by design. Existing public Git history and app source can still expose identifiers. These changes do not turn public Drive music into private content.

CI tests the application and builds optimized unsigned release APKs, then verifies non-debuggable manifests and catalog obfuscation. Signing keys/passwords never belong in this public repository or its artifacts. R8 mapping files should be retained privately for diagnosing crashes and must not be bundled into APKs or public artifacts.

Optional build environment: FREZZAMUSIC_KEYSTORE_PATH, FREZZAMUSIC_KEYSTORE_PASSWORD, FREZZAMUSIC_KEY_ALIAS, FREZZAMUSIC_KEY_PASSWORD. No debug signing fallback is permitted for release. Without credentials, the build produces unsigned release APKs for private signing.

The previous CI workflow generated debug APKs without a configured stable signing key. A debug certificate in an APK does not contain the private key needed to sign an update. If the old signing key is unavailable, a new production key requires a one-time reinstall; do not replace public downloads silently before the migration is understood. Preserve existing user data before uninstalling. Signing keys must be backed up privately and reused for future releases.
