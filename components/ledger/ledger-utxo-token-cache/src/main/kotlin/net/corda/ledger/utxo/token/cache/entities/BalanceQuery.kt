package net.corda.ledger.utxo.token.cache.entities

data class BalanceQuery(
    override val externalEventRequestId: String,
    override val flowId: String,
    override val tagRegex: String?,
    override val ownerHash: String?,
    override val poolKey: TokenPoolKey,
) : TokenFilter
