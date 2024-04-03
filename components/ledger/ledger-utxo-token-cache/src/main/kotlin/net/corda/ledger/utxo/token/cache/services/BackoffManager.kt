package net.corda.ledger.utxo.token.cache.services

import net.corda.ledger.utxo.token.cache.entities.TokenPoolKey

interface BackoffManager {

    /**
     * Updates the state for the given TokenPoolKey
     */
    fun update(poolKey: TokenPoolKey)

    /**
     * Returns true when the backoff time has not expired for the given TokenPoolKey
     */
    fun backoff(poolKey: TokenPoolKey): Boolean
}