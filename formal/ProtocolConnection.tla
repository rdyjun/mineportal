------------------------ MODULE ProtocolConnection ------------------------
EXTENDS Naturals

Phases == {"Idle", "Resolving", "Observed", "Connecting", "Negotiating", "Active", "Closed"}
Routes == {"None", "Backend", "Desktop"}
Endpoints == {"Unresolved", "PublicPinned", "LocalResolved"}
Protocols == {"Unobserved", "Known", "Missing", "Unknown"}
Translations == {"Unselected", "Direct", "Translated", "Probe", "Rejected"}
Mods == {"Unseen", "None", "Optional", "Required", "Satisfied"}

VARIABLES phase, route, endpoint, protocol, translation, mods,
          authorized, sessionLive, sentMessage

vars == <<phase, route, endpoint, protocol, translation, mods,
          authorized, sessionLive, sentMessage>>

Init ==
    /\ phase = "Idle"
    /\ route = "None"
    /\ endpoint = "Unresolved"
    /\ protocol = "Unobserved"
    /\ translation = "Unselected"
    /\ mods = "Unseen"
    /\ authorized = FALSE
    /\ sessionLive = FALSE
    /\ sentMessage = FALSE

Authorize ==
    /\ phase = "Idle"
    /\ authorized' = TRUE
    /\ phase' = "Resolving"
    /\ route' \in {"Backend", "Desktop"}
    /\ UNCHANGED <<endpoint, protocol, translation, mods, sessionLive, sentMessage>>

Resolve ==
    /\ phase = "Resolving"
    /\ endpoint' = IF route = "Backend" THEN "PublicPinned" ELSE "LocalResolved"
    /\ phase' = "Observed"
    /\ protocol' \in {"Known", "Missing", "Unknown"}
    /\ UNCHANGED <<route, translation, mods, authorized, sessionLive, sentMessage>>

SelectKnownProtocol ==
    /\ phase = "Observed"
    /\ protocol = "Known"
    /\ translation' \in {"Direct", "Translated"}
    /\ phase' = "Connecting"
    /\ UNCHANGED <<route, endpoint, protocol, mods, authorized, sessionLive, sentMessage>>

ProbeMissingProtocol ==
    /\ phase = "Observed"
    /\ protocol = "Missing"
    /\ translation' = "Probe"
    /\ phase' = "Connecting"
    /\ UNCHANGED <<route, endpoint, protocol, mods, authorized, sessionLive, sentMessage>>

RejectUnknownProtocol ==
    /\ phase = "Observed"
    /\ protocol = "Unknown"
    /\ translation' = "Rejected"
    /\ phase' = "Closed"
    /\ authorized' = FALSE
    /\ UNCHANGED <<route, endpoint, protocol, mods, sessionLive, sentMessage>>

BeginNegotiation ==
    /\ phase = "Connecting"
    /\ sessionLive' = TRUE
    /\ phase' = "Negotiating"
    /\ mods' \in {"None", "Optional", "Required"}
    /\ UNCHANGED <<route, endpoint, protocol, translation, authorized, sentMessage>>

SatisfyModHandshake ==
    /\ phase = "Negotiating"
    /\ mods = "Required"
    /\ mods' = "Satisfied"
    /\ UNCHANGED <<phase, route, endpoint, protocol, translation, authorized, sessionLive, sentMessage>>

Activate ==
    /\ phase = "Negotiating"
    /\ mods \in {"None", "Optional", "Satisfied"}
    /\ phase' = "Active"
    /\ UNCHANGED <<route, endpoint, protocol, translation, mods, authorized, sessionLive, sentMessage>>

SendMessage ==
    /\ phase = "Active"
    /\ sentMessage' = TRUE
    /\ UNCHANGED <<phase, route, endpoint, protocol, translation, mods, authorized, sessionLive>>

Close ==
    /\ phase \in {"Connecting", "Negotiating", "Active"}
    /\ phase' = "Closed"
    /\ authorized' = FALSE
    /\ sessionLive' = FALSE
    /\ sentMessage' = FALSE
    /\ UNCHANGED <<route, endpoint, protocol, translation, mods>>

ClosedStutter ==
    /\ phase = "Closed"
    /\ UNCHANGED vars

Next == Authorize \/ Resolve \/ SelectKnownProtocol \/ ProbeMissingProtocol
        \/ RejectUnknownProtocol \/ BeginNegotiation \/ SatisfyModHandshake
        \/ Activate \/ SendMessage \/ Close \/ ClosedStutter

Spec == Init /\ [][Next]_vars

TypeOK ==
    /\ phase \in Phases
    /\ route \in Routes
    /\ endpoint \in Endpoints
    /\ protocol \in Protocols
    /\ translation \in Translations
    /\ mods \in Mods
    /\ authorized \in BOOLEAN
    /\ sessionLive \in BOOLEAN
    /\ sentMessage \in BOOLEAN

ActiveIsAuthorized == phase = "Active" => authorized
ActiveHasPinnedEndpoint == phase = "Active" => endpoint \in {"PublicPinned", "LocalResolved"}
BackendNeverUsesUnvalidatedAddress == route = "Backend" /\ endpoint # "Unresolved" => endpoint = "PublicPinned"
UnknownProtocolNeverConnects == protocol = "Unknown" => phase \notin {"Connecting", "Negotiating", "Active"}
RequiredModsNeverBypassed == phase = "Active" => mods # "Required"
MessagesOnlyWhileActive == sentMessage => phase = "Active"
ClosedReleasesSensitiveState == phase = "Closed" => ~authorized /\ ~sessionLive

=============================================================================
