------------------------------ MODULE PlaySession ------------------------------
\* One desktop Minecraft session (ChatClient) plus the process-wide Microsoft
\* token (BackendClient) and a second server slot that shares that token.
\*
\* What the desktop code does, and what this spec checks:
\* \* TCP connect is not announced. onConnected runs from ClientboundLoginPacket,
\*   once per connect() (joinAnnounced). A second login packet does not announce.
\* \* A transfer does not reset joinAnnounced, does not emit the old session's
\*   disconnect, and does not announce the replacement's login again.
\* \* The transfer bound is 8 in ChatClient; the model uses 1 so the "one more
\*   transfer closes the session" rule stays finite. The same guard rejects a
\*   bad host or port.
\* \* User cancel bumps the generation and sets stopped before the socket
\*   callback, so a late login or the disconnect callback cannot revive it.
\* \* The control socket closing, or clear-account / unpair, drops the in-memory
\*   Minecraft token and every live session. The device grant file is not a
\*   Minecraft token. Reconnect can resume the device grant, but the Minecraft
\*   token stays absent until the server pushes a new one.
\* \* A second server can be live while this session is cancelled. Wiping the
\*   token closes both. Connect is ignored while the token is absent.
\*
\* Trial-mode McConnectionManager is different: it announces "connected" from the
\* first play chat packet, not from ClientboundLoginPacket, and it does not
\* record that the login packet was seen. RelayAnnounce.tla models that code.
\* This module models the desktop path, which is what ChatClientTest locks in.

EXTENDS Naturals

MaxGen == 3
MaxTransfers == 1

VARIABLES
  phase,          \* "idle" | "connecting" | "play" | "closed"
  gen,
  inflight,       \* generation of the session that may still become current; 0 = none
  everLogin,      \* Play login observed for the current connect(), not across transfers
  announced,
  announceCount,  \* number of onConnected calls for the current connect()
  stopped,
  transfers,
  transferring,   \* replacement handshake; login must not announce again
  token,          \* "none" | "memory"  — never a disk value
  stored,         \* "empty" | "device" — device.json only
  link,           \* "up" | "down"
  sibling         \* second server: "idle" | "live"

vars == <<phase, gen, inflight, everLogin, announced, announceCount, stopped,
          transfers, transferring, token, stored, link, sibling>>

Init ==
  /\ phase = "idle"
  /\ gen = 0
  /\ inflight = 0
  /\ everLogin = FALSE
  /\ announced = FALSE
  /\ announceCount = 0
  /\ stopped = FALSE
  /\ transfers = 0
  /\ transferring = FALSE
  /\ token = "none"
  /\ stored = "empty"
  /\ link = "up"
  /\ sibling = "idle"

\* Pairing persists a device grant and does not write a Minecraft access token.
SaveDevice ==
  /\ stored' = "device"
  /\ UNCHANGED <<phase, gen, inflight, everLogin, announced, announceCount,
                 stopped, transfers, transferring, token, link, sibling>>

\* Server push after the app is paired. Memory only.
PushToken ==
  /\ link = "up"
  /\ stored = "device"
  /\ token = "none"
  /\ token' = "memory"
  /\ UNCHANGED <<phase, gen, inflight, everLogin, announced, announceCount,
                 stopped, transfers, transferring, stored, link, sibling>>

StartConnect ==
  /\ link = "up"
  /\ token = "memory"
  /\ phase \in {"idle", "closed"}
  /\ gen < MaxGen
  /\ phase' = "connecting"
  /\ gen' = gen + 1
  /\ inflight' = gen + 1
  /\ everLogin' = FALSE
  /\ announced' = FALSE
  /\ announceCount' = 0
  /\ stopped' = FALSE
  /\ transfers' = 0
  /\ transferring' = FALSE
  /\ UNCHANGED <<token, stored, link, sibling>>

\* The only step that announces a connection.
PlayLogin ==
  /\ phase = "connecting"
  /\ ~transferring
  /\ inflight = gen
  /\ ~stopped
  /\ link = "up"
  /\ phase' = "play"
  /\ everLogin' = TRUE
  /\ announced' = TRUE
  /\ announceCount' = announceCount + 1
  /\ UNCHANGED <<gen, inflight, stopped, transfers, transferring, token, stored,
                 link, sibling>>

\* Own the replacement. The old socket's disconnect is ignored and joinAnnounced
\* stays set, so the new login will not call onConnected again.
Transfer ==
  /\ phase = "play"
  /\ transfers < MaxTransfers
  /\ gen < MaxGen
  /\ ~stopped
  /\ link = "up"
  /\ transfers' = transfers + 1
  /\ transferring' = TRUE
  /\ phase' = "connecting"
  /\ gen' = gen + 1
  /\ inflight' = gen + 1
  /\ everLogin' = FALSE
  /\ UNCHANGED <<announced, announceCount, stopped, token, stored, link, sibling>>

TransferLogin ==
  /\ phase = "connecting"
  /\ transferring
  /\ inflight = gen
  /\ ~stopped
  /\ link = "up"
  /\ phase' = "play"
  /\ transferring' = FALSE
  /\ everLogin' = TRUE
  /\ UNCHANGED <<gen, inflight, announced, announceCount, stopped, transfers,
                 token, stored, link, sibling>>

\* Count, host, or port rejected. The current session is disconnected once.
RejectTransfer ==
  /\ phase = "play"
  /\ transfers = MaxTransfers
  /\ ~stopped
  /\ phase' = "closed"
  /\ announced' = FALSE
  /\ stopped' = TRUE
  /\ inflight' = 0
  /\ UNCHANGED <<gen, everLogin, announceCount, transfers, transferring, token,
                 stored, link, sibling>>

