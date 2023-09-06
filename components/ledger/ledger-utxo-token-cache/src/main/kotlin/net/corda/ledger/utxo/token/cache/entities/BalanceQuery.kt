package net.corda.ledger.utxo.token.cache.entities

data class BalanceQuery(
    val externalEventRequestId: String,
    val flowId: String,
    override val tagRegex: String?,
    override val ownerHash: String?,
    override val poolKey: TokenPoolKey,
) : TokenFilter

