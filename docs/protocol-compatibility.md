# Protocol and mod compatibility

MinePortal uses the Java 17-compatible MCProtocolLib 26.1 codec as its native wire format.
ViaVersion and ViaBackwards 5.12.2 translate registered protocol versions on both the backend
relay and the headless Windows agent. A server-reported positive protocol that Via does not
recognize is rejected instead of being attempted with the wrong packet codec.

## Public-listing sample

The first 20 listings visible on minelist.kr on 2026-11-01 advertised the following version
families. This is a point-in-time compatibility sample, not a mirror or a crawler dataset.

| Advertised family | Protocol used for the compatibility test | Strategy |
| --- | ---: | --- |
| 1.19 | 759 | Via translation |
| 1.20–1.20.1 | 763 | Via translation |
| 1.21–1.21.1 | 767 | Via translation |
| 1.21.4 | 769 | Via translation |
| 1.21.7–1.21.8 | 772 | Via translation |
| 1.21.9–1.21.10 | 773 | Via translation |
| 1.21.11 | 774 | Via translation |
| 26.1–26.1.2 | 775 | Native codec |
| 26.2 | 776 | Via translation |
| 26.3 | 777 | Via translation |

Listings that advertise a wide range such as 1.7.2–26.1.2 are expected to negotiate the
current protocol exposed by their proxy. MinePortal does not claim native 1.7 protocol support;
if a server actually reports a legacy protocol outside ViaBackwards' graph, the connection is
rejected with an explicit unsupported-version error.

Minecraft DNS SRV records are honored when the default port is used. The backend validates and
pins the resolved public address before both status ping and login, preventing DNS rebinding from
turning a public-host request into an internal-network connection. The desktop agent performs the
same resolution locally and may intentionally connect to LAN addresses. If the backend cannot
inspect such an address, the agent performs its own status ping to discover the protocol and Forge
network version.

## Modded servers

- Vanilla, Paper, Spigot, BungeeCord, Velocity, and Fabric servers that do not require a client
  mod use the normal protocol path.
- Forge/FML status markers and the supported login/configuration handshake messages are handled.
- Unknown or malformed custom payloads are bounded and isolated so they cannot crash the relay.
- A server mod that requires real client-side code, assets, registries, or custom gameplay state
  cannot be made universally compatible by a chat-only proxy. MinePortal must fail closed when
  that required handshake cannot be satisfied; it must not pretend arbitrary mods are installed.

## Verification

`shared/src/test` covers the observed protocol matrix, DNS SRV selection and address pinning,
private-address rejection, local status parsing, and malformed Forge payloads. The TLA+ model in
`formal/ProtocolConnection.tla` checks the connection lifecycle invariants, including rejection of
unknown protocols, no activation before required mod negotiation, public address pinning on the
backend route, and cleanup on close.
