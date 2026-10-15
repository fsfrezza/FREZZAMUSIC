# Shared music links (0.23)

Remote songs use Drive file IDs; albums use Drive folder IDs, independent of display titles, folder prefixes and old catalog IDs. Local files are not shared. MainActivity handles both cold start and singleTop onNewIntent, waits for the catalog and opens the album. A shared song offers an explicit Listen action and uses the album queue. It waits for the MediaController before enabling playback. No external audio URL is accepted.

Share controls appear in online song rows and album detail. A single web/share page reads identifiers plus optional presentation-only metadata. Explicit Android intent buttons address the band's package or FREZZAMUSIC, with browser fallback and installation instructions. The page never claims to detect installed apps. Download buttons currently point to the user's existing Drive files, which must be updated to 0.23 APKs before public rollout.

## Publication
The share-pages workflow deploys web/ to GitHub Pages. Repository Settings → Pages must use GitHub Actions, and the environment must permit the development/v0.23-links branch. Check the workflow result before circulating shared HTTPS URLs. The intended URL is https://fsfrezza.github.io/FREZZAMUSIC/share/ .

## Verified automatic opening is a separate deployment gate
A project Pages site cannot publish https://fsfrezza.github.io/.well-known/assetlinks.json by itself. Until that root file is deployed through the user site fsfrezza.github.io (or a dedicated domain), HTTPS links open the landing page and users choose a package-specific Open button. The manifest deliberately does not claim autoVerify. Do not present this as verified App Links yet.

To enable verification later, serve assetlinks.json at the host root with each package and its actual installed APK signing SHA-256 fingerprint, enable autoVerify and test on devices. Keep signing keys stable between updates. Multiple installed apps may still require user preference; no guaranteed app priority is claimed.

## Device acceptance
Test SOLASIAS only, theFrezza only, FREZZAMUSIC only, both band and main, and neither installed. Exercise links from Chrome and WhatsApp; fresh and running activities; empty/loaded/stale catalog; missing/deleted IDs; offline loading; wrong-band app; consecutive song links; changing sort order. Album opens without starting playback, song requires Listen. No physical-device verification has been performed by this source change.
