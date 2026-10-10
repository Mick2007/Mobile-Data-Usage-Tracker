package com.example.dataguard.vpn.engine

import java.net.IDN
import java.util.Locale

object DomainNormalizer {

    /**
     * Normalizes domains into canonical form:
     * - Strips leading/trailing whitespaces and protocols (http://, https://)
     * - Converts to lowercase using US locale
     * - Converts Internationalized Domain Names (IDN) with non-ASCII unicode to Punycode
     * - Strips trailing dots (e.g. "youtube.com." -> "youtube.com")
     * - Strips wildcard prefix if present (e.g. "*.youtube.com" -> "youtube.com")
     * - Preserves base domain without corrupting ports or paths
     */
    fun normalize(input: String): String {
        var domain = input.trim()

        // Strip scheme if user pasted full URL (http://, https://)
        val schemeIdx = domain.indexOf("://")
        if (schemeIdx != -1) {
            domain = domain.substring(schemeIdx + 3)
        }

        // Strip path, query, or fragment
        val slashIdx = domain.indexOf('/')
        if (slashIdx != -1) {
            domain = domain.substring(0, slashIdx)
        }
        val questIdx = domain.indexOf('?')
        if (questIdx != -1) {
            domain = domain.substring(0, questIdx)
        }
        val hashIdx = domain.indexOf('#')
        if (hashIdx != -1) {
            domain = domain.substring(0, hashIdx)
        }

        // Strip port if present
        val colonIdx = domain.indexOf(':')
        if (colonIdx != -1 && !domain.contains("]")) {
            domain = domain.substring(0, colonIdx)
        }

        // Strip wildcard asterisk prefix
        if (domain.startsWith("*.")) {
            domain = domain.substring(2)
        } else if (domain.startsWith("*")) {
            domain = domain.substring(1)
        }

        // Strip trailing dots (FQDN root dot)
        while (domain.endsWith(".")) {
            domain = domain.dropLast(1)
        }

        // Lowercase
        domain = domain.lowercase(Locale.US)

        // Convert IDN (International Domain Names) to ASCII punycode (e.g. münchen.de -> xn--mnchen-3ya.de)
        return try {
            if (domain.isNotEmpty()) {
                IDN.toASCII(domain, IDN.ALLOW_UNASSIGNED)
            } else {
                ""
            }
        } catch (_: Exception) {
            domain
        }
    }

    /**
     * Checks if a target domain matches a rule domain either exactly or as a subdomain.
     *
     * Example:
     * Target "m.youtube.com" matches rule "youtube.com" (is a subdomain).
     * Target "youtube.com" matches rule "youtube.com" (exact match).
     * Target "notyoutube.com" does NOT match rule "youtube.com" (not naive substring).
     * Target "www.youtube.com" matches rule "youtube.com".
     * Target "youtube.com" matches rule "www.youtube.com" when ignoreWww is true.
     */
    fun matches(targetDomain: String, rulePattern: String): Boolean {
        val target = normalize(targetDomain)
        val rule = normalize(rulePattern)

        if (target.isEmpty() || rule.isEmpty()) return false

        // Exact match
        if (target == rule) return true

        // Subdomain match: target ends with "." + rule
        // e.g. "googlevideo.com" does NOT match "youtube.com"
        // "r1---sn-abc.googlevideo.com" matches "googlevideo.com"
        // "video.youtube.com" ends with ".youtube.com"
        if (target.endsWith(".$rule")) return true

        // Handle www variations: "www.example.com" vs "example.com"
        val targetWithoutWww = if (target.startsWith("www.")) target.substring(4) else target
        val ruleWithoutWww = if (rule.startsWith("www.")) rule.substring(4) else rule

        if (targetWithoutWww == ruleWithoutWww) return true
        if (targetWithoutWww.endsWith(".$ruleWithoutWww")) return true

        return false
    }
}
