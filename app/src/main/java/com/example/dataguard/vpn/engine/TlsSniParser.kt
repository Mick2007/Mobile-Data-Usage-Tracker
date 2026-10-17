package com.example.dataguard.vpn.engine

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/**
 * Result of feeding TCP payload into the per-flow TLS reassembly buffer.
 */
sealed class TlsFeedResult {
    data class Parsed(val sni: String?) : TlsFeedResult()
    data class NeedMoreData(val currentBytes: Int, val expectedBytes: Int) : TlsFeedResult()
    object NotTls : TlsFeedResult()
    object ExceededCapacity : TlsFeedResult()
}

/**
 * Per-flow TLS ClientHello reassembly buffer (capped at 16 KB).
 * Reassembles TLS ClientHello records that span multiple TCP packets across the TUN MTU (1500 bytes).
 */
class TlsFlowBuffer(private val maxCapacity: Int = 16384) {
    private val outputStream = ByteArrayOutputStream(2048)

    fun feed(data: ByteArray, offset: Int = 0, length: Int = data.size): TlsFeedResult {
        val available = maxCapacity - outputStream.size()
        if (available <= 0) {
            return TlsFeedResult.ExceededCapacity
        }

        val toWrite = minOf(length, available)
        outputStream.write(data, offset, toWrite)

        val bytes = outputStream.toByteArray()
        if (bytes.size < 5) {
            return TlsFeedResult.NeedMoreData(bytes.size, 5)
        }

        // Validate TLS Record Header
        val contentType = bytes[0].toInt() and 0xFF
        if (contentType != 22) { // 22 = Handshake
            return TlsFeedResult.NotTls
        }

        val major = bytes[1].toInt() and 0xFF
        if (major != 3) { // SSL 3.0, TLS 1.0, 1.1, 1.2, 1.3
            return TlsFeedResult.NotTls
        }

        val recordLength = ((bytes[3].toInt() and 0xFF) shl 8) or (bytes[4].toInt() and 0xFF)
        val totalExpected = 5 + recordLength

        if (bytes.size < totalExpected) {
            if (bytes.size >= maxCapacity) {
                // If capped before full record, attempt best-effort parse of available extensions
                val partialSni = TlsSniParser.extractSniHostname(ByteBuffer.wrap(bytes), allowPartial = true)
                return TlsFeedResult.Parsed(partialSni)
            }
            return TlsFeedResult.NeedMoreData(bytes.size, totalExpected)
        }

        // Complete TLS record received
        val sni = TlsSniParser.extractSniHostname(ByteBuffer.wrap(bytes, 0, totalExpected))
        return TlsFeedResult.Parsed(sni)
    }

    fun currentSize(): Int = outputStream.size()
}

/**
 * Safe parser for TLS ClientHello Server Name Indication (SNI) extension (RFC 6066).
 * Inspects hostnames without decrypting payloads or touching certificates.
 */
object TlsSniParser {

    /**
     * Extracts TLS SNI from standard TLS record starting with 5-byte header.
     */
    fun extractSniHostname(buffer: ByteBuffer, allowPartial: Boolean = false): String? {
        val originalPos = buffer.position()
        try {
            if (buffer.remaining() < 5) return null
            val contentType = buffer.get().toInt() and 0xFF
            if (contentType != 22) return null // 22 = Handshake

            val majorVersion = buffer.get().toInt() and 0xFF
            val minorVersion = buffer.get().toInt() and 0xFF
            val recordLength = buffer.short.toInt() and 0xFFFF
            if (!allowPartial && buffer.remaining() < recordLength) return null

            return extractSniFromClientHello(buffer)
        } catch (_: Exception) {
            // Malformed TLS ClientHello handled safely
        } finally {
            buffer.position(originalPos)
        }
        return null
    }

    /**
     * Extracts SNI from buffer starting at the Handshake header (type 0x01 ClientHello).
     */
    fun extractSniFromClientHello(buffer: ByteBuffer): String? {
        try {
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
            val availableEnd = minOf(extensionsEnd, buffer.limit())

            while (buffer.position() + 4 <= availableEnd) {
                val extType = buffer.short.toInt() and 0xFFFF
                val extLen = buffer.short.toInt() and 0xFFFF
                if (buffer.position() + extLen > availableEnd) break

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
            // Handled safely
        }
        return null
    }

    /**
     * Convenience method for ByteArray input.
     */
    fun extractSniHostname(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size): String? {
        return try {
            val buf = ByteBuffer.wrap(bytes, offset, length)
            extractSniHostname(buf)
        } catch (_: Exception) {
            null
        }
    }
}
