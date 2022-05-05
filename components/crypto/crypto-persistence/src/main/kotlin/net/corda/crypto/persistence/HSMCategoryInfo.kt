package net.corda.crypto.persistence

import net.corda.data.crypto.wire.hsm.PrivateKeyPolicy

data class HSMCategoryInfo(
    val category: String,
    val keyPolicy: PrivateKeyPolicy
)
