package net.corda.crypto.persistence.hsm

data class HSMUsage(
    val hsmId: String,
    val usages: Int
)