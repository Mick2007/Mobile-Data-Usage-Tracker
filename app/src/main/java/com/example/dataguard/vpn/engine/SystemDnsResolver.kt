package com.example.dataguard.vpn.engine

import android.content.Context
import android.net.ConnectivityManager
import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.nio.ByteBuffer

/**
 * Utility for discovering system default DNS resolvers, forwarding intercepted
 * DNS queries upstream through protected sockets, and synthesizing RFC 1035 DNS
 * and IPv4/UDP packets for the TUN virtual interface.
 */
object SystemDnsResolver {

    private const val TAG = "SystemDnsResolver"
    const val VIRTUAL_DNS_IP = "10.1.10.2"
    const val VIRTUAL_CLIENT_IP = "10.1.10.1"
    const val DNS_PORT = 53

    /**
     * Resolves the device's active network default DNS servers using ConnectivityManager.
     * Falls back to standard public resolvers (8.8.8.8, 1.1.1.1) if none are discovered.
     */
    fun getSystemDnsServers(context: Context): List<InetAddress> {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val activeNetwork = cm?.activeNetwork
            val linkProps = activeNetwork?.let { cm.getLinkProperties(it) }
            val ipv4Dns = linkProps?.dnsServers?.filterIsInstance<Inet4Address>() ?: emptyList()
            if (ipv4Dns.isNotEmpty()) {
                return ipv4Dns
            }
            val allDns = linkProps?.dnsServers ?: emptyList()
            if (allDns.isNotEmpty()) {
                return allDns
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to inspect system DNS servers: ${e.message}")
        }

        return listOf(
            InetAddress.getByName("8.8.8.8"),
            InetAddress.getByName("1.1.1.1")
        )
    }

    /**
     * Forwards an intercepted DNS query to the upstream DNS server using a protected socket.
     * The socket is protected via [socketProtector] (invoking [android.net.VpnService.protect])
     * so that upstream UDP packets bypass the TUN virtual interface.
     */
    fun forwardDnsQuery(
        queryData: ByteArray,
        upstreamServers: List<InetAddress>,
        socketProtector: ((DatagramSocket) -> Boolean)? = null,
        timeoutMs: Int = 2500
    ): ByteArray? {
        if (queryData.isEmpty() || upstreamServers.isEmpty()) return null

        for (server in upstreamServers) {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket()
                val protectedSuccessfully = socketProtector?.invoke(socket) ?: true
                if (!protectedSuccessfully) {
                    Log.w(TAG, "Warning: DatagramSocket could not be protected by VpnService")
                }

                socket.soTimeout = timeoutMs
                val outPacket = DatagramPacket(queryData, queryData.size, server, DNS_PORT)
                socket.send(outPacket)

                val responseBuffer = ByteArray(2048)
                val inPacket = DatagramPacket(responseBuffer, responseBuffer.size)
                socket.receive(inPacket)

                if (inPacket.length > 0) {
                    return responseBuffer.copyOf(inPacket.length)
                }
            } catch (e: Exception) {
                Log.d(TAG, "DNS query forward to ${server.hostAddress} failed: ${e.message}")
            } finally {
                try {
                    socket?.close()
                } catch (_: Exception) {}
            }
        }

