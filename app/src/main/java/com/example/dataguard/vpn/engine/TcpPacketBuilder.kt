package com.example.dataguard.vpn.engine

import java.net.InetAddress

/**
 * Builds standard IPv4 / TCP packets for the TUN virtual interface with RFC 793 compliant checksums.
 */
object TcpPacketBuilder {

    const val FLAG_FIN = 0x01
    const val FLAG_SYN = 0x02
    const val FLAG_RST = 0x04
    const val FLAG_PSH = 0x08
    const val FLAG_ACK = 0x10

    /**
     * Synthesizes an IPv4 TCP packet.
     */
    fun buildIpv4TcpPacket(
        srcIp: String,
        dstIp: String,
        srcPort: Int,
        dstPort: Int,
        seqNum: Long,
        ackNum: Long,
        flags: Int,
        payload: ByteArray = ByteArray(0),
        windowSize: Int = 65535
    ): ByteArray {
        val ipHeaderLen = 20
        val tcpHeaderLen = 20
        val totalLength = ipHeaderLen + tcpHeaderLen + payload.size
        val packet = ByteArray(totalLength)

        val srcAddrBytes = InetAddress.getByName(srcIp).address
        val dstAddrBytes = InetAddress.getByName(dstIp).address

        // --- IPv4 Header (20 bytes) ---
        packet[0] = 0x45.toByte() // Version 4, IHL 5
        packet[1] = 0x00.toByte() // DSCP / ECN
        packet[2] = ((totalLength shr 8) and 0xFF).toByte()
        packet[3] = (totalLength and 0xFF).toByte()
        packet[4] = 0x00.toByte() // Identification
        packet[5] = 0x00.toByte()
        packet[6] = 0x40.toByte() // Flags: Don't Fragment
        packet[7] = 0x00.toByte() // Fragment Offset
        packet[8] = 64.toByte()   // TTL
        packet[9] = 6.toByte()    // Protocol: TCP (6)
        packet[10] = 0x00.toByte() // Header checksum placeholder
        packet[11] = 0x00.toByte()

        System.arraycopy(srcAddrBytes, 0, packet, 12, 4)
        System.arraycopy(dstAddrBytes, 0, packet, 16, 4)

        // Calculate IPv4 Header Checksum
        val ipChecksum = SystemDnsResolver.calculateIpv4Checksum(packet, 0, ipHeaderLen)
        packet[10] = ((ipChecksum.toInt() shr 8) and 0xFF).toByte()
        packet[11] = (ipChecksum.toInt() and 0xFF).toByte()

        // --- TCP Header (20 bytes) ---
        val tcpOffset = ipHeaderLen
        packet[tcpOffset + 0] = ((srcPort shr 8) and 0xFF).toByte()
        packet[tcpOffset + 1] = (srcPort and 0xFF).toByte()
        packet[tcpOffset + 2] = ((dstPort shr 8) and 0xFF).toByte()
        packet[tcpOffset + 3] = (dstPort and 0xFF).toByte()

        // Sequence number (32 bits)
        packet[tcpOffset + 4] = ((seqNum shr 24) and 0xFF).toByte()
        packet[tcpOffset + 5] = ((seqNum shr 16) and 0xFF).toByte()
        packet[tcpOffset + 6] = ((seqNum shr 8) and 0xFF).toByte()
        packet[tcpOffset + 7] = (seqNum and 0xFF).toByte()

        // Acknowledgment number (32 bits)
        packet[tcpOffset + 8] = ((ackNum shr 24) and 0xFF).toByte()
        packet[tcpOffset + 9] = ((ackNum shr 16) and 0xFF).toByte()
        packet[tcpOffset + 10] = ((ackNum shr 8) and 0xFF).toByte()
        packet[tcpOffset + 11] = (ackNum and 0xFF).toByte()

        // Data Offset: 5 (20 bytes) in high 4 bits, reserved 0
        packet[tcpOffset + 12] = 0x50.toByte()
        packet[tcpOffset + 13] = (flags and 0xFF).toByte()

        // Window size
        packet[tcpOffset + 14] = ((windowSize shr 8) and 0xFF).toByte()
        packet[tcpOffset + 15] = (windowSize and 0xFF).toByte()

        // Checksum placeholder (0x0000)
        packet[tcpOffset + 16] = 0x00.toByte()
        packet[tcpOffset + 17] = 0x00.toByte()

        // Urgent pointer
        packet[tcpOffset + 18] = 0x00.toByte()
        packet[tcpOffset + 19] = 0x00.toByte()

        // Payload
        if (payload.isNotEmpty()) {
            System.arraycopy(payload, 0, packet, ipHeaderLen + tcpHeaderLen, payload.size)
        }

        // Calculate RFC 793 TCP Checksum over Pseudo-Header + TCP Header + Payload
        val tcpChecksum = calculateTcpChecksum(
            srcAddrBytes = srcAddrBytes,
            dstAddrBytes = dstAddrBytes,
            packet = packet,
            tcpOffset = tcpOffset,
            tcpLength = tcpHeaderLen + payload.size
        )

        packet[tcpOffset + 16] = ((tcpChecksum.toInt() shr 8) and 0xFF).toByte()
        packet[tcpOffset + 17] = (tcpChecksum.toInt() and 0xFF).toByte()

        return packet
    }

    /**
     * Calculates standard RFC 793 one's complement TCP checksum.
     */
    fun calculateTcpChecksum(
        srcAddrBytes: ByteArray,
        dstAddrBytes: ByteArray,
        packet: ByteArray,
        tcpOffset: Int,
        tcpLength: Int
    ): Short {
        var sum = 0L

        // Pseudo-Header
        sum += ((srcAddrBytes[0].toInt() and 0xFF) shl 8) or (srcAddrBytes[1].toInt() and 0xFF)
        sum += ((srcAddrBytes[2].toInt() and 0xFF) shl 8) or (srcAddrBytes[3].toInt() and 0xFF)
        sum += ((dstAddrBytes[0].toInt() and 0xFF) shl 8) or (dstAddrBytes[1].toInt() and 0xFF)
        sum += ((dstAddrBytes[2].toInt() and 0xFF) shl 8) or (dstAddrBytes[3].toInt() and 0xFF)
        sum += 6L // Protocol: TCP (6)
        sum += tcpLength.toLong()

        // TCP Header and Payload
        var i = tcpOffset
        val end = tcpOffset + tcpLength
        while (i < end) {
            val b1 = packet[i].toInt() and 0xFF
            val b2 = if (i + 1 < end) packet[i + 1].toInt() and 0xFF else 0
            sum += (b1 shl 8) or b2
            i += 2
        }

        while (sum > 0xFFFF) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }

        return (sum.inv() and 0xFFFF).toShort()
    }
}
