package com.example.dataguard.vpn.engine

import java.nio.ByteBuffer

/**
 * Safe parser for TLS ClientHello Server Name Indication (SNI) extension (RFC 6066).
 * Inspects hostnames without decrypting payloads or touching certificates.
 */
object TlsSniParser {

    fun extractSniHostname(buffer: ByteBuffer): String? {
        val originalPos = buffer.position()
        try {
            // Check TLS Record Header (5 bytes)
            if (buffer.remaining() < 5) return null
            val contentType = buffer.get().toInt() and 0xFF
            if (contentType != 22) return null // 22 = Handshake

            val majorVersion = buffer.get().toInt() and 0xFF
            val minorVersion = buffer.get().toInt() and 0xFF
            val recordLength = buffer.short.toInt() and 0xFFFF
            if (buffer.remaining() < recordLength) return null

            // Handshake header: type (1 byte), length (3 bytes)
            if (buffer.remaining() < 4) return null
            val handshakeType = buffer.get().toInt() and 0xFF
            if (handshakeType != 1) return null // 1 = ClientHello

            // 3-byte handshake length
            val b1 = buffer.get().toInt() and 0xFF
            val b2 = buffer.get().toInt() and 0xFF
            val b3 = buffer.get().toInt() and 0xFF
            val handshakeLen = (b1 shl 16) or (b2 shl 8) or b3

            // Client Version (2 bytes) + Random (32 bytes) = 34 bytes
            if (buffer.remaining() < 34) return null
            buffer.position(buffer.position() + 34)

            // Session ID (1 byte length + data)
            if (!buffer.hasRemaining()) return null
            val sessionIdLen = buffer.get().toInt() and 0xFF
            if (buffer.remaining() < sessionIdLen) return null
            buffer.position(buffer.position() + sessionIdLen)

            // Cipher Suites (2 bytes length + data)
            if (buffer.remaining() < 2) return null
            val cipherSuitesLen = buffer.short.toInt() and 0xFFFF
            if (buffer.remaining() < cipherSuitesLen) return null
            buffer.position(buffer.position() + cipherSuitesLen)

            // Compression Methods (1 byte length + data)
            if (!buffer.hasRemaining()) return null
            val compressionLen = buffer.get().toInt() and 0xFF
            if (buffer.remaining() < compressionLen) return null
            buffer.position(buffer.position() + compressionLen)

            // Extensions (2 bytes length + data)
            if (buffer.remaining() < 2) return null
            val extensionsLen = buffer.short.toInt() and 0xFFFF
            val extensionsEnd = buffer.position() + extensionsLen
            if (buffer.remaining() < extensionsLen) return null

            while (buffer.position() + 4 <= extensionsEnd) {
                val extType = buffer.short.toInt() and 0xFFFF
                val extLen = buffer.short.toInt() and 0xFFFF
                if (buffer.position() + extLen > extensionsEnd) break

                if (extType == 0) { // server_name (SNI)
                    if (extLen >= 5) {
                        val serverNameListLen = buffer.short.toInt() and 0xFFFF
                        val nameType = buffer.get().toInt() and 0xFF
                        if (nameType == 0) { // host_name
                            val nameLen = buffer.short.toInt() and 0xFFFF
                            if (nameLen > 0 && buffer.remaining() >= nameLen) {
                                val nameBytes = ByteArray(nameLen)
                                buffer.get(nameBytes)
                                val hostname = String(nameBytes, Charsets.US_ASCII).trim().lowercase()
                                if (hostname.isNotEmpty() && hostname.all { it.isLetterOrDigit() || it == '.' || it == '-' || it == '_' }) {
                                    return hostname
                                }
                            }
                        }
                    }
                    break
                } else {
                    buffer.position(buffer.position() + extLen)
                }
            }
        } catch (_: Exception) {
            // Malformed TLS ClientHello handled safely
        } finally {
            buffer.position(originalPos)
        }
        return null
    }
}
