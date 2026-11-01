# Desktop validation — 2026-11-02

Verified locally on an arm64 Mac:

- Java 21 compilation and platform-native JavaFX shadow JAR build.
- 15 shared protocol tests passed; Java formatting passed.
- Two Node tests passed (Windows/client hints, macOS, iPad/iPhone/Android/Linux,
  blocked localStorage and button re-enabling).
- Real browser: macOS home chooses `MinePortal-mac-arm64.dmg`.
- Real browser with desktop user agent: Play is active; Home and trial switch
  are hidden; server list and chat controls remain.
- jpackage app image generated; launcher is Mach-O arm64, bundled runtime included.

Not yet verified: native GUI layout/interactions (Mac locked), Microsoft login,
email verification, pairing through WebEngine status events, real Minecraft
connections, Windows packaged runtime, signed/notarized installation, release
asset URLs and production deployment. The app loads the live website, so the
frontend must be released with the installers. Browser presentation checks do
not establish that the native WebView renders or authenticates correctly.

## Twenty candidate servers

Observed on https://minelist.kr/servers on 2026-11-02. These are a test queue,
not successful connection results. Attempt sequentially through the actual app,
record protocol, login, received chat, disconnect and any error, and do not send
public chat messages during validation. Recheck the current listing first.

| Address | App connection result |
| --- | --- |
| play.hypernetwork.kr | Pending |
| pikmon.kr | Pending |
| ddingtycoon.kr | Pending |
| playfarm.kr | Pending |
| leunesiseu.kr | Pending |
| mcng.kr | Pending |
| dongleland.com | Pending |
| seresonline.mcv.kr | Pending |
| dose.kr | Pending |
| play.planetearth.kr | Pending |
| online.eevee.kr | Pending |
| last-traveler.com | Pending |
| craftm.kr | Pending |
| beartown.kr | Pending |
| picosv.kr | Pending |
| playmc.kr | Pending |
| nspring.kr | Pending |
| rendog.kr | Pending |
| mineplanet.kr | Pending |
| kkulbi.kr | Pending |
