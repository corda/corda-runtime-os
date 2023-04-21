package net.corda.ledger.utxo.token.cache.entities

import net.corda.data.ledger.utxo.token.selection.key.TokenPoolCacheKey
import java.math.BigDecimal

data class ClaimQuery(
    val externalEventRequestId: String,
    val flowId: String,
    val targetAmount: BigDecimal,
    val tagRegex: String?,
    val ownerHash: String?,
    override val poolKey: TokenPoolCacheKey
):TokenEvent
