# Desktop restoration security review

Reviewed PR #31 on 2026-11-02 before merging. Scope: the desktop/WebView boundary,
pairing and authenticated desktop events, local routing and transfers, download
URLs, package workflow permissions, newly introduced runtime dependencies, and
credential patterns in added lines. This is a focused source review, not a
penetration test or a complete audit of all existing dependencies.

## Finding fixed before merge

**Medium: unauthorized desktop disconnect events could remove another connection's
routing state.** `DesktopWsHandler.handleDisconnected` removed a route before
checking that the socket owned the requested server. A socket knowing a server ID
and its connection attempt could affect routing even when unauthenticated or paired
to another owner. Replaced sockets also retained owner attributes until closure.

The handler now verifies both current registered socket identity and server
ownership before accepting state/chat events or modifying routing. Five regression
tests cover unauthenticated sockets, other owners, replaced sockets (including
injected connected/chat events), legitimate disconnects, and missing server IDs.
All 36 server tests, the server build and formatting checks passed locally.

## Other checks

- Remote JavaScript receives no Java object/native execution bridge. Pairing status
  events require the exact official HTTPS origin and a bounded alphanumeric code;
  the backend still requires a single-use, expiring pairing grant. Presentation
  detection through User-Agent is not used as authentication.
- Server ownership is resolved from the authenticated account. Desktop-only
  requests reject unavailable local apps rather than fall back to the public relay.
  Public probes still resolve/validate public addresses and pin resolved bytes;
  desktop connections intentionally support user-selected LAN destinations.
- Transfers have bounded host/port/count checks, and canceled or stale sessions
  cannot initiate a later connection. Only the authenticated current desktop socket
  can publish server state or chat for its own account.
- Download URLs use fixed GitHub release asset names. The new package workflow has
  read-only repository permissions, no deployment credentials and no PR-head input
  interpolated into commands. The upload action is now pinned to its commit, like
  checkout and setup-java.
- Added-line scans found no private key, GitHub token, AWS access key or JWT patterns.
  Validation records omit credentials, verification codes and raw chat. Pattern
  scans do not prove that all possible secret formats are absent.
- OSV queries for `org.openjfx:javafx-base`, `javafx-graphics`, `javafx-controls`,
  `javafx-media` and `javafx-web` version `21.0.13` returned no advisories at review
  time. The upstream [October 2026 patch announcement](https://gluonhq.com/news/2026-10-20-oct-2026-critical-patch-update/)
  includes this release. This is not a complete transitive dependency assessment.
- GitHub Dependabot and secret-scanning APIs reported those features disabled on
  this repository. No settings or authentication scopes were changed.

## Remaining release checks

Native GUI/OAuth/pairing interaction checks still await computer-control permission
approval. Signing/notarization, publishing matching installer release assets and
download verification remain outstanding. CI launcher checks use headless mode
and do not establish native GUI security or full compatibility. Merging main
triggers the repository's existing web deployment; it does not publish installers.

No remaining merge-blocking issue was identified within this review's scope after
the ownership fix. Release validation remains tracked in `desktop-validation.md`.

## Native GUI follow-up review (PR #34)

External-page recovery controls navigate the existing WebEngine history, reload
the current document or load the fixed official HTTPS site. They show only the
parsed hostname, never OAuth query parameters, and introduce no native JavaScript
bridge or relaxed pairing origin checks. The frontend now closes the pairing modal
only after the authenticated account event reports a linked desktop, and allows
canceling an active login attempt via the same owner-checked disconnect endpoint.
No new authorization, network destinations or persistent credentials are introduced.
Focused regression checks execute the production pairing and slot button handlers.
No additional merge-blocking security finding was identified in these changes.

## Desktop browser login handoff (2026-11-02, local follow-up)

Microsoft password-page regression is reported inside JavaFX WebView; the exact provider failure has not been reproduced. New desktop shells delegate OAuth to the system browser, consistent with Microsoft macOS authentication guidance: https://learn.microsoft.com/en-us/entra/msal/objc/customize-webviews . This is a change of authentication transport, not evidence that the account password is invalid.

- Native status handling remains restricted to the exact official HTTPS origin. A fixed browser destination is constructed from a UUID-shaped attempt id; no arbitrary URL or Java bridge is accepted. Older shells retain their existing web path.
- A separate 244-bit random proof remains only in initiating page memory and POST bodies. It is absent from browser URLs, persistence, completion HTML and logs. Claims require both proof and initiating sid, expire after ten minutes, and are consumed once. New attempts invalidate old attempts from that app session; the map is bounded.
- Browser authorization requires an explicit confirmation with a one-use nonce bound to its cookie session, before the normal OAuth state flow. Confirmation pages disable framing, caching and referrers; form targets allow only self and fixed Microsoft login hosts (required for the authorization redirect).
- The existing OAuth callback still rotates sid. Handoff delivers that sid only through the existing HttpOnly/Secure/SameSite cookie policy, never response JSON. Account/Minecraft token exchange and owner checks are unchanged. No Minecraft connection is initiated by these endpoints.
- Seven new backend regressions cover proof/session isolation, explicit browser confirmation, expiration, duplicate completion/claim, supersession, JSON deserialization, HTTP cookie delivery and confirmation headers. Three frontend regressions cover successful handoff, failure without embedded fallback, and ordinary web navigation.
- Remaining: real system-browser OAuth and return to the revised Mac app must be validated. App restart persistence remains a separate unresolved issue. No public installer release or new live Minecraft checks have been performed.
