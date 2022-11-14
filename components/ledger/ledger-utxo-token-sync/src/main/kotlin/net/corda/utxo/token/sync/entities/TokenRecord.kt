package net.corda.utxo.token.sync.entities

import java.math.BigDecimal
import java.time.Instant

data class TokenRecord(
    val key: TokenPoolKeyRecord,
    val stateRef: String,
    val amount: BigDecimal,
    val ownerHash: String?,
    val tag: String?,
    val lastModified: Instant
)

