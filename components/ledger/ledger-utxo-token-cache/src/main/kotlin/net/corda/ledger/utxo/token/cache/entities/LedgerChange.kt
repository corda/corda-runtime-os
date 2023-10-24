package net.corda.ledger.utxo.token.cache.entities

data class LedgerChange(
    override val poolKey: TokenPoolKey,
    val claimId: String?, // HACK: Added for testing will be removed by CORE-5722 (ledger integration)
    override val externalEventRequestId: String,
    override val flowId: String,
    val consumedTokens: List<CachedToken>,
    val producedTokens: List<CachedToken>
) : TokenEvent
