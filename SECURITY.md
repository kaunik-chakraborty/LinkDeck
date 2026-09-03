# Security Policy

## Supported Versions

| Version | Supported |
| ------- | --------- |
| 1.3.x   | Yes       |
| 1.2.x   | Yes       |
| 1.1.x   | Yes       |
| 1.0.x   | Yes       |

---

## Security Model & Guarantees

LinkDeck operates as a local-first link proxy on Android. Its security design enforces the following boundaries:

1. **Strict Scheme Whitelisting**:
   - Only `http` and `https` schemes are accepted by `IntentSanitizer`.
   - Dangerous schemes (`intent:`, `javascript:`, `file:`, `content:`, `data:`, `market:`, `tel:`, `mailto:`, etc.) are rejected immediately.
2. **SSRF & Private Network Protection**:
   - Every intermediate and final redirect destination resolved by `RedirectResolver` is validated via `NetworkSafetyChecker`.
   - Destination addresses matching loopback (`127.0.0.0/8`, `::1`), private ranges (`10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`), link-local (`169.254.0.0/16`, `fe80::/10`), unique local (`fc00::/7`), carrier-grade NAT (`100.64.0.0/10`), multicast, and IPv4-mapped IPv6 are blocked fail-closed before any HTTP request is dispatched.
   - Non-standard/dangerous ports (e.g. `22`, `25`, `8080`) are blocked fail-closed.
   - Multi-homed DNS responses containing any private address fail-closed immediately.
3. **No Unsafe Execution**:
   - LinkDeck contains no `WebView`, JavaScript evaluation engine, or HTML renderer.
   - Redirect probing utilizes bounded `HEAD`/`GET` protocol handshakes with strict timeouts (5s per hop, 15s total) and bounded max hops (7).
4. **Presentation-Level Sensitive Data Redaction**:
   - `UrlRedactor` masks sensitive query tokens (`token`, `code`, `password`, `session`, `auth`, `key`, `secret`, `jwt`, `access_token`, etc.) to `[redacted]` before displaying URLs in the Link Inspector UI.
   - Redaction is strictly isolated to the UI presentation layer; the original sanitized link is preserved unmodified for application launching.
5. **Package Visibility & Isolation**:
   - LinkDeck queries only standard web-handling packages via `<queries>` declarations in `AndroidManifest.xml`. It does NOT request `QUERY_ALL_PACKAGES`.
   - Runtime validation checks target package enablement, visibility, and intent-handling capability before launching. LinkDeck excludes itself from candidate lists to prevent recursive intent loops.
6. **On-Demand TLS Inspection & On-Device Threat Detection**:
   - `LinkThreatAnalyzer` evaluates link syntax locally for IDN Punycode homoglyph spoofing (`xn--...`), deceptive userinfo credentials, raw IP destinations, and cleartext HTTP without transmitting browsing data to third-party reputation APIs.
   - `TlsCertificateInspector` executes on-demand via direct cryptographic socket handshakes without sending HTTP request payloads, parsing X.509 chains, cipher suites, validity windows, and SHA-256 fingerprints.
7. **Offline On-Device De-AMPing**:
   - `DeAmpEngine` executes completely offline without network sockets, unrolling AMP wrappers into canonical publisher URLs using standard RFC 3986 URI parsing.
   - Output URLs are re-sanitized through `IntentSanitizer` before dispatching to system package resolvers, preventing intent or loopback SSRF redirection.

---

## Reporting a Vulnerability

If you discover a security vulnerability in LinkDeck, please report it responsibly:

- Please report security vulnerabilities via **GitHub Security Advisories** on the repository page.
- Please include a detailed description of the vulnerability, reproduction steps, and proof of concept.
- Maintainers will review reports promptly and publish patches in subsequent release builds.
