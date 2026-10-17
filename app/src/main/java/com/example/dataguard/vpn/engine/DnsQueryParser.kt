package com.example.dataguard.vpn.engine

import java.net.InetAddress
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

    /**
     * Overload for ByteArray slice or entire array.
     */
    fun extractDnsDomain(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size): String? {
        return try {
            val buf = ByteBuffer.wrap(bytes, offset, length)
            extractDnsDomain(buf)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Extracts resolved IPv4 addresses from standard DNS response byte array.
     */
    fun extractResolvedIpv4Addresses(responseBytes: ByteArray): List<String> {
        val resolvedIps = mutableListOf<String>()
        try {
            val buf = ByteBuffer.wrap(responseBytes)
            if (buf.remaining() < 12) return emptyList()

            val id = buf.short.toInt() and 0xFFFF
            val flags = buf.short.toInt() and 0xFFFF
            val qdCount = buf.short.toInt() and 0xFFFF
            val anCount = buf.short.toInt() and 0xFFFF
            val nsCount = buf.short.toInt() and 0xFFFF
            val arCount = buf.short.toInt() and 0xFFFF

            if (anCount < 1) return emptyList()

            // Skip Questions
            for (i in 0 until qdCount) {
                // Skip QNAME
                while (buf.hasRemaining()) {
                    val len = buf.get().toInt() and 0xFF
                    if (len == 0) break
                    if ((len and 0xC0) == 0xC0) {
                        // Pointer: 2 bytes total
                        if (buf.hasRemaining()) buf.get()
                        break
                    }
                    if (buf.remaining() >= len) {
                        buf.position(buf.position() + len)
                    } else {
                        return emptyList()
                    }
                }
                // Skip QTYPE (2) + QCLASS (2)
                if (buf.remaining() >= 4) {
                    buf.position(buf.position() + 4)
                }
            }

            // Parse Answers
            for (i in 0 until anCount) {
                if (buf.remaining() < 2) break
                val firstByte = buf.get().toInt() and 0xFF
                if ((firstByte and 0xC0) == 0xC0) {
                    // Pointer: 1 more byte
                    if (buf.hasRemaining()) buf.get()
                } else {
                    // Label sequence
                    var len = firstByte
                    while (len > 0 && buf.remaining() >= len) {
                        buf.position(buf.position() + len)
                        if (!buf.hasRemaining()) break
                        len = buf.get().toInt() and 0xFF
                    }
                }

                if (buf.remaining() < 10) break
                val type = buf.short.toInt() and 0xFFFF
                val clazz = buf.short.toInt() and 0xFFFF
                val ttl = buf.int
                val rdLength = buf.short.toInt() and 0xFFFF

                if (type == 1 && rdLength == 4 && buf.remaining() >= 4) { // Type A (IPv4)
                    val ipBytes = ByteArray(4)
                    buf.get(ipBytes)
                    val ip = InetAddress.getByAddress(ipBytes).hostAddress
                    if (!ip.isNullOrBlank()) {
                        resolvedIps.add(ip)
                    }
                } else {
                    if (buf.remaining() >= rdLength) {
                        buf.position(buf.position() + rdLength)
                    } else {
                        break
                    }
                }
            }
        } catch (_: Exception) {
            // Parsing errors handled gracefully
        }
        return resolvedIps
    }

    /**
     * Extracts resolved IPv4 addresses along with their authoritative DNS TTL (in seconds).
     */
    fun extractResolvedIpv4Records(responseBytes: ByteArray): List<DnsResolvedRecord> {
        val records = mutableListOf<DnsResolvedRecord>()
        try {
            val buf = ByteBuffer.wrap(responseBytes)
            if (buf.remaining() < 12) return emptyList()

            val id = buf.short.toInt() and 0xFFFF
            val flags = buf.short.toInt() and 0xFFFF
            val qdCount = buf.short.toInt() and 0xFFFF
            val anCount = buf.short.toInt() and 0xFFFF
            val nsCount = buf.short.toInt() and 0xFFFF
            val arCount = buf.short.toInt() and 0xFFFF

            if (anCount < 1) return emptyList()

            // Skip Questions
            for (i in 0 until qdCount) {
                while (buf.hasRemaining()) {
                    val len = buf.get().toInt() and 0xFF
                    if (len == 0) break
                    if ((len and 0xC0) == 0xC0) {
                        if (buf.hasRemaining()) buf.get()
                        break
                    }
                    if (buf.remaining() >= len) {
                        buf.position(buf.position() + len)
                    } else {
                        return emptyList()
                    }
                }
                if (buf.remaining() >= 4) {
                    buf.position(buf.position() + 4)
                }
            }

            // Parse Answers
            for (i in 0 until anCount) {
                if (buf.remaining() < 2) break
                val firstByte = buf.get().toInt() and 0xFF
                if ((firstByte and 0xC0) == 0xC0) {
                    if (buf.hasRemaining()) buf.get()
                } else {
                    var len = firstByte
                    while (len > 0 && buf.remaining() >= len) {
                        buf.position(buf.position() + len)
                        if (!buf.hasRemaining()) break
                        len = buf.get().toInt() and 0xFF
                    }
                }

                if (buf.remaining() < 10) break
                val type = buf.short.toInt() and 0xFFFF
                val clazz = buf.short.toInt() and 0xFFFF
                val ttlSeconds = buf.int.toLong() and 0xFFFFFFFFL
                val rdLength = buf.short.toInt() and 0xFFFF

                if (type == 1 && rdLength == 4 && buf.remaining() >= 4) { // Type A (IPv4)
                    val ipBytes = ByteArray(4)
                    buf.get(ipBytes)
                    val ip = InetAddress.getByAddress(ipBytes).hostAddress
                    if (!ip.isNullOrBlank()) {
                        records.add(DnsResolvedRecord(ip, ttlSeconds))
                    }
                } else {
                    if (buf.remaining() >= rdLength) {
                        buf.position(buf.position() + rdLength)
                    } else {
                        break
                    }
                }
            }
        } catch (_: Exception) {
            // Handled gracefully
        }
        return records
    }
}

/**
 * Resolved DNS Type A record with IP and TTL seconds.
 */
data class DnsResolvedRecord(
    val ip: String,
    val ttlSeconds: Long
)

