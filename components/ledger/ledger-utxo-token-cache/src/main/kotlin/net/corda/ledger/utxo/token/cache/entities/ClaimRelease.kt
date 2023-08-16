package net.corda.ledger.utxo.token.cache.entities

data class ClaimRelease(
    val claimId: String,
    val externalEventRequestId: String,
    val flowId: String,
    val usedTokens: Set<String>,
    override val poolKey: TokenPoolKey
) : TokenEvent