\* stopped is set before the socket callback, so this step itself is the cancel;
\* a later login is not an action that can set announced.
UserCancel ==
  /\ phase \in {"connecting", "play"}
  /\ gen < MaxGen
  /\ stopped' = TRUE
  /\ gen' = gen + 1
  /\ inflight' = 0
  /\ phase' = "closed"
  /\ announced' = FALSE
  /\ transferring' = FALSE
  /\ UNCHANGED <<everLogin, announceCount, transfers, token, stored, link, sibling>>

\* onClose / onError: clearAccount, then schedule reconnect. Device file stays.
NetDrop ==
  /\ link = "up"
  /\ link' = "down"
  /\ token' = "none"
  /\ phase' = "closed"
  /\ announced' = FALSE
  /\ stopped' = TRUE
  /\ inflight' = 0
  /\ transferring' = FALSE
  /\ sibling' = "idle"
  /\ UNCHANGED <<gen, everLogin, announceCount, transfers, stored>>

NetReconnect ==
  /\ link = "down"
  /\ link' = "up"
  /\ UNCHANGED <<phase, gen, inflight, everLogin, announced, announceCount,
                 stopped, transfers, transferring, token, stored, sibling>>

\* clear-account, logout, or session expiry. The device grant remains.
ClearToken ==
  /\ token = "memory"
  /\ token' = "none"
  /\ phase' = "closed"
  /\ announced' = FALSE
  /\ stopped' = TRUE
  /\ inflight' = 0
  /\ transferring' = FALSE
  /\ sibling' = "idle"
  /\ UNCHANGED <<gen, everLogin, announceCount, transfers, stored, link>>

Unpair ==
  /\ stored' = "empty"
  /\ token' = "none"
  /\ phase' = "closed"
  /\ announced' = FALSE
  /\ stopped' = TRUE
  /\ inflight' = 0
  /\ transferring' = FALSE
  /\ sibling' = "idle"
  /\ UNCHANGED <<gen, everLogin, announceCount, transfers, link>>

\* Second server sharing the same in-memory account. Cancelling this session
\* does not stop the sibling; dropping the token does.
StartSibling ==
  /\ link = "up"
  /\ token = "memory"
  /\ sibling = "idle"
  /\ sibling' = "live"
  /\ UNCHANGED <<phase, gen, inflight, everLogin, announced, announceCount,
                 stopped, transfers, transferring, token, stored, link>>

StopSibling ==
  /\ sibling = "live"
  /\ sibling' = "idle"
  /\ UNCHANGED <<phase, gen, inflight, everLogin, announced, announceCount,
                 stopped, transfers, transferring, token, stored, link>>

Pause == UNCHANGED vars

Next ==
  \/ SaveDevice
  \/ PushToken
  \/ StartConnect
  \/ PlayLogin
  \/ Transfer
  \/ TransferLogin
  \/ RejectTransfer
  \/ UserCancel
  \/ NetDrop
  \/ NetReconnect
  \/ ClearToken
  \/ Unpair
  \/ StartSibling
  \/ StopSibling
  \/ Pause

Spec ==
  Init /\ [][Next]_vars
  /\ WF_vars(PlayLogin)
  /\ WF_vars(NetReconnect)
  /\ WF_vars(PushToken)

TypeOK ==
  /\ phase \in {"idle", "connecting", "play", "closed"}
  /\ gen \in 0..MaxGen
  /\ inflight \in 0..MaxGen
  /\ everLogin \in BOOLEAN
  /\ announced \in BOOLEAN
  /\ announceCount \in 0..1
  /\ stopped \in BOOLEAN
  /\ transfers \in 0..MaxTransfers
  /\ transferring \in BOOLEAN
  /\ token \in {"none", "memory"}
  /\ stored \in {"empty", "device"}
  /\ link \in {"up", "down"}
  /\ sibling \in {"idle", "live"}

\* Announced only after a Play login on this connect(), and only once.
AnnouncedOnlyAfterLogin == announced => everLogin \/ transferring

FirstConnectIsSilentUntilLogin ==
  phase = "connecting" /\ ~transferring => ~announced /\ announceCount = 0

AnnounceAtMostOnce == announceCount <= 1

PlayHasAnnouncedLogin == phase = "play" => announced /\ everLogin /\ token = "memory" /\ link = "up"

\* Cancel and socket loss both clear the announcement. A late login has no
\* action that can set it again until a new connect().
StoppedIsNotAnnounced == stopped => ~announced

MicrosoftTokenIsNotStored == stored \in {"empty", "device"}

DownHasNoMinecraftSession ==
  link = "down" => token = "none" /\ ~announced /\ sibling = "idle" /\ phase # "play"

\* The sibling is another server for the same account, not a second copy of this
\* session. It can outlive a cancel of this session, and it cannot outlive the token.
SiblingSharesToken == sibling = "live" => token = "memory" /\ link = "up"

LoginArrives ==
  (phase = "connecting" /\ ~transferring /\ ~announced /\ link = "up"
     /\ token = "memory" /\ ~stopped)
    ~> (announced \/ phase = "closed" \/ link = "down" \/ token = "none" \/ stopped)

LinkRecovers == link = "down" ~> link = "up"

TokenRestored ==
  (link = "up" /\ token = "none" /\ stored = "device")
    ~> (token = "memory" \/ stored = "empty" \/ link = "down")

=============================================================================
