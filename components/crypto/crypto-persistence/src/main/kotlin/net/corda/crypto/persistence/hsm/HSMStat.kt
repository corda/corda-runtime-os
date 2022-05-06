package net.corda.crypto.persistence.hsm

data class HSMStat(
    val usages: Int,
    val configId: String,
    val serviceName: String,
    val capacity: Int
)