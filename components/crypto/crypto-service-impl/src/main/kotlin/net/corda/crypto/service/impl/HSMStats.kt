package net.corda.crypto.service.impl

import net.corda.crypto.config.impl.PrivateKeyPolicy

// TODO this is currently not being used anywhere
data class HSMStats(
    val allUsages: Int,
    val hsmId: String,
    val privateKeyPolicy: PrivateKeyPolicy,
    val capacity: Int
)