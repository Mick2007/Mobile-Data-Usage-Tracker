package com.example.dataguard.vpn.model

enum class VpnStatus(val label: String) {
    OFF("VPN OFF"),
    STARTING("VPN STARTING"),
    ACTIVE("VPN ACTIVE"),
    ERROR("VPN ERROR")
}

data class VpnState(
    val status: VpnStatus = VpnStatus.OFF,
    val startedAt: Long? = null,
    val bytesIn: Long = 0L,
    val bytesOut: Long = 0L,
    val packetsProcessed: Long = 0L,
    val localAddress: String? = null,
    val activeSessionName: String? = null,
    val errorMessage: String? = null,
    val isAutoStartOnBoot: Boolean = false
) {
    val bytesProcessed: Long get() = bytesIn + bytesOut
    val isRunning: Boolean get() = status == VpnStatus.ACTIVE
    val isStarting: Boolean get() = status == VpnStatus.STARTING
}
