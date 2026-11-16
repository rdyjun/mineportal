----------------------------- MODULE ServerAttempt -----------------------------
\* ServerConfig's per-server attempt machine, for two servers owned by one user.
\*
\* beginConnection is a no-op while the server is already active (CONNECTING or
\* CONNECTED) and otherwise allocates a new attempt id.
\* markConnected succeeds only for the current attempt and only from CONNECTING,
\* so a late "connected" event cannot revive a failed, closed, or superseded
\* attempt, and cannot mark a different server.
\* markFailed requires the current attempt and an active state.
\* markClosed(attempt) ignores a stale attempt id. markClosed() with no id closes
\* whatever is not already CLOSED, including IDLE, matching the Java method.
\*
\* The two servers share nothing except the spec. Closing or failing one does
\* not change the other. That is the multi-server session rule on the backend:
\* each server id has its own attempt.

EXTENDS Naturals

Servers == {"a", "b"}
MaxAttempt == 2

VARIABLES state, attempt, armed

vars == <<state, attempt, armed>>

Init ==
  /\ state = [s \in Servers |-> "idle"]
  /\ attempt = [s \in Servers |-> 0]
  /\ armed = [s \in Servers |-> 0]

Begin(s) ==
  /\ IF state[s] \in {"connecting", "connected"}
     THEN UNCHANGED <<state, attempt, armed>>
     ELSE /\ attempt[s] < MaxAttempt
          /\ state' = [state EXCEPT ![s] = "connecting"]
          /\ attempt' = [attempt EXCEPT ![s] = attempt[s] + 1]
          /\ UNCHANGED armed

\* id is the attempt the event claims. Only the current id, and only while
\* CONNECTING, moves the server to connected and records that id.
MarkConnected(s, id) ==
  /\ id \in 1..MaxAttempt
  /\ IF id = attempt[s] /\ state[s] = "connecting"
     THEN /\ state' = [state EXCEPT ![s] = "connected"]
          /\ armed' = [armed EXCEPT ![s] = id]
          /\ UNCHANGED attempt
     ELSE UNCHANGED vars

MarkFailed(s, id) ==
  /\ id \in 1..MaxAttempt
  /\ IF id = attempt[s] /\ state[s] \in {"connecting", "connected"}
     THEN /\ state' = [state EXCEPT ![s] = "failed"]
          /\ UNCHANGED <<attempt, armed>>
     ELSE UNCHANGED vars

MarkClosed(s, id) ==
  /\ id \in 0..MaxAttempt
  /\ IF id = attempt[s] /\ state[s] # "closed"
     THEN /\ state' = [state EXCEPT ![s] = "closed"]
          /\ UNCHANGED <<attempt, armed>>
     ELSE UNCHANGED vars

MarkClosedBare(s) ==
  /\ IF state[s] # "closed"
     THEN /\ state' = [state EXCEPT ![s] = "closed"]
          /\ UNCHANGED <<attempt, armed>>
     ELSE UNCHANGED vars

Pause == UNCHANGED vars

Succeed(s) ==
  state[s] = "connecting" /\ MarkConnected(s, attempt[s])

Next ==
  \/ \E s \in Servers : Begin(s)
  \/ \E s \in Servers, id \in 1..MaxAttempt : MarkConnected(s, id)
  \/ \E s \in Servers, id \in 1..MaxAttempt : MarkFailed(s, id)
  \/ \E s \in Servers, id \in 0..MaxAttempt : MarkClosed(s, id)
  \/ \E s \in Servers : MarkClosedBare(s)
  \/ Pause

Spec == Init /\ [][Next]_vars /\ \A s \in Servers : WF_vars(Succeed(s))

TypeOK ==
  \A s \in Servers :
    /\ state[s] \in {"idle", "connecting", "connected", "failed", "closed"}
    /\ attempt[s] \in 0..MaxAttempt
    /\ armed[s] \in 0..MaxAttempt

\* "connected" was entered from CONNECTING for this same attempt, not from a
\* stale event and not from the other server's event.
ConnectedIsCurrentAttempt ==
  \A s \in Servers :
    state[s] = "connected" => armed[s] = attempt[s] /\ attempt[s] > 0

ActiveHasAttempt ==
  \A s \in Servers :
    state[s] \in {"connecting", "connected"} => attempt[s] > 0

\* A connecting server is not left there forever if the matching connected
\* event stays enabled. Failure and close also leave the state, which is allowed.
ConnectingResolves(s) ==
  state[s] = "connecting" ~> state[s] # "connecting"

ConnectingResolvesAll ==
  /\ ConnectingResolves("a")
  /\ ConnectingResolves("b")

=============================================================================
