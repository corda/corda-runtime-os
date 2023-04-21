package net.corda.ledger.utxo.token.cache.entities

import net.corda.data.ledger.utxo.token.selection.key.TokenPoolCacheKey

data class ClaimRelease(
    val claimId: String,
    val externalEventRequestId: String,
    val flowId: String,
    val usedTokens: Set<String>,
    override val poolKey: TokenPoolCacheKey
) : TokenEvent
