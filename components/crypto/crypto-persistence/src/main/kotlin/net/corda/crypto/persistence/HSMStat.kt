package net.corda.crypto.persistence

data class HSMStat(
    val usages: Int,
    val configId: String,
    val serviceName: String,
    val capacity: Int
)