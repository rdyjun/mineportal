# MinePortal Windows background agent

This component lets the MinePortal website connect to Minecraft servers from the user's own
Windows PC and network. It is deliberately headless: it has no window, tray icon, popup,
settings screen, login form, server list or chat UI.

All user interaction happens at [mineportal.kr](https://mineportal.kr):

1. The website signs the user in and creates a short-lived pairing code.
2. A `mineportal://` link starts or reuses the background agent.
3. The agent maintains an outbound TLS WebSocket to MinePortal.
4. Connect, disconnect and chat commands arrive over that authenticated channel.
5. Minecraft traffic goes directly from the user's PC to the selected server.

The agent does not listen on a local network port. Its only local persistent credential is the
device token in `%USERPROFILE%\.mineportal\device.json`; Minecraft access tokens and chat-signing
keys stay in process memory.

## Build

Java 17 or newer is required.

```bat
gradlew.bat build
```

The fat JAR is written to `build/libs/mineportal-client.jar`. Windows packaging is expected to
launch it with `javaw` so no console window appears.

## Updates

The agent contains no native update UI and does not download executable updates by itself.
Release and update guidance belongs on the website; users replace the installed package from the
same web download flow they used initially.
