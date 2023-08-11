package net.corda.ledger.utxo.token.cache

import net.corda.data.ledger.utxo.token.selection.key.TokenPoolCacheKey
import net.corda.ledger.utxo.token.cache.entities.TokenPoolKey

internal object Helper {

    fun TokenPoolCacheKey.toDto() =
        TokenPoolKey(shortHolderId, tokenType, issuerHash, notaryX500Name, symbol)

}