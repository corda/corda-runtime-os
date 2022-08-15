package net.corda.crypto.service.impl

import net.corda.crypto.config.impl.PrivateKeyPolicy

data class HSMStats(
    val allUsages: Int,
    val hsmId: String,
    val privateKeyPolicy: PrivateKeyPolicy,
    val capacity: Int
)