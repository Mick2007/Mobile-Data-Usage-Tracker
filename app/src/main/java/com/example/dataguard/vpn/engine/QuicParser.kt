package com.example.dataguard.vpn.engine

import java.nio.ByteBuffer

/**
 * Lightweight parser for QUIC (RFC 9000) Initial packets on UDP port 443.
 *
 * Inspects QUIC Initial packets for Server Name Indication (SNI) hostnames
 * in plaintext or early-exchange frames, falling back safely to DNS-derived
 * IP mappings when SNI extraction is infeasible.
 *
 * RFC 9000 Header Format:
 * - Long Header: First byte has bit 0x80 set.
 * - Initial Packet Type: Bits 4-5 (0x30) are 0x00 for Initial packet (0xC0..0xCF).
 * - Version: 4 bytes (e.g., 0x00000001 for QUIC v1).
 */
object QuicParser {

    const val QUIC_PORT = 443

    /**
     * Determines whether the given UDP payload is a QUIC packet (Long or Short Header).
     */
    fun isQuicPacket(buffer: ByteBuffer): Boolean {
        if (buffer.remaining() < 5) return false
        val originalPos = buffer.position()
        try {
            val firstByte = buffer.get(originalPos).toInt() and 0xFF
            // Long header has 0x80 set; Short header (1-RTT) has 0x40 fixed bit set and 0x80 clear.
            return (firstByte and 0x40) != 0
        } catch (_: Exception) {
            return false
        }
    }

    /**
     * Checks if this is a QUIC Long Header Initial packet (RFC 9000).
     */
    fun isQuicInitialPacket(buffer: ByteBuffer): Boolean {
        if (buffer.remaining() < 9) return false
        val originalPos = buffer.position()
        try {
            val firstByte = buffer.get(originalPos).toInt() and 0xFF
            // Initial packet in QUIC v1 has 0x80 (Long Header) and 0x00 type (bits 4-5)
            // (firstByte & 0xF0) == 0xC0 or (firstByte & 0xF0) == 0xD0 (for draft versions)
            if ((firstByte and 0x80) == 0) return false
            val packetType = (firstByte and 0x30) shr 4
            if (packetType != 0) return false // 0x00 = Initial

            // Read version (bytes 1-4)
            val version = (buffer.get(originalPos + 1).toInt() and 0xFF shl 24) or
                    (buffer.get(originalPos + 2).toInt() and 0xFF shl 16) or
                    (buffer.get(originalPos + 3).toInt() and 0xFF shl 8) or
                    (buffer.get(originalPos + 4).toInt() and 0xFF)

            // Version != 0 (version 0 is Version Negotiation)
            return version != 0
        } catch (_: Exception) {
            return false
        }
    }

    /**
     * Attempts to extract the TLS SNI hostname from a QUIC Initial packet.
     * Searches for TLS 1.3 ClientHello inside CRYPTO frame payloads.
     * Returns null if unparseable or encrypted.
     */
    fun extractSniHostname(buffer: ByteBuffer): String? {
        val originalPos = buffer.position()
        try {
            if (!isQuicInitialPacket(buffer)) return null

            // Search for TLS Handshake Record marker (0x16 0x03 0x01 or 0x16 0x03 0x03) or
            // ClientHello Handshake type (0x01) within the initial packet payload.
            val limit = buffer.limit()
            var scanPos = originalPos + 5 // skip initial header prefix

            // Fast scan for ClientHello handshake type (0x01) followed by length and version
            while (scanPos < limit - 40) {
                val b = buffer.get(scanPos).toInt() and 0xFF
                // Check if this position begins a TLS ClientHello (type 0x01)
                if (b == 0x01) {
                    val len1 = buffer.get(scanPos + 1).toInt() and 0xFF
                    val len2 = buffer.get(scanPos + 2).toInt() and 0xFF
                    val len3 = buffer.get(scanPos + 3).toInt() and 0xFF
                    val handshakeLen = (len1 shl 16) or (len2 shl 8) or len3

                    // Plausible ClientHello length (100 to 16384 bytes)
                    if (handshakeLen in 100..16384 && scanPos + 4 + handshakeLen <= limit + 2048) {
                        // Check client version at scanPos + 4: 0x03 0x03 (TLS 1.2/1.3)
                        val vMajor = buffer.get(scanPos + 4).toInt() and 0xFF
                        val vMinor = buffer.get(scanPos + 5).toInt() and 0xFF
                        if (vMajor == 0x03 && (vMinor == 0x01 || vMinor == 0x03)) {
                            // Synthesize a slice starting at this ClientHello
                            buffer.position(scanPos)
                            val candidate = TlsSniParser.extractSniFromClientHello(buffer)
                            if (candidate != null) {
                                return candidate
                            }
                        }
                    }
                }
                scanPos++
            }
        } catch (_: Exception) {
            // Handled safely
        } finally {
            buffer.position(originalPos)
        }
        return null
    }

    /**
     * Resolves the domain for a QUIC packet on UDP 443.
     * Attempts SNI extraction first, falling back strictly to DNS IP mapping.
     * Does NOT guess when neither is available.
     */
    fun resolveQuicDomain(
        payload: ByteBuffer,
        destinationIp: String,
        flowTracker: DomainFlowTracker?
    ): String? {
        val extractedSni = extractSniHostname(payload)
        if (extractedSni != null) {
            return extractedSni
        }
        // Fall back strictly to DNS-derived IP-to-domain map
        return flowTracker?.getPrimaryDomainForIp(destinationIp)
    }
}
