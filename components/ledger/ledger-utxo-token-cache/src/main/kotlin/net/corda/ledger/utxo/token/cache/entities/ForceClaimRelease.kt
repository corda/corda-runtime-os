package net.corda.ledger.utxo.token.cache.entities

data class ForceClaimRelease(
    val claimId: String,
    override val poolKey: TokenPoolKey
) : TokenEvent {
    // Unused
    override val externalEventRequestId: String = ""

    // Unused
    override val flowId: String = ""
}
