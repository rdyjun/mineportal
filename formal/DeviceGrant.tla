------------------------------ MODULE DeviceGrant ------------------------------
\* Device token issued by DesktopConnectionManager.issueDeviceToken and checked
\* by ownerForDeviceToken.
\*
\* A new token replaces the previous id for that owner. Resume accepts only the
\* current id. unpair leaves the id in place but marks it dead, so the stored
\* value no longer resumes; the next issue uses a new id. Expiry (`expiresAt <=
\* now`, so the expiry instant is already dead) removes the grant. Pairing-code
\* expiry is the other comparison (`expiresAt < now`) and is modeled as the
\* Expire step in DesktopPairing.tla. Neither grant is a Minecraft access token.

EXTENDS Naturals

MaxGen == 2

VARIABLES gen, previous, live, expired, held, accepted

vars == <<gen, previous, live, expired, held, accepted>>

Init ==
  /\ gen = 0
  /\ previous = 0
  /\ live = FALSE
  /\ expired = FALSE
  /\ held = 0
  /\ accepted = FALSE

\* Successful consume of a pairing code. Rotates the id and clears expiry.
\* The new id is not accepted until Resume sees it.
Issue ==
  /\ gen < MaxGen
  /\ previous' = gen
  /\ gen' = gen + 1
  /\ live' = TRUE
  /\ expired' = FALSE
  /\ accepted' = FALSE
  /\ UNCHANGED held

\* expiresAt <= now. The grant is deleted and resume fails.
Expire ==
  /\ live
  /\ ~expired
  /\ live' = FALSE
  /\ expired' = TRUE
  /\ UNCHANGED <<gen, previous, held, accepted>>

\* The desktop process presents whatever id it stored.
Hold(id) ==
  /\ held' = id
  /\ UNCHANGED <<gen, previous, live, expired, accepted>>

Resume ==
  /\ live
  /\ ~expired
  /\ held = gen
  /\ held # 0
  /\ accepted' = TRUE
  /\ UNCHANGED <<gen, previous, live, expired, held>>

Unpair ==
  /\ live \/ accepted
  /\ live' = FALSE
  /\ accepted' = FALSE
  /\ UNCHANGED <<gen, previous, expired, held>>

Pause == UNCHANGED vars

Next ==
  \/ Issue
  \/ Expire
  \/ \E id \in 0..MaxGen : Hold(id)
  \/ Resume
  \/ Unpair
  \/ Pause

Spec == Init /\ [][Next]_vars /\ WF_vars(Resume)

TypeOK ==
  /\ gen \in 0..MaxGen
  /\ previous \in 0..MaxGen
  /\ live \in BOOLEAN
  /\ expired \in BOOLEAN
  /\ held \in 0..MaxGen
  /\ accepted \in BOOLEAN

\* The id resume checks is never the id that was just replaced.
CurrentTokenIsNew == live => gen # previous /\ gen > 0

\* Expiry and unpair both clear the live bit. A dead grant does not stay current.
DeadGrantIsNotLive == expired => ~live

\* Resume only succeeds after some issue has created an id.
AcceptedMeansIssued == accepted => gen > 0

\* A presented current token that is still live is eventually accepted, or the
\* grant is expired, replaced, or unpaired first.
CurrentTokenResumes ==
  (live /\ ~expired /\ held = gen /\ ~accepted)
    ~> (accepted \/ ~live \/ expired \/ held # gen)

=============================================================================
