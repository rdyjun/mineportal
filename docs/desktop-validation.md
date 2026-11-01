# Desktop validation — 2026-11-02

## Local build and regression checks

- Server: 31 tests passed; desktop: 22 tests passed; download routing: two tests passed.
- Desktop tests include actual loopback TCP handshake/version checks, login readiness,
  owned server transfers, stale events, and cancellation during transfer resolution.
- Server HTTP tests reject desktop-only connections when the app is offline and never
  fall back to the web relay, including a disconnect race and pre-existing web connection.
- Server boot JAR and desktop shadow JAR builds and Java formatting passed.
- Real browser: Mac download chooses `MinePortal-mac-arm64.dmg`; desktop presentation
  opens Play and hides Home and trial controls.
- Java 21 runtime is bundled; the jpackage launcher is Mach-O arm64.
- Bundled `jdk.naming.dns` was verified with jimage; Minecraft SRV discovery must
  also work in the installer runtime, not just with a full development JDK.
- GitHub Actions on commit `88357cb` built the Windows ZIP and Mac arm64 DMG,
  verified their bundled DNS module, and kept both native launchers running for
  eight seconds in headless mode with the bundled runtime. Both package jobs passed:
  https://github.com/rdyjun/mineportal/actions/runs/41362502186
  This checks launcher/runtime startup, not GUI rendering or interactive login.

## Actual server checks

Microsoft authentication succeeded through the production website without requesting email
verification. The actual Java desktop engine ran on this M1 Mac in **headless mode**, paired
to that authenticated session, and connected directly from this PC. The production web UI
controlled the engine. This verifies engine behavior; it does **not** verify the new JavaFX
window, its OAuth handling or WebEngine status-event pairing.

Twenty servers from https://minelist.kr/servers were checked sequentially, with six
supplementary rechecks after the final error/handshake changes. Across those attempts,
8 servers reached Minecraft Play state and 7 also delivered chat/system messages.
These are short connection/receive checks, not long-duration stability guarantees.
Every test connection was closed, the temporary device grant was revoked, and its
credential file was confirmed deleted. No public chat or Discord messages were sent.
Fifteen missing queue addresses were added to the user's list; eight existing records
were retained, for 23 total records.

Raw chat text, account credentials and verification codes are excluded. Sanitized status
transitions, counts and timestamps are in `live-engine-validation.json` and
`live-engine-rechecks.json`. Counts are newly observed retained messages (capped by the
backend's 200-message history). The table combines observed capabilities across attempts;
intermittent failures are preserved in the JSON records.

| Server | Entered Play at least once | Chat received at least once | Latest result |
| --- | --- | --- | --- |
| play.hypernetwork.kr | Yes | Yes | Login/receive confirmed; then closed |
| pikmon.kr | No | No | Server requires a different protocol/version |
| ddingtycoon.kr | No | No | Transfer destination DNS failure |
| playfarm.kr | Yes | Yes | Login/receive confirmed; then closed |
| leunesiseu.kr | No | No | Server requires a different protocol/version |
| mcng.kr | Yes | No | Login confirmed; no chat during observation; then closed |
| dongleland.com | Yes | Yes | Login/receive confirmed; then closed |
| seresonline.mcv.kr | Yes | Yes | Login/receive confirmed; then closed |
| dose.kr | No | No | Transfer destination DNS failure |
| play.planetearth.kr | No | No | External Discord verification required; not performed |
| online.eevee.kr | No | No | Observation timed out; test connection closed |
| last-traveler.com | No | No | Server requires a different protocol/version |
| craftm.kr | Yes | Yes | Login/receive confirmed; then closed |
| beartown.kr | No | No | Connection error; see sanitized JSON |
| picosv.kr | No | No | Server requires a different protocol/version |
| playmc.kr | No | No | Server requires a different protocol/version |
| nspring.kr | Yes | Yes | Login/receive confirmed; then closed |
| rendog.kr | Yes | Yes | Login/receive confirmed; then closed |
| mineplanet.kr | No | No | Server requires a different protocol/version |
| kkulbi.kr | No | No | Transfer destination DNS failure |

## Outstanding

- The Mac is unlocked, but native computer control is awaiting Accessibility and
  Screen Recording permission approval. Native GUI layout/interactions, native OAuth
  and the WebEngine pairing event remain unverified. Do not bypass permissions.
- The app loads the live website; local frontend changes have not been deployed.
- The user explicitly approved GitHub push and draft PR creation. Draft PR
  https://github.com/rdyjun/mineportal/pull/31 is open. Windows/Mac package CI passed;
  the retired macOS 14 runner was replaced with macOS 15 arm64.
- Developer ID signing/notarization and production release assets are pending.
- Some gateways advertise a broad or missing status protocol but then demand a
  specific version at login. Recorded version failures are not evidence that the
  supported ViaVersion handshake is generally broken; the actual target-767
  loopback handshake test passed. Required client mods/external verification
  cannot be claimed as supported without completing their real requirements.
