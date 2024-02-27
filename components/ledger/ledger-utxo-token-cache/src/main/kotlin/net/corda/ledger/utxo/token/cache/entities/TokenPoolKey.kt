package net.corda.ledger.utxo.token.cache.entities

import net.corda.data.ledger.utxo.token.selection.key.TokenPoolCacheKey

data class TokenPoolKey(
    val shortHolderId: String,
    val tokenType: String,
    val issuerHash: String,
    val notaryX500Name: String,
    val symbol: String
) {
    private val stringKey = "$shortHolderId-$tokenType-$issuerHash-$notaryX500Name-$symbol"

    init {
        // hack - the state manager key field is currently 255, we need to find a better solution to this
        // but for now we will throw and app devs will need to refactor there code to use short forms
        // for the unbound user defined values of type and symbol
        check(stringKey.length <= 255) {
            "The string version of this key can't exceed a length of 255 characters '$stringKey' (length = ${stringKey.length})"
        }
    }

    fun toAvro(): TokenPoolCacheKey {
        return TokenPoolCacheKey.newBuilder()
            .setShortHolderId(shortHolderId)
            .setTokenType(tokenType)
            .setIssuerHash(issuerHash)
            .setNotaryX500Name(notaryX500Name)
            .setSymbol(symbol)
            .build()
    }

    override fun toString(): String {
        return stringKey
    }
}
