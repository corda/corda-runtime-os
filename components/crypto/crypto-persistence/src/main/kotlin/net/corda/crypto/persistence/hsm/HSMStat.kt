package net.corda.crypto.persistence.hsm

import net.corda.data.crypto.wire.hsm.PrivateKeyPolicy

data class HSMStat(
    val usages: Int,
    val configId: String,
    val privateKeyPolicy: PrivateKeyPolicy,
    val serviceName: String,
    val capacity: Int
)