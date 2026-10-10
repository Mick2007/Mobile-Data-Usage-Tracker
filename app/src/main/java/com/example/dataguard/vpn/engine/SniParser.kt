package com.example.dataguard.vpn.engine

import java.nio.ByteBuffer

object SniParser {

    /**
     * Extracts TLS Server Name Indication (SNI) hostname from raw payload without decrypting TLS.
     * RFC 6066: TLS extensions - server_name.
     *
     * Returns the host string if valid SNI is found, or null otherwise.
     */
    fun extractSni(buffer: ByteBuffer): String? {
        val originalPos = buffer.position()
        try {
            // Need at least TLS record header (5 bytes) + Handshake header (4 bytes)
            if (buffer.remaining() < 9) return null

            val contentType = buffer.get().toInt() and 0xFF
            // TLS Handshake content type is 0x16 (22)
            if (contentType != 0x16) return null

            val majorVersion = buffer.get().toInt() and 0xFF
            val minorVersion = buffer.get().toInt() and 0xFF
            // TLS versions 3.0, 3.1, 3.2, 3.3
            if (majorVersion != 3) return null

            val recordLength = buffer.short.toInt() and 0xFFFF
            if (buffer.remaining() < recordLength) return null

            // Handshake message type: 0x01 is ClientHello
            val handshakeType = buffer.get().toInt() and 0xFF
            if (handshakeType != 0x01) return null

            // Handshake length (3 bytes)
            val hLen1 = buffer.get().toInt() and 0xFF
            val hLen2 = buffer.get().toInt() and 0xFF
            val hLen3 = buffer.get().toInt() and 0xFF
            val handshakeLen = (hLen1 shl 16) or (hLen2 shl 8) or hLen3

            // Skip Client Version (2 bytes)
            if (buffer.remaining() < 2 + 32) return null
            buffer.position(buffer.position() + 2)

            // Skip Random (32 bytes)
            buffer.position(buffer.position() + 32)

            // Session ID
            if (buffer.remaining() < 1) return null
            val sessionIdLen = buffer.get().toInt() and 0xFF
            if (buffer.remaining() < sessionIdLen) return null
            buffer.position(buffer.position() + sessionIdLen)

            // Cipher Suites
            if (buffer.remaining() < 2) return null
            val cipherSuitesLen = buffer.short.toInt() and 0xFFFF
            if (buffer.remaining() < cipherSuitesLen) return null
            buffer.position(buffer.position() + cipherSuitesLen)

            // Compression Methods
            if (buffer.remaining() < 1) return null
            val compMethodsLen = buffer.get().toInt() and 0xFF
            if (buffer.remaining() < compMethodsLen) return null
            buffer.position(buffer.position() + compMethodsLen)

            // Extensions
            if (buffer.remaining() < 2) return null
            val extensionsLen = buffer.short.toInt() and 0xFFFF
            if (buffer.remaining() < extensionsLen) return null

            val extensionsEnd = buffer.position() + extensionsLen

            while (buffer.position() + 4 <= extensionsEnd) {
                val extType = buffer.short.toInt() and 0xFFFF
                val extLen = buffer.short.toInt() and 0xFFFF

                if (buffer.position() + extLen > extensionsEnd) break

                // Extension type 0x0000 is server_name (SNI)
                if (extType == 0) {
                    val sniBlockEnd = buffer.position() + extLen
                    if (buffer.remaining() < 2) break
                    val serverNameListLen = buffer.short.toInt() and 0xFFFF

                    while (buffer.position() + 3 <= sniBlockEnd) {
                        val nameType = buffer.get().toInt() and 0xFF
                        val nameLen = buffer.short.toInt() and 0xFFFF

                        if (buffer.position() + nameLen > sniBlockEnd) break

                        // name_type 0 is host_name
                        if (nameType == 0) {
                            val hostBytes = ByteArray(nameLen)
                            buffer.get(hostBytes)
                            val hostname = String(hostBytes, Charsets.US_ASCII).trim().lowercase()
                            if (isValidHostname(hostname)) {
                                return hostname
                            }
                        } else {
                            buffer.position(buffer.position() + nameLen)
                        }
                    }
                    break
                } else {
                    buffer.position(buffer.position() + extLen)
                }
            }
        } catch (_: Exception) {
            // Malformed packet or insufficient data: safe fail-safe
        } finally {
            buffer.position(originalPos)
        }
        return null
    }

    private fun isValidHostname(host: String): Boolean {
        if (host.isEmpty() || host.length > 253) return false
        return host.all { it.isLetterOrDigit() || it == '.' || it == '-' || it == '_' }
    }
}
