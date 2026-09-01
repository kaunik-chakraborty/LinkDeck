package com.linkdeck.android.ui.guide

/**
 * Typed domain model representing a single guide or specification item.
 */
data class GuideItem(
    val id: String,
    val index: Int,
    val title: String,
    val description: String,
    val example: String? = null
)

/**
 * Central repository delivering data-driven content for the Features & Architecture Guide.
 */
object GuideRepository {

    fun getSimpleGuideItems(): List<GuideItem> = listOf(
        GuideItem(
            id = "default_browser",
            index = 1,
            title = "Default Browser Gateway",
            description = "Android routes all web links through the default browser. Setting LinkDeck as default gives you total control when tapping links in WhatsApp, Instagram, Telegram, Slack, or Gmail.",
            example = "Tapping a link won't force you into Chrome. Instead, LinkDeck displays an interactive chooser with your web browsers and native apps."
        ),
        GuideItem(
            id = "tracking_cleaner",
            index = 2,
            title = "Deep Tracking Parameter Cleaner",
            description = "Strips 60+ invasive advertising and analytics tracking keys (Google Ads, Facebook, Instagram igsi/igshid, TikTok, CommerceIQ, UTM, Click IDs) in 0 milliseconds before links open.",
            example = "Dirty: https://youtu.be/xyz?si=987654&feature=share\nClean: https://youtu.be/xyz\n(Google and Meta can no longer trace which user forwarded the link!)"
        ),
        GuideItem(
            id = "redirect_unfurler",
            index = 3,
            title = "Short Link Redirect Unfurler",
            description = "Probes shortened links (bit.ly, t.co, tinyurl, amzn.to) safely on-device so you see the real, unshortened destination before your browser loads it.",
            example = "bit.ly/3xY9k\n→ Unfurled to: amazon.com/dp/B08N5WRWNW\n(No surprise phishing redirects or unexpected hops!)"
        ),
        GuideItem(
            id = "phishing_alerts",
            index = 4,
            title = "Lookalike & Phishing Protection",
            description = "Detects fake lookalike websites using international Cyrillic/Greek letters (Punycode IDN), deceptive embedded credentials, and unencrypted cleartext HTTP.",
            example = "https://xn--pple-43d.com looks like 'apple.com' but uses a Cyrillic 'а'. LinkDeck flags it with a high-severity security alert."
        ),
        GuideItem(
            id = "ssl_inspector",
            index = 5,
            title = "SSL / TLS Certificate Inspector",
            description = "Inspects connection encryption, Issuer Authority (Let's Encrypt, DigiCert, Cloudflare), expiration dates, and provides 1-tap SHA-256 fingerprint copying.",
            example = "Warns you if a destination uses unencrypted plain HTTP where credentials can be intercepted over public Wi-Fi."
        ),
        GuideItem(
            id = "cname_cloaking",
            index = 6,
            title = "DNS CNAME Cloaking Detector",
            description = "Unmasks third-party advertising trackers disguised as first-party subdomains (e.g. metrics.store.com) by resolving DNS over HTTPS against 60+ adtech signatures."
        ),
        GuideItem(
            id = "share_clean",
            index = 7,
            title = "Share via LinkDeck (Clean & Forward)",
            description = "Select LinkDeck when sharing a link from any app. Strips tracking parameters instantly and allows 1-tap forwarding to WhatsApp, Signal, or clipboard, with an optional toggle to keep original parameters if desired."
        ),
        GuideItem(
            id = "routing_rules",
            index = 8,
            title = "Custom Routing Rules & Preferred Apps",
            description = "Define automatic routing rules for domains (reddit.com → Reddit app) or specific paths (youtube.com/shorts/* → NewPipe), and save per-domain 'Always' app preferences."
        ),
        GuideItem(
            id = "widgets",
            index = 9,
            title = "Interactive Home Screen Widgets",
            description = "Includes 2 dedicated home screen widgets:\n• Multi-Link & Paste Widget: 1-tap clipboard paste & open, paste & save, and 4 customizable quick links.\n• Status Widget: Live protection status, 1-tap test link shortcut, and dashboard access."
        ),
        GuideItem(
            id = "appearance",
            index = 10,
            title = "Custom Typography & OLED Dark Theme",
            description = "Personalize your experience with 5 curated typography options (Satoshi, Outfit, Inter, JetBrains Mono, Space Grotesk), Material You dynamic colors, and OLED Pure Dark mode."
        )
    )

