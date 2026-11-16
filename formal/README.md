# MinePortal state models

`ProtocolConnection.tla` models the safety boundary shared by the backend relay and the
headless desktop agent: address resolution, protocol selection, mod negotiation, activation,
chat sending, and cleanup.

The model deliberately does not claim that an arbitrary client-required mod can be emulated.
It proves the safer property: a connection cannot become active while a required handshake is
still unsatisfied, an insufficient client capability closes instead of satisfying that handshake,
and an unknown protocol is rejected instead of being sent through a random codec.

Implementation mapping:

- `Resolve`: `MinecraftAddressResolver`
- protocol observation: `PingService`
- protocol selection: `ProtocolCompatibility` and `ViaTranslation`
- mod negotiation: `ForgeHandshake`
- activate/send/close: `McConnectionManager` and `ChatClient`

The other modules model behavior around that boundary. They describe what the current
code does, including bounds chosen only to keep the state graph finite: a transfer cap of 1
instead of 8, and an explicit expire step instead of a five-minute wall clock.

| Module | Code | What TLC checks |
| --- | --- | --- |
| `DesktopPairing.tla` | `createPairingCode`, `consumePairingCode`, `DesktopApp.trusted` | Codes are single-use and are removed even when expired. Only the official `https://mineportal.kr` origin presents a well-formed code. Unpair clears that owner's outstanding codes and leaves the other owner's codes. A still-valid presented code is eventually consumed. |
| `DeviceGrant.tla` | `issueDeviceToken`, `ownerForDeviceToken`, `unpair` | A new device token revokes the previous id. An expired grant is deleted. Resume accepts only the current live id. |
| `PlaySession.tla` | `ChatClient`, `BackendClient` | TCP connect does not announce. `onConnected` happens on the Play login packet, once. Transfer keeps that announcement, ignores the old socket's disconnect, and does not announce the replacement login. User cancel and a late login cannot revive the attempt. The Minecraft access token lives only in memory; the device grant file is not that token. A socket drop clears the token and both server sessions, and a later resume does not restore the token until the server pushes a new one. A second server can stay up across a cancel of the first, and cannot stay up without the token. |
| `ServerAttempt.tla` | `ServerConfig` | Two servers keep independent attempt ids. `connected` is entered only from `CONNECTING` for the current attempt, so a late or foreign event cannot revive a closed attempt. A connecting server eventually leaves that state. |
| `RelayAnnounce.tla` | `McConnectionManager` | The trial-mode relay does **not** announce on `ClientboundLoginPacket`. The first play chat packet does, and that branch does not check that a login packet was seen. Stale and stopped listeners do not announce. |

`RelayAnnounce.tla` is the one place a model and the desktop rule disagree. In
`McConnectionManager` the login packet only registers the chat-signing key and returns;
`markConnected` runs when the first system or player chat arrives. A quiet server therefore
stays "connecting" after a successful Play login, and a chat packet delivered with no login
packet would still announce. `ChatClient` (formal mode) announces on the login packet, which
`PlaySession.tla` checks. The relay listener is an anonymous class inside `doConnect`, and
changing when trial mode flips to "connected" would change the status users see, so this pass
records the difference instead of editing that path.

Run every model with TLC 1.7.4 from the repository root:

```text
formal/check.sh
```

The script downloads the immutable official release and verifies SHA-256
`936a262061c914694dfd669a543be24573c45d5aa0ff20a8b96b23d01e050e88` before execution.
CI runs the same jar and the same configs. The protocol model's message counter is
intentionally a boolean (`sentMessage`): it checks whether sending is reachable outside
`Active`, while keeping the state graph finite and CI-bounded.

UI properties for the website are separate and use Bombadil. From the repository root:

```text
formal/ui/run.sh
```

That script serves `client/` behind a local stub. The stub answers `/api/account` and
`/api/servers` and turns `/api/account/login/microsoft` into a cookie session. It never
calls Microsoft and has no credentials. The Bombadil spec checks the login gate, that the
sidebar and the server grid show the same addresses, that the chat grid's slot count is the
selected layout, that both download buttons follow `downloadPlatform`, and that the visible
language matches the `ko` / `en` toggle. The production page can add a server and can drop
extra chat slots by switching layout. It has no control that deletes a saved server, and the
stub does not invent one.
