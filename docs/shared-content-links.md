# Shared music links (0.23)

Remote songs use Drive file IDs; albums use Drive folder IDs, independent of display titles, folder prefixes and old catalog IDs. Local files are not shared. MainActivity handles both cold start and singleTop onNewIntent, waits for the catalog and opens the album. A shared song offers an explicit Listen action and uses the album queue. It waits for the MediaController before enabling playback. No external audio URL is accepted.

Share controls appear in online song rows and album detail. A single web/share page reads identifiers plus optional presentation-only metadata. Explicit Android intent buttons address the band's package or FREZZAMUSIC, with browser fallback and installation instructions. The page never claims to detect installed apps. The only installation link is FREZZAMUSIC, using its current Drive APK file.

## Publication
The share-pages workflow deploys web/ to GitHub Pages. Repository Settings → Pages must use GitHub Actions, and the environment must permit the development/v0.23-links branch. Check the workflow result before circulating shared HTTPS URLs. The intended URL is https://fsfrezza.github.io/FREZZAMUSIC/share/ .

## Verified automatic opening is a separate deployment gate
A project Pages site cannot publish https://fsfrezza.github.io/.well-known/assetlinks.json by itself. Until that root file is deployed through the user site fsfrezza.github.io (or a dedicated domain), HTTPS links open the landing page and users choose a package-specific Open button. The manifest deliberately does not claim autoVerify. Do not present this as verified App Links yet.

To enable verification later, serve assetlinks.json at the host root with each package and its actual installed APK signing SHA-256 fingerprint, enable autoVerify and test on devices. Keep signing keys stable between updates. Multiple installed apps may still require user preference; no guaranteed app priority is claimed.

## Device acceptance
Test SOLASIAS only, theFrezza only, FREZZAMUSIC only, both band and main, and neither installed. Exercise links from Chrome and WhatsApp; fresh and running activities; empty/loaded/stale catalog; missing/deleted IDs; offline loading; wrong-band app; consecutive song links; changing sort order. Album opens without starting playback, song requires Listen. No physical-device verification has been performed by this source change.

## Short links (0.23.1)
All new shares use `/FREZZAMUSIC/s/?c=CODE`. Codes comprise the band/kind prefix plus the first 96 bits of SHA-256 over `artist|kind|Drive ID` in URL-safe base64. Metadata is absent from the URL. This is shortening/obfuscation, not authorization or encryption. Full legacy links still parse. No external shortening service is required.

The app resolves codes against its own current catalog. web/catalog.json supplies public titles/covers and legacy app compatibility; missing entries still offer code-based opening in 0.23.1. The generator refreshes metadata during Pages deployment, keeps old mappings, rejects collisions and writes atomically. On listing failure the committed snapshot remains available. New catalog content can be shared before site metadata refresh, with a generic band/title presentation until redeployment.

## Preferred app routing (0.23.3)
The public page has a single Open action and attempts the content artist's package first, then FREZZAMUSIC, then the download page. Fallback stages use URL fragments so the canonical compact query stays unchanged. Stage guards prevent repeated automatic launches; returning from an app does not restart the chain. Desktop does not auto-launch. Browser denial is not treated as proof an app is absent; a manual Open action remains available. Chrome may block automatic intents without user activation, so zero-tap browser routing is not guaranteed.

MainActivity also dispatches valid incoming links before creating the UI and onNewIntent: the compatible matching band app is preferred, otherwise FREZZAMUSIC. The current matching app handles locally, preventing bounce loops. Package visibility is limited to the three known packages; incompatible old app versions are skipped. Existing HTTPS verification deployment gates remain unchanged.

Only the FREZZAMUSIC flavor declares the canonical HTTPS host; band flavors keep the package-targeted custom scheme and therefore do not compete for the shared web URL. Native forwarding normalizes all accepted input to the custom scheme. Release verification checks the merged APK manifests for this host ownership rule. Old installed band APKs may still have their previous host associations until updated.

As of 0.23.15, only FREZZAMUSIC is offered for download for both artists, for tracks and albums. Band APKs older than versionCode 50 are skipped by native forwarding to avoid their old MAIN-first behavior creating a loop. Browser fallback tries the artist package, then MAIN, then keeps the single FREZZAMUSIC download link. Browser restrictions may require tapping Open.
