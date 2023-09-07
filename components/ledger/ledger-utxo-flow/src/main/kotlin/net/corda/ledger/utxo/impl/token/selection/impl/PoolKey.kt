package net.corda.ledger.utxo.impl.token.selection.impl

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonProperty
import net.corda.data.ledger.utxo.token.selection.key.TokenPoolCacheKey

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
data class PoolKey(
    @JsonProperty("shortHolderId")
    val shortHolderId: String,
    @JsonProperty("tokenType")
    val tokenType: String,
    @JsonProperty("issuerHash")
    val issuerHash: String,
    @JsonProperty("notaryX500Name")
    val notaryX500Name: String,
    @JsonProperty("symbol")
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