        return null
    }

    /**
     * Synthesizes a standard RFC 1035 NXDOMAIN (Name Error) response for blocked domains.
     */
    fun buildDnsNxDomainResponse(queryData: ByteArray): ByteArray? {
        if (queryData.size < 12) return null

        val response = ByteArray(queryData.size)
        // Copy transaction ID
        response[0] = queryData[0]
        response[1] = queryData[1]
        // Flags: QR=1 (Response), Opcode=0, AA=1 (Authoritative), TC=0, RD=1, RA=1, RCODE=3 (NXDOMAIN)
        // 0x8183 = 1000 0001 1000 0011
        response[2] = 0x81.toByte()
        response[3] = 0x83.toByte()
        // QDCOUNT: 1
        response[4] = 0x00
        response[5] = 0x01
        // ANCOUNT, NSCOUNT, ARCOUNT: 0
        response[6] = 0x00
        response[7] = 0x00
        response[8] = 0x00
        response[9] = 0x00
        response[10] = 0x00
        response[11] = 0x00

        // Copy question section verbatim
        System.arraycopy(queryData, 12, response, 12, queryData.size - 12)
        return response
    }

    /**
     * Synthesizes a standard RFC 1035 Sinkhole (A record answering 0.0.0.0) response.
     */
    fun buildDnsSinkholeResponse(queryData: ByteArray): ByteArray? {
        if (queryData.size < 12) return null

        // Find end of Question section: QNAME (ends with 0x00) + QTYPE (2 bytes) + QCLASS (2 bytes)
        var pos = 12
        while (pos < queryData.size) {
            val len = queryData[pos].toInt() and 0xFF
            if (len == 0) {
                pos++ // past 0-length label
                break
            }
            pos += 1 + len
        }
        val questionEnd = pos + 4 // plus QTYPE and QCLASS
        if (questionEnd > queryData.size) return null

        val questionLen = questionEnd - 12
        val answerRecordLen = 16 // 2 (pointer) + 2 (type A) + 2 (class IN) + 4 (TTL) + 2 (rdlength=4) + 4 (IP: 0.0.0.0)
        val totalLen = 12 + questionLen + answerRecordLen

        val response = ByteBuffer.allocate(totalLen)
        // Header
        response.put(queryData[0])
        response.put(queryData[1])
        // Flags: QR=1 (Response), Opcode=0, AA=1, RD=1, RA=1, RCODE=0 (NoError)
        response.put(0x81.toByte())
        response.put(0x80.toByte())
        // QDCOUNT = 1, ANCOUNT = 1, NSCOUNT = 0, ARCOUNT = 0
        response.putShort(1)
        response.putShort(1)
        response.putShort(0)
        response.putShort(0)

        // Question section
        response.put(queryData, 12, questionLen)

        // Answer section: Name pointer to question QNAME at offset 12 (0xC00C)
        response.putShort(0xC00C.toShort())
        response.putShort(1) // Type A
        response.putShort(1) // Class IN
        response.putInt(60)  // TTL: 60 seconds
        response.putShort(4) // RDLENGTH: 4 bytes
        response.put(0.toByte()) // 0.0.0.0
        response.put(0.toByte())
        response.put(0.toByte())
        response.put(0.toByte())

        return response.array()
    }

    /**
     * Constructs a valid raw IPv4 + UDP packet wrapping [payload].
     * Computes IPv4 header checksum according to RFC 791.
     */
    fun buildIpv4UdpPacket(
        srcIp: String,
        dstIp: String,
        srcPort: Int,
        dstPort: Int,
        payload: ByteArray
    ): ByteArray {
        val ipHeaderLen = 20
        val udpHeaderLen = 8
        val totalLength = ipHeaderLen + udpHeaderLen + payload.size
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
        packet[9] = 17.toByte()   // Protocol: UDP (17)
        packet[10] = 0x00.toByte() // Header checksum placeholder
        packet[11] = 0x00.toByte()

        System.arraycopy(srcAddrBytes, 0, packet, 12, 4)
        System.arraycopy(dstAddrBytes, 0, packet, 16, 4)

        // Compute and store IPv4 checksum
        val ipChecksum = calculateIpv4Checksum(packet, 0, ipHeaderLen)
        packet[10] = ((ipChecksum.toInt() shr 8) and 0xFF).toByte()
        packet[11] = (ipChecksum.toInt() and 0xFF).toByte()

        // --- UDP Header (8 bytes) ---
        val udpOffset = ipHeaderLen
        packet[udpOffset + 0] = ((srcPort shr 8) and 0xFF).toByte()
        packet[udpOffset + 1] = (srcPort and 0xFF).toByte()
        packet[udpOffset + 2] = ((dstPort shr 8) and 0xFF).toByte()
        packet[udpOffset + 3] = (dstPort and 0xFF).toByte()

        val udpLen = udpHeaderLen + payload.size
        packet[udpOffset + 4] = ((udpLen shr 8) and 0xFF).toByte()
        packet[udpOffset + 5] = (udpLen and 0xFF).toByte()
        // UDP Checksum (0x0000 = disabled/optional in IPv4)
        packet[udpOffset + 6] = 0x00.toByte()
        packet[udpOffset + 7] = 0x00.toByte()

        // --- Payload ---
        System.arraycopy(payload, 0, packet, ipHeaderLen + udpHeaderLen, payload.size)

        return packet
    }

    /**
     * Calculates RFC 791 IPv4 one's complement header checksum.
     */
    fun calculateIpv4Checksum(data: ByteArray, offset: Int, length: Int): Short {
        var sum = 0
        var i = offset
        while (i < offset + length) {
            val byte1 = data[i].toInt() and 0xFF
            val byte2 = if (i + 1 < offset + length) data[i + 1].toInt() and 0xFF else 0
            val word = (byte1 shl 8) or byte2
            sum += word
            i += 2
        }

        while (sum > 0xFFFF) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }

        return (sum.inv() and 0xFFFF).toShort()
    }
}
