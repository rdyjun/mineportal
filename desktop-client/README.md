# MinePortal desktop

Windows and Apple Silicon macOS GUI application. The JavaFX window uses the same
`https://mineportal.kr/` HTML/CSS as the website, with the `MinePortalDesktop` user
agent selecting a desktop presentation: Play opens immediately, Home and trial
mode are hidden. Microsoft login, server lists and chat stay inside the window.
Minecraft connections run locally through the existing Java connection engine.
An internet connection to MinePortal is required for authentication and control.

Pairing uses the existing short-lived backend code. The web page sends it through
WebEngine's status event; the app accepts it only from the exact MinePortal
origin. No privileged Java object is exposed to page scripts. Device credentials
remain in `~/.mineportal/device.json`; Minecraft credentials stay in memory.

## Build and run

Use JDK 21. JavaFX 21 native libraries are selected for the build machine's OS
and architecture; build each distribution on its target platform.

```sh
bash gradlew test shadowJar spotlessCheck
java -jar build/libs/mineportal-client.jar
```

`--headless` preserves the background-agent mode for web pairing and CI smoke
tests. Windows registers `mineportal://` with the installed launcher. In-app
pairing on macOS needs no OS protocol registration.

`.github/workflows/desktop-package.yml` builds Windows ZIP and Apple Silicon DMG
artifacts with their own Java runtime, so users do not install Java separately.
Mac downloads use `MinePortal-mac-arm64.dmg`; Windows uses `MinePortal-win.zip`.
The website and installers must be released together: do not deploy the download
links until the matching release assets are available. Mac Intel is not a
supported installer target yet; browser user agents cannot reliably distinguish
Intel Macs from Apple Silicon Macs.

## Release validation

Run the shared protocol tests, `node --test client/downloads.test.cjs`, launch
the packaged app, complete Microsoft login and pairing, check server connect,
received chat, disconnect, close/reopen, and network recovery on both platforms.
Verify M1 DMG installation and the production asset URLs before release.
Distributable macOS builds still require Developer ID signing and notarization;
local unsigned packages are development artifacts.
