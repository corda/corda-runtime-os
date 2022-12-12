package net.corda.ledger.utxo.impl.token.selection.impl

import net.corda.data.ledger.utxo.token.selection.key.TokenPoolCacheKey

data class PoolKey(
    val shortHolderId: String,
    val tokenType: String,
    val issuerHash: String,
    val notaryX500Name: String,
    val symbol: String
) {
    companion object {
        fun fromTokenPoolCacheKey(key: TokenPoolCacheKey): PoolKey {
            return PoolKey(
                key.shortHolderId,
                key.tokenType,
                key.issuerHash,
                key.notaryX500Name,
                key.symbol
            )
        }
    }

    fun toTokenPoolCacheKey(): TokenPoolCacheKey {
        return TokenPoolCacheKey.newBuilder()
            .setShortHolderId(shortHolderId)
            .setTokenType(tokenType)
            .setIssuerHash(issuerHash)
            .setNotaryX500Name(notaryX500Name)
            .setSymbol(symbol)
            .build()
    }
}
