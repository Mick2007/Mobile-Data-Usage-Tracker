package com.example.dataguard.vpn.engine

import java.nio.ByteBuffer

object DnsQueryParser {

    /**
     * Parses standard DNS query UDP payload (RFC 1035) to extract the queried domain name.
     * Does NOT touch response data or decrypt traffic.
     *
     * Returns lowercase domain name if valid query, or null otherwise.
     */
    fun extractDnsDomain(buffer: ByteBuffer): String? {
        val originalPos = buffer.position()
        try {
            // DNS header is 12 bytes
            if (buffer.remaining() < 12) return null

            val id = buffer.short.toInt() and 0xFFFF
            val flags = buffer.short.toInt() and 0xFFFF
            val qdCount = buffer.short.toInt() and 0xFFFF
            val anCount = buffer.short.toInt() and 0xFFFF
            val nsCount = buffer.short.toInt() and 0xFFFF
            val arCount = buffer.short.toInt() and 0xFFFF

            // Must have at least 1 question
            if (qdCount < 1) return null

            // Read QNAME labels
            val domainBuilder = StringBuilder()
            var loopGuard = 0
            while (buffer.hasRemaining() && loopGuard++ < 128) {
                val labelLen = buffer.get().toInt() and 0xFF
                if (labelLen == 0) break // null terminator

                if (labelLen > 63 || buffer.remaining() < labelLen) return null

                val labelBytes = ByteArray(labelLen)
                buffer.get(labelBytes)
                val label = String(labelBytes, Charsets.US_ASCII)

                if (domainBuilder.isNotEmpty()) {
                    domainBuilder.append('.')
                }
                domainBuilder.append(label)
            }

            val domain = domainBuilder.toString().trim().lowercase()
            if (domain.isNotEmpty() && domain.all { it.isLetterOrDigit() || it == '.' || it == '-' || it == '_' }) {
                return domain
            }
        } catch (_: Exception) {
            // Malformed DNS query safely ignored
        } finally {
            buffer.position(originalPos)
        }
        return null
    }
}
