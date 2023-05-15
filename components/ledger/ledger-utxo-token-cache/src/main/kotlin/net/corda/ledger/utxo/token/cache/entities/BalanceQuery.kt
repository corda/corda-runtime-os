package net.corda.ledger.utxo.token.cache.entities

import net.corda.data.ledger.utxo.token.selection.key.TokenPoolCacheKey

data class BalanceQuery(
    val externalEventRequestId: String,
    val flowId: String,
    private val tagRegex: String?,
    private val ownerHash: String?,
    override val poolKey: TokenPoolCacheKey,
) : TokenFilter {
    override fun getTagRegex(): String? {
        return tagRegex
    }

    override fun getOwnerHash(): String? {
        return ownerHash
    }
}
