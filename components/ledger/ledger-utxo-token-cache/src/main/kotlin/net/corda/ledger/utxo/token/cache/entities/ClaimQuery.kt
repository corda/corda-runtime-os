package net.corda.ledger.utxo.token.cache.entities

import java.math.BigDecimal
import net.corda.data.ledger.utxo.token.selection.key.TokenPoolCacheKey

data class ClaimQuery(
    val externalEventRequestId: String,
    val flowId: String,
    val targetAmount: BigDecimal,
    override val tagRegex: String?,
    override val ownerHash: String?,
    override val poolKey: TokenPoolCacheKey
): TokenFilter