    fun getTechnicalGuideItems(): List<GuideItem> = listOf(
        GuideItem(
            id = "intent_sanitizer",
            index = 1,
            title = "Intent Sanitization & Scheme Whitelisting",
            description = "• Strict HTTP/HTTPS scheme whitelist in IntentSanitizer. Drops javascript:, content:, file:, and intent: URI schemes to eliminate arbitrary code execution.\n• Strips incoming intent extras and clip data to neutralize Android intent injection attacks.\n• Enforces a 4,096 character upper bound to prevent pathological memory denial-of-service."
        ),
        GuideItem(
            id = "tracking_engine",
            index = 2,
            title = "Prefix-Driven Parameter Cleaning Engine",
            description = "• Linear O(N) query tokenizer preserving parameter order, URI fragments, and percent-encoding.\n• Strips prefix sets (utm_, cq_, gad_, thg_, sc_, pk_, mat_, ig_, fb_, tw_) and exact click IDs (gbraid, wbraid, gclid, gclsrc, fbclid, igsi, igsh, igshid, mibextid, ttclid, li_fat_id, msclkid, yclid, zanpid, s_kwcid).\n• Prunes dangling empty marketing tokens without corrupting functional API query parameters."
        ),
        GuideItem(
            id = "tls_inspector",
            index = 3,
            title = "Direct SSLSocket X.509 Cryptographic TLS Inspector",
            description = "• Performs direct TLS socket handshake without HTTP payload overhead, preserving 0ms launch speed.\n• Extracts TLS protocol version (TLSv1.3/TLSv1.2), negotiated cipher suite, Issuer Authority (CN/O), Subject Alternative Names (SANs), RSA/EC key length, and SHA-256 fingerprint.\n• Fail-safe fallback captures peer certificate chains during SSLHandshakeException."
        ),
        GuideItem(
            id = "cname_cloaking_doh",
            index = 4,
            title = "RFC 8484 DNS-over-HTTPS CNAME Engine",
            description = "• Queries DNS-over-HTTPS (Quad9 non-logging Swiss Foundation endpoint) with a strict 2.5s socket timeout.\n• Validates canonical CNAME records against an asset database of 60+ adtech signatures (Criteo, Branch, Adobe Audience Manager, Demdex, AppsFlyer, Adjust, Kochava, Segment, Eloqua, LiveRamp, Trade Desk)."
        ),
        GuideItem(
            id = "ssrf_filter",
            index = 5,
            title = "SSRF & Private Network Subnet Filter",
            description = "• Evaluates every resolved IP address before socket creation in RedirectResolver.\n• Blocks loopback (127.0.0.0/8), RFC 1918 private subnets (10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16), CGNAT (100.64.0.0/10), Link-local (169.254.0.0/16), IPv6 Unique Local (fc00::/7), and IPv4-mapped IPv6."
        ),
        GuideItem(
            id = "binder_caching",
            index = 6,
            title = "Low-Latency Binder IPC Architecture",
            description = "• Implements in-memory thread-safe 60-second caching for installed browser discovery in AppResolver, eliminating redundant IPC Binder calls to PackageManagerService.\n• Reduces link chooser launch latency from ~120ms to < 1ms."
        ),
        GuideItem(
            id = "widget_remoteviews",
            index = 7,
            title = "AppWidget RemoteViews & Clipboard IPC",
            description = "• Multi-Link and Status widgets dispatch immutable PendingIntent actions to WidgetActionActivity.\n• Ingests clipboard data, sanitizes URIs, and routes through ChooserActivity without foreground UI flicker."
        ),
        GuideItem(
            id = "rule_evaluator",
            index = 8,
            title = "Deterministic Rule & Path Pattern Evaluator",
            description = "• RoutingRuleEvaluator executes exact host matching and hierarchical glob/wildcard path pattern matching (e.g. /shorts/*, /r/*).\n• Enforces strict priority resolution: Rule matching precedes 'Always' domain preferences, which precede manual chooser selection."
        ),
        GuideItem(
            id = "zero_telemetry",
            index = 9,
            title = "Zero Telemetry & Local Persistence",
            description = "• Zero tracking SDKs, zero crash reporting services, zero cloud accounts, and zero background data transmission.\n• All routing rules, quick link deck slots, and preferences are stored in app-private SharedPreferences with immediate 1-tap wipe support."
        )
    )
}
