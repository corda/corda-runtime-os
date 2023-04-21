package net.corda.ledger.utxo.token.cache.entities

import net.corda.data.ledger.utxo.token.selection.key.TokenPoolCacheKey

data class LedgerChange(
    override val poolKey: TokenPoolCacheKey,
    val claimId: String?, // HACK: Added for testing will be removed by CORE-5722 (ledger integration)
    val flowId: String?, // HACK: Added for testing will be removed by CORE-5722 (ledger integration)
    val consumedTokens: List<CachedToken>,
    val producedTokens: List<CachedToken>
) : TokenEvent
