package net.corda.ledger.utxo.token.cache.services

import net.corda.ledger.utxo.token.cache.entities.TokenPoolKey

interface ClaimStateStoreFactory {
    fun create(key: TokenPoolKey): ClaimStateStore
}
