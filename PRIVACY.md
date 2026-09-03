# Privacy Policy for LinkDeck

**Effective Date:** September 2, 2026  
**Last Updated:** September 2, 2026

LinkDeck ("the Application", "we", "us") is an open-source, on-device Android link routing and privacy companion developed by Kaunik Chakraborty. 

This Privacy Policy describes our strict privacy-first architecture, disclosing exactly how the Application interacts with links, device services, and the internet.

---

## 1. Core Principle: Zero Personal Data Collection

LinkDeck is architected from the ground up to operate with zero telemetry:

* **Zero Personal Information:** We do not collect, request, record, or transmit names, email addresses, phone numbers, contacts, device identifiers (e.g., IMEI, Android ID, Advertising ID), or precise/coarse location.
* **No Account Required:** The Application does not contain any user registration, authentication, login, or cloud accounts.
* **No Third-Party Analytics or Advertising SDKs:** The Application contains **zero** tracking libraries, zero marketing SDKs, and zero crash reporting telemetry (e.g., no Google Analytics, no Firebase, no Facebook SDK, no AppsFlyer).
* **No Browsing History Stored:** LinkDeck does not record, log, or persist the links, URLs, or domains that you tap, open, or inspect. Once an intent is routed or dismissed, the in-memory link data is discarded.

---

## 2. On-Device Link Processing & Sanitization

All core link handling and privacy functions execute **100% locally on your device**:

* **Tracking Parameter Stripping:** When you tap or share a URL, LinkDeck's `TrackingParameterCleaner` strips marketing and attribution tokens (such as `utm_*`, `fbclid`, `gclid`, `gbraid`, `wbraid`, `msclkid`, `igsi`, `igsh`, `igshid`, `ig_rid`, `ig_mid`, `twclid`, `tw_source`, `mc_*`, etc.) purely using local in-memory URI parsing. Your links are never sent to any remote server for cleaning.
* **On-Device De-AMPing:** When you tap or share an Accelerated Mobile Pages (AMP) link, LinkDeck's `DeAmpEngine` parses and unwraps Google AMP viewers, AMP Project CDN caches, Cloudflare/Bing AMP caches, and publisher AMP subdomains locally into direct canonical publisher URLs with zero network calls and zero telemetry.
* **Quick Settings Clipboard Sanitization:** When you tap the Clean Clipboard Quick Settings tile, LinkDeck accesses your primary clipboard clip locally to inspect and sanitize the link. Clipboard data is never stored to disk, never logged, and never transmitted over the network.
* **Application Resolution via Binder IPC:** To show you which browsers and native applications can open a link, LinkDeck queries Android's system `PackageManager` through local Binder Inter-Process Communication (IPC).
* **Scoped Package Visibility:** LinkDeck does **not** request the high-privilege `QUERY_ALL_PACKAGES` permission. It uses scoped Android `<queries>` declarations strictly restricted to web handlers (`http`/`https` intent filters) and text share targets (`ACTION_SEND`).

---

## 3. Local Storage (SharedPreferences)

The Application saves only user-defined configuration locally on your device using Android's private `SharedPreferences` (`MODE_PRIVATE`):

1. **Routing Rules:** Custom website patterns and package names assigned by you (e.g., routing `youtube.com/shorts/*` to your preferred app).
2. **Saved App Preferences:** Remembered default browser/app selections that you explicitly choose to save via the "Always" action.
3. **Pinned Share Targets:** Package and component names of apps you choose to pin to the top of the share sheet.
4. **App Settings:** Boolean configuration toggles (e.g., Dark/Light theme, toggle tracking protection, toggle redirect unfurling, toggle threat heuristics).

**None of this configuration data contains personal information or browsing history, and none of it is ever backed up to or synchronized with external servers.**

---

## 4. Network Communications & On-Demand Security Features

LinkDeck requires the `android.permission.INTERNET` permission exclusively to perform user-initiated security and verification tasks. The Application never transmits personal data over the network:

### A. Short-Link Redirect Unfurling
* **When active:** Only when the user taps a known link-shortener domain (e.g., `bit.ly`, `t.co`, `tinyurl.com`) and when the redirect unfurling feature is enabled in Settings.
* **How it works:** LinkDeck executes isolated HTTP `HEAD` or lightweight `GET` requests directly from your device to follow redirect headers (up to a strict limit of 8 hops) to discover the actual destination URL before launching it.
* **Privacy protections:** Cookies are explicitly disabled, response bodies are not parsed or stored, and requests are protected by a Server-Side Request Forgery (SSRF) filter that blocks connections to private, loopback, or internal IP address ranges (RFC 1918 / RFC 4193).

### B. DNS CNAME Cloaking Detection
* **When active:** When the user inspects a link and requests privacy analysis.
* **How it works:** LinkDeck performs an on-demand DNS-over-HTTPS (DoH) query using RFC 8484 wire format to a privacy-preserving DNS resolver (`dns.quad9.net`) to check if a subdomain redirects to known third-party tracking and analytics networks.
* **Privacy protections:** Queries are sent over encrypted HTTPS directly to Quad9 (a non-profit, privacy-focused DoH service that does not log user IPs).

### C. Cryptographic TLS Certificate Inspection
* **When active:** When the user opens the Link Inspector diagnostics sheet.
* **How it works:** LinkDeck opens a direct Java `SSLSocket` to port 443 of the target host to retrieve and display the public X.509 cryptographic certificate chain (issuer CA, cipher suites, validity dates, and SHA-256 fingerprint). No user data is sent during this handshake.

---

## 5. System Clipboard Interaction

* **Read Access:** LinkDeck accesses the clipboard **only** when you explicitly press the "Paste" action in the "Test a link" tool or tap a clipboard action widget. The Application **never** monitors the clipboard in the background or inspects clipboard contents without direct user interaction.
* **Write Access:** The Application writes to the clipboard **only** when you explicitly tap "Copy Link" or "Copy to Clipboard".

---

## 6. Target Audience & Children's Privacy

* LinkDeck is a general productivity and security utility intended for users aged **13 and older** (Target Audience: 18+ and 13–17).
* The Application is not directed toward or designed for children under the age of 13.
* LinkDeck does not knowingly collect, solicit, or maintain personal information from children under 13.

---

## 7. Open Source Verification

LinkDeck is distributed under the **Apache License 2.0**. Our entire source code, build scripts, and architecture are open and verifiable by the community:

* **Source Code Repository:** [https://github.com/kaunik-chakraborty/LinkDeck](https://github.com/kaunik-chakraborty/LinkDeck)

---

## 8. Changes to This Privacy Policy

We may update this Privacy Policy from time to time to reflect application improvements or regulatory changes. Any updates will be committed directly to our public repository with an updated "Last Updated" date.

---

## 9. Contact Us

If you have questions, feedback, or privacy concerns regarding LinkDeck, please reach out via:

* **Developer:** Kaunik Chakraborty
* **GitHub Issues:** [https://github.com/kaunik-chakraborty/LinkDeck/issues](https://github.com/kaunik-chakraborty/LinkDeck/issues)
* **Email:** contact.kaunik@gmail.com
