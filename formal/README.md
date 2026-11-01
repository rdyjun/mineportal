# MinePortal protocol state model

`ProtocolConnection.tla` models the safety boundary shared by the backend relay and the
headless desktop agent: address resolution, protocol selection, mod negotiation, activation,
chat sending, and cleanup.

The model deliberately does not claim that an arbitrary client-required mod can be emulated.
It proves the safer property: a connection cannot become active while a required handshake is
still unsatisfied, and an unknown protocol is rejected instead of being sent through a random
codec.

Implementation mapping:

- `Resolve`: `MinecraftAddressResolver`
- protocol observation: `PingService`
- protocol selection: `ProtocolCompatibility` and `ViaTranslation`
- mod negotiation: `ForgeHandshake`
- activate/send/close: `McConnectionManager` and `ChatClient`

Run TLC 1.7.4 from the repository root:

```text
java -jar tla2tools.jar -workers auto -config formal/ProtocolConnection.cfg formal/ProtocolConnection.tla
```

The CI workflow downloads the immutable official release and verifies SHA-256
`936a262061c914694dfd669a543be24573c45d5aa0ff20a8b96b23d01e050e88` before execution.
The message counter is intentionally represented as a boolean (`sentMessage`): the model verifies
whether sending is reachable outside `Active`, while keeping the state graph finite and CI-bounded.
