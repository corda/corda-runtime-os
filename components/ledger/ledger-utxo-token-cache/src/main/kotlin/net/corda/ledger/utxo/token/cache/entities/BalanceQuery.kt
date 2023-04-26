package net.corda.ledger.utxo.token.cache.entities

import net.corda.data.ledger.utxo.token.selection.key.TokenPoolCacheKey

data class BalanceQuery(
    val externalEventRequestId: String,
    val flowId: String,
    override val poolKey: TokenPoolCacheKey
) : TokenEvent
