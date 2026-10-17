package com.example.dataguard.vpn.engine

import android.content.pm.PackageManager
import com.example.dataguard.data.entity.AttributionConfidence

/**
 * Result of resolving package name, app label, and attribution confidence for network traffic.
 */
data class AppAttribution(
    val packageName: String,
    val appName: String,
    val confidence: AttributionConfidence,
    val isVerifiedUid: Boolean
)

/**
 * Centralized, strict domain-suffix matcher and application attribution engine.
 *
 * Replaces naive substring matching (`domain.contains(...)`) with strict domain-suffix matching
 * (`domain.equals(pattern) || domain.endsWith("." + pattern)`) to prevent false positives
 * (e.g., "dropbox.com" or "linux.com" incorrectly matching "x.com").
 *
 * Rules:
 * 1. UID attribution is used ONLY when verified via PackageManager.
 * 2. If UID is unverified, domain-suffix matching against a curated catalog assigns MEDIUM confidence.
 * 3. If neither matches, attribution confidence is UNKNOWN and no package is guessed.
 */
object AppDomainMatcher {

    data class CuratedApp(
        val packageName: String,
        val appName: String,
        val domainSuffixes: List<String>
    )

    private val CURATED_CATALOG = listOf(
        CuratedApp(
            packageName = "com.google.android.youtube",
            appName = "YouTube",
            domainSuffixes = listOf("youtube.com", "youtu.be", "googlevideo.com", "ytimg.com")
        ),
        CuratedApp(
            packageName = "com.instagram.android",
            appName = "Instagram",
            domainSuffixes = listOf("instagram.com", "cdninstagram.com", "ig.me")
        ),
        CuratedApp(
            packageName = "com.whatsapp",
            appName = "WhatsApp",
            domainSuffixes = listOf("whatsapp.com", "whatsapp.net")
        ),
        CuratedApp(
            packageName = "com.facebook.katana",
            appName = "Facebook",
            domainSuffixes = listOf("facebook.com", "fbcdn.net", "fbsbx.com", "meta.com")
        ),
        CuratedApp(
            packageName = "com.netflix.mediaclient",
            appName = "Netflix",
            domainSuffixes = listOf("netflix.com", "nflxvideo.net", "nflxext.com", "nflximg.net")
        ),
        CuratedApp(
            packageName = "com.spotify.music",
            appName = "Spotify",
            domainSuffixes = listOf("spotify.com", "scdn.co", "spoti.fi")
        ),
        CuratedApp(
            packageName = "com.twitter.android",
            appName = "X (Twitter)",
            domainSuffixes = listOf("x.com", "twitter.com", "twimg.com", "t.co")
        ),
        CuratedApp(
            packageName = "org.telegram.messenger",
            appName = "Telegram",
            domainSuffixes = listOf("telegram.org", "t.me", "telegram.me", "telesco.pe")
        ),
        CuratedApp(
            packageName = "com.zhiliaoapp.musically",
            appName = "TikTok",
            domainSuffixes = listOf("tiktok.com", "byteoversea.com", "ibytedtos.com", "musical.ly")
        ),
        CuratedApp(
            packageName = "com.snapchat.android",
            appName = "Snapchat",
            domainSuffixes = listOf("snapchat.com", "sc-cdn.net")
        ),
        CuratedApp(
            packageName = "com.reddit.frontpage",
            appName = "Reddit",
            domainSuffixes = listOf("reddit.com", "redd.it", "redditmedia.com")
        ),
        CuratedApp(
            packageName = "com.google.android.googlequicksearchbox",
            appName = "Google",
            domainSuffixes = listOf("google.com", "gstatic.com", "googleapis.com", "googleusercontent.com")
        )
    )

    /**
     * Checks if a domain strictly matches a pattern or is a subdomain of the pattern.
     * e.g., "x.com" matches "x.com" and "api.x.com", but DOES NOT match "dropbox.com" or "linux.com".
     */
    fun matchesDomainSuffix(domain: String, pattern: String): Boolean {
        val cleanDomain = domain.trim().lowercase().removePrefix("http://").removePrefix("https://").substringBefore('/')
        val cleanPattern = pattern.trim().lowercase()

        if (cleanDomain == cleanPattern) return true
        if (cleanDomain.endsWith(".$cleanPattern")) return true

        return false
    }

    /**
     * Infers package name from domain using strict domain-suffix matching.
     */
    fun inferPackageForDomain(domain: String): String? {
        val cleanDomain = domain.trim().lowercase()
        if (cleanDomain.isBlank()) return null

        for (app in CURATED_CATALOG) {
            for (suffix in app.domainSuffixes) {
                if (matchesDomainSuffix(cleanDomain, suffix)) {
                    return app.packageName
                }
            }
        }
        return null
    }

    /**
     * Resolves app information for a given UID and domain.
     * Uses UID attribution only when verified via PackageManager.
     * When unverified, uses curated domain-suffix matching or falls back to UNKNOWN without guessing.
     */
    fun resolveAppInfo(
        packageManager: PackageManager?,
        uid: Int,
        domain: String?
    ): AppAttribution {
        // 1. Verified UID attribution
        if (packageManager != null && uid > 0) {
            val packages = try {
                packageManager.getPackagesForUid(uid)
            } catch (_: Exception) {
                null
            }

            if (!packages.isNullOrEmpty()) {
                val pkg = packages[0]
                val appName = try {
                    val info = packageManager.getApplicationInfo(pkg, 0)
                    packageManager.getApplicationLabel(info).toString()
                } catch (_: Exception) {
                    pkg
                }
                return AppAttribution(
                    packageName = pkg,
                    appName = appName,
                    confidence = AttributionConfidence.HIGH,
                    isVerifiedUid = true
                )
            }
        }

        // 2. Curated domain-suffix matching (Medium confidence)
        if (!domain.isNullOrBlank()) {
            val cleanDomain = domain.trim().lowercase()
            for (app in CURATED_CATALOG) {
                for (suffix in app.domainSuffixes) {
                    if (matchesDomainSuffix(cleanDomain, suffix)) {
                        return AppAttribution(
                            packageName = app.packageName,
                            appName = app.appName,
                            confidence = AttributionConfidence.MEDIUM,
                            isVerifiedUid = false
                        )
                    }
                }
            }
        }

        // 3. Unverified - do NOT guess the app
        return AppAttribution(
            packageName = "unknown",
            appName = "Unknown / Web Traffic",
            confidence = AttributionConfidence.UNKNOWN,
            isVerifiedUid = false
        )
    }
}
