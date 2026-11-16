----------------------------- MODULE DesktopPairing -----------------------------
\* Pairing codes and the desktop origin gate.
\*
\* DesktopConnectionManager.createPairingCode stores owner + expiry and does not
\* cancel a previous code. consumePairingCode removes the entry before it checks
\* the TTL, so a code is single-use even when it is already expired (`expiresAt
\* < now`; the expiry instant itself is still accepted). DesktopApp.trusted
\* forwards a code only from https://mineportal.kr and only when it matches
\* [A-Z0-9]{4,16}. A foreign page or a bad shape never calls pairImmediately, so
\* the backend is not asked to consume it. unpair deletes every outstanding code
\* for that owner and leaves the other owner's codes in place.
\*
\* Time is an explicit Expire step rather than a counter, so the state graph stays
\* finite and small. The one-instant difference between the code clock (`<`) and
\* the device-token clock (`<=`) is covered in DeviceGrant.tla's comment; both
\* clocks reject a grant after it has expired.
\*
\* Device-token rotation is DeviceGrant.tla. The Minecraft access token is not a
\* pairing code; PlaySession.tla is where that token is allowed to exist.

EXTENDS Naturals

Owners == {"u1", "u2"}
Codes == {"c1", "c2"}

VARIABLES
  slot,       \* "empty" | "fresh" | "expired"
  ownerOf,
  spent,      \* removed by consume; a reissue clears this
  origin,     \* "official" | "other"
  presented,  \* a code, "bad", or "none"
  paired,
  granted,
  epoch,      \* bumped by unpair
  codeEpoch

vars == <<slot, ownerOf, spent, origin, presented, paired, granted, epoch, codeEpoch>>

Init ==
  /\ slot = [c \in Codes |-> "empty"]
  /\ ownerOf = [c \in Codes |-> "u1"]
  /\ spent = [c \in Codes |-> FALSE]
  /\ origin = "official"
  /\ presented = "none"
  /\ paired = [o \in Owners |-> FALSE]
  /\ granted = [o \in Owners |-> FALSE]
  /\ epoch = [o \in Owners |-> 0]
  /\ codeEpoch = [c \in Codes |-> 0]

\* A second issue does not erase the other code. One reissue per slot keeps the
\* graph finite; the implementation's codes are random and unbounded.
Issue(o, c) ==
  /\ slot[c] = "empty"
  /\ slot' = [slot EXCEPT ![c] = "fresh"]
  /\ ownerOf' = [ownerOf EXCEPT ![c] = o]
  /\ spent' = [spent EXCEPT ![c] = FALSE]
  /\ codeEpoch' = [codeEpoch EXCEPT ![c] = epoch[o]]
  /\ UNCHANGED <<origin, presented, paired, granted, epoch>>

Expire(c) ==
  /\ slot[c] = "fresh"
  /\ slot' = [slot EXCEPT ![c] = "expired"]
  /\ UNCHANGED <<ownerOf, spent, origin, presented, paired, granted, epoch, codeEpoch>>

Present(org, code) ==
  /\ origin' = org
  /\ presented' = code
  /\ UNCHANGED <<slot, ownerOf, spent, paired, granted, epoch, codeEpoch>>

\* Official origin, well-formed code. Expired and fresh codes are both removed.
\* Only a fresh code pairs the owner.
Consume ==
  /\ origin = "official"
  /\ presented \in Codes
  /\ slot[presented] \in {"fresh", "expired"}
  /\ LET c == presented
         o == ownerOf[c]
     IN /\ slot' = [slot EXCEPT ![c] = "empty"]
        /\ spent' = [spent EXCEPT ![c] = TRUE]
        /\ IF slot[c] = "fresh"
           THEN /\ paired' = [paired EXCEPT ![o] = TRUE]
                /\ granted' = [granted EXCEPT ![o] = TRUE]
           ELSE UNCHANGED <<paired, granted>>
        /\ UNCHANGED <<ownerOf, origin, presented, epoch, codeEpoch>>

Unpair(o) ==
  /\ epoch[o] = 0
  /\ epoch' = [epoch EXCEPT ![o] = 1]
  /\ slot' = [c \in Codes |-> IF ownerOf[c] = o THEN "empty" ELSE slot[c]]
  /\ paired' = [paired EXCEPT ![o] = FALSE]
  /\ UNCHANGED <<ownerOf, spent, origin, presented, granted, codeEpoch>>

Pause == UNCHANGED vars

Next ==
  \/ \E o \in Owners, c \in Codes : Issue(o, c)
  \/ \E c \in Codes : Expire(c)
  \/ \E org \in {"official", "other"}, code \in (Codes \cup {"bad", "none"}) :
       Present(org, code)
  \/ Consume
  \/ \E o \in Owners : Unpair(o)
  \/ Pause

Spec == Init /\ [][Next]_vars /\ WF_vars(Consume)

TypeOK ==
  /\ \A c \in Codes :
       /\ slot[c] \in {"empty", "fresh", "expired"}
       /\ ownerOf[c] \in Owners
       /\ spent[c] \in BOOLEAN
       /\ codeEpoch[c] \in 0..1
  /\ origin \in {"official", "other"}
  /\ presented \in (Codes \cup {"bad", "none"})
  /\ \A o \in Owners :
       /\ paired[o] \in BOOLEAN
       /\ granted[o] \in BOOLEAN
       /\ epoch[o] \in 0..1

\* Consume removes the slot. A second presentation cannot be accepted until Issue
\* stores a new code in that slot, which clears `spent`.
SingleUse ==
  \A c \in Codes : spent[c] => slot[c] = "empty"

\* A live code belongs to the owner's current unpair epoch.
EpochMatchesLiveCodes ==
  \A c \in Codes :
    slot[c] # "empty" => codeEpoch[c] = epoch[ownerOf[c]]

PairingImpliesGrant ==
  \A o \in Owners : paired[o] => granted[o]

\* A fresh code sitting on the official origin is consumed, expires, or the page
\* stops presenting it. A foreign origin is not in the antecedent: that page
\* never calls the consume path.
FreshCodeIsConsumed ==
  /\ (slot["c1"] = "fresh" /\ presented = "c1" /\ origin = "official")
       ~> (slot["c1"] # "fresh" \/ presented # "c1" \/ origin # "official")
  /\ (slot["c2"] = "fresh" /\ presented = "c2" /\ origin = "official")
       ~> (slot["c2"] # "fresh" \/ presented # "c2" \/ origin # "official")

=============================================================================
