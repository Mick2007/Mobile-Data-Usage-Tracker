package com.example.dataguard.vpn.engine

enum class NetworkInterfaceType {
    MOBILE,
    WIFI,
    ANY
}

enum class FirewallDecision {
    ALLOW,
    BLOCK
}

enum class RuleType(val label: String) {
    GLOBAL_BLOCK("Global Block"),
    GLOBAL_ALLOW("Global Allow"),
    APP_BLOCK("App Block"),
    APP_ALLOW("App Allow")
}

data class FirewallEvaluationResult(
    val decision: FirewallDecision,
    val uid: Int,
    val packageName: String?,
    val matchedDomain: String? = null,
    val reason: String? = null
)
