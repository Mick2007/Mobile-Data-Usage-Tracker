package com.example.dataguard.vpn.engine

import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.FileReader

/**
 * Socket table parser that reads `/proc/net/tcp`, `/proc/net/tcp6`, `/proc/net/udp`, and `/proc/net/udp6`
 * to map a local source port to its owning Android Application UID.
 *
 * Background:
 * On Android, `ConnectivityManager.getConnectionOwnerUid()` operates reliably on physical interfaces,
 * but for TUN-side synthetic virtual addresses or older Android API levels (< 29), it may return -1
 * (INVALID_UID). The Linux kernel maintains the active socket ownership table in `/proc/net/`, where
 * each entry lists the local port (in hex) and the socket owner UID at column 7.
 *
 * Format of `/proc/net/tcp`:
 * sl  local_address rem_address   st tx_queue rx_queue tr tm->when retrnsmt   uid  timeout inode
 *  1: 0100007F:C5C0 0100007F:1F40 01 00000000:00000000 00:00000000 00000000 10042        0 ...
 *
 * Column index 1: local_address ("0100007F:C5C0") -> port 0xC5C0 = 50624
 * Column index 7: uid (10042)
 */
object ProcNetSocketReader {

    private const val TAG = "ProcNetSocketReader"

    /**
     * Resolves the UID owning the specified local port from `/proc/net` socket tables.
     * @param protocol 6 for TCP, 17 for UDP.
     * @param srcPort The local source port number in decimal (1..65535).
     * @return The UID (e.g. 10042) if found, or -1 if not found.
     */
    fun findUidForPort(protocol: Int, srcPort: Int): Int {
        if (srcPort <= 0 || srcPort > 65535) return -1

        val filesToScan = if (protocol == 6) {
            listOf("/proc/net/tcp", "/proc/net/tcp6")
        } else {
            listOf("/proc/net/udp", "/proc/net/udp6")
        }

        for (filePath in filesToScan) {
            val uid = scanProcNetFile(filePath, srcPort)
            if (uid > 0) {
                return uid
            }
        }
        return -1
    }

    /**
     * Scans a single `/proc/net` file for a matching local port.
     */
    fun scanProcNetFile(filePath: String, targetPort: Int): Int {
        val file = File(filePath)
        if (!file.exists() || !file.canRead()) return -1

        try {
            BufferedReader(FileReader(file)).use { reader ->
                // Skip header line
                val header = reader.readLine() ?: return -1

                var line: String? = reader.readLine()
                while (line != null) {
                    val uid = parseProcNetLine(line, targetPort)
                    if (uid > 0) {
                        return uid
                    }
                    line = reader.readLine()
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Cannot read $filePath: ${e.message}")
        }
        return -1
    }

    /**
     * Parses a single line from a `/proc/net` table.
     */
    fun parseProcNetLine(line: String, targetPort: Int): Int {
        val tokens = line.trim().split(Regex("\\s+"))
        // We need at least 8 tokens: sl(0), local_address(1), rem_address(2), st(3), tx_rx(4), tr_tm(5), retrnsmt(6), uid(7)
        if (tokens.size < 8) return -1

        val localAddr = tokens[1]
        val colonIdx = localAddr.indexOf(':')
        if (colonIdx < 0 || colonIdx + 1 >= localAddr.length) return -1

        val portHex = localAddr.substring(colonIdx + 1)
        val port = portHex.toIntOrNull(16) ?: return -1

        if (port == targetPort) {
            return tokens[7].toIntOrNull() ?: -1
        }
        return -1
    }
}
