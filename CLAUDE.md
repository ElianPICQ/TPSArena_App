# CLAUDE.md

**TPS Arena Companion for Android**: native rewrite of the companion PWA (`TPSArena_Site/app/`). The player sees their level, stats, achievements, match history and leaderboards, manages friends and chats with them. The app talks to the backend only through the site's nginx, like the PWA: REST on `<site>/api/` (the Web BFF) and the chat WebSocket on `<site>/ws` (Real-time). Kotlin + Jetpack Compose (Material 3), OkHttp, kotlinx.serialization. Package `fr.tpsarena.companion`, minSdk 24, AGP 9 with built-in Kotlin (KGP version pinned in the root `build.gradle.kts`).

Code, comments and commits are in English. The UI is in French.

## Commands

- `./gradlew assembleDebug`: debug APK in `app/build/outputs/apk/debug/`.
- `./gradlew testDebugUnitTest`: unit tests (JVM, no device): API client against MockWebServer, Real-time protocol with a fake socket, the label and format ports.
- `tools/build_release.sh` (`tools/build_release.ps1` on Windows): copies the site's public mkcert CA (`TPSArena_Site/certs/tps-arena-ca.crt`) into `res/raw/`, builds the release APK, and copies it to `TPSArena_Site/releases/android/TPSArenaCompanion.apk`, where the site's manifest offers it within 30 s. `--no-copy` / `-NoCopy` only builds. The release is signed with `keystore.properties` (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`; git-ignored) when it exists, else with the debug key.
- On the emulator, the site is reachable at the host's LAN IP (`https://<ip>:8443`, its certificate names that IP). `10.0.2.2` doesn't match the certificate.

## Architecture

- `CompanionApp` (Application) holds the singletons, so the socket outlives the activity: `Settings` (SharedPreferences), `ApiClient`, `Cache`, `Realtime`, `SessionManager`, `Notifier`. It follows the foreground (ProcessLifecycleOwner) and the network (default network callback) to retry like the PWA's `visibilitychange` / `online` handlers.
- `data/ApiClient.kt` mirrors the PWA's `lib/api.ts`. The access token stays in memory. The web-bff sets the refresh token as an HttpOnly, `Secure` cookie (`tpsarena_refresh`, Path=/api/auth): the client reads `Set-Cookie` itself and sends `Cookie:` only on `/auth/*`, so it works over plain HTTP too (no browser cookie jar). Refreshes are serialized by a mutex, and the rotated token is written with `commit()` before use. Reason: Auth revokes **every** session when a rotated refresh token is reused. The backend was not changed.
- `realtime/Realtime.kt` ports `lib/realtime.ts`: `auth` with `"client": "mobile"` (first auth only), heartbeats, re-auth before the token expires, backoff 1/2/5/10/30 s, back to login on 4001 `session_revoked` / 4003, pending sends matched by `client_id` (errors reject the oldest pending one). State is a `StateFlow<RealtimeState>`; alerts go to `Notifier`. All of it runs on the main dispatcher; socket callbacks hop onto it. The open conversation only counts as read while the app is visible (`setVisible`).
- Background notifications: no FCM on the LAN, so `notify/ChatService` (foreground service, type `remoteMessaging`, not sticky) keeps the process and socket alive while signed in. It only runs if the menu toggle « Notifications en arrière-plan » is on and notifications are allowed. Without it, the socket is closed when the app leaves the foreground (`Realtime.pause`), and friends see the player offline. The service can only be started from the foreground (Android 12+).
- `ui/Resource.kt` mirrors `lib/resource.ts`: show the cached data (`Cache`, one JSON file per player and screen under `filesDir/cache`), then the server's. Offline, it shows the cached data with its date. Each screen has a small ViewModel in its file under `ui/screens/`. `SignedInModel` holds the friend list for the whole signed-in UI (tab badge, names in notifications) and reloads it when `friendsVersion` changes.
- `ui/AppRoot.kt`: auth state → login or signed-in shell (header with menu, Navigation Compose, tab bar, toasts). The UI is keyed by `(user, online)`, so going back online rebuilds the screens, like the PWA's dependency on `online`. Routes are in `realtime/Routes`; notifications open them through `MainActivity.EXTRA_ROUTE`.
- Theme: the site's colors (`ui/theme/Theme.kt`, from `TPSArena_Site/src/styles.css` ← the game's `ui_theme.gd`), Orbitron / Rajdhani fonts copied from the site, chamfered panels via `CutCornerShape`. Dark only. Icons are the PWA's SVG paths (`ui/components/Icons.kt`). The launcher icon redraws `public/icons/maskable-512.png` as vectors.
- `res/xml/network_security_config.xml`: cleartext allowed (any LAN IP), and HTTPS trusts system, user-installed and the bundled mkcert CA. The bundled CA belongs to the machine that built the APK, so rebuild with `tools/build_release.sh` on the machine that serves the site.

## Contract with the backend and the site

These are mirrored from the PWA and must change with it: error codes → texts (`data/Labels.kt`, same as the PWA's `labels.ts` and the game's `ApiClient.ERROR_MESSAGES`), map ids, achievement ids (`backend/services/stats/stats/progression.py`), `xp_to_next_level` (the whole level's span), Real-time close codes and messages (`backend/services/realtime/PROTOCOL.md`), web-bff routes (`backend/services/web-bff/web_bff/routes/`). `Labels.kt` also has the app's own codes (`no_server`, `invalid_server_url`, `untrusted_certificate`).
