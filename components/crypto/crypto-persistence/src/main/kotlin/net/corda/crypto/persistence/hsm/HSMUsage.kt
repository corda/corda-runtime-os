package net.corda.crypto.persistence.hsm

data class HSMUsage(
    val workerSetId: String,
    val usages: Int
)