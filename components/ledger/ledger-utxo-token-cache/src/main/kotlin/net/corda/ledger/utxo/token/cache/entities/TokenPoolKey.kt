package net.corda.ledger.utxo.token.cache.entities

import net.corda.data.ledger.utxo.token.selection.key.TokenPoolCacheKey

data class TokenPoolKey(
    val shortHolderId: String,
    val tokenType: String,
    val issuerHash: String,
    val notaryX500Name: String,
    val symbol: String
) {
    fun toAvro(): TokenPoolCacheKey {
        return TokenPoolCacheKey.newBuilder()
            .setShortHolderId(shortHolderId)
            .setTokenType(tokenType)
            .setIssuerHash(issuerHash)
            .setNotaryX500Name(notaryX500Name)
            .setSymbol(symbol)
            .build()
    }
}

