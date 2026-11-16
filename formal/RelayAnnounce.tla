----------------------------- MODULE RelayAnnounce -----------------------------
\* Trial-mode announcement in McConnectionManager, together with the attempt
\* check in ServerConfig.markConnected.
\*
\* The listener returns from ClientboundLoginPacket after registering the
\* signing key. It does not mark the server connected there. The first system
\* or player chat packet is what calls markConnected, and that branch does not
\* look at whether a login packet was seen. markConnected itself still requires
\* the listener's attempt id and CONNECTING, so a packet from a stopped or
\* superseded attempt does not announce. beginConnection is refused while the
\* server is active, so a new attempt starts only after close.
\*
\* This spec does not claim "connected implies a login packet was seen". That
\* is true of ChatClient and is checked in PlaySession.tla. It is not true of
\* this listener.

EXTENDS Naturals

MaxAttempt == 2

VARIABLES
  server,      \* "connecting" | "connected" | "closed"
  loginSeen,
  viaChat,     \* markConnected ran from a chat packet
  attempt,     \* attempt id captured by this listener
  current,     \* ServerConfig.lastAttemptId
  stopped

vars == <<server, loginSeen, viaChat, attempt, current, stopped>>

Init ==
  /\ server = "connecting"
  /\ loginSeen = FALSE
  /\ viaChat = FALSE
  /\ attempt = 1
  /\ current = 1
  /\ stopped = FALSE

\* This listener is the one ServerConfig will still accept.
Current == ~stopped /\ attempt = current /\ server = "connecting"

LoginPacket ==
  /\ Current
  /\ loginSeen' = TRUE
  /\ UNCHANGED <<server, viaChat, attempt, current, stopped>>

\* No loginSeen guard. This is the announcement.
ChatPacket ==
  /\ Current
  /\ server' = "connected"
  /\ viaChat' = TRUE
  /\ UNCHANGED <<loginSeen, attempt, current, stopped>>

\* Old socket, wrong attempt, or already closed. markConnected returns false.
StalePacket ==
  /\ \/ stopped
     \/ attempt # current
     \/ server # "connecting"
  /\ UNCHANGED vars

Close ==
  /\ server # "closed"
  /\ server' = "closed"
  /\ stopped' = TRUE
  /\ UNCHANGED <<loginSeen, viaChat, attempt, current>>

\* Not active anymore, so beginConnection may allocate a new id. The old
\* listener keeps its original attempt.
NewAttempt ==
  /\ server = "closed"
  /\ current < MaxAttempt
  /\ current' = current + 1
  /\ server' = "connecting"
  /\ stopped' = FALSE
  /\ viaChat' = FALSE
  /\ loginSeen' = FALSE
  /\ UNCHANGED attempt

Pause == UNCHANGED vars

Next ==
  \/ LoginPacket
  \/ ChatPacket
  \/ StalePacket
  \/ Close
  \/ NewAttempt
  \/ Pause

Spec == Init /\ [][Next]_vars

TypeOK ==
  /\ server \in {"connecting", "connected", "closed"}
  /\ loginSeen \in BOOLEAN
  /\ viaChat \in BOOLEAN
  /\ attempt \in 1..MaxAttempt
  /\ current \in 1..MaxAttempt
  /\ attempt <= current
  /\ stopped \in BOOLEAN

\* The login packet does not flip the server to connected.
LoginDoesNotAnnounce == loginSeen /\ server = "connecting" => ~viaChat

\* Connected was entered by a chat packet on the attempt that is still current.
ConnectedIsCurrentChat ==
  server = "connected" => viaChat /\ ~stopped /\ attempt = current

=============================================================================
