package net.corda.crypto.persistence

data class HSMUsage(
    val hsmId: String,
    val usages: Int
)