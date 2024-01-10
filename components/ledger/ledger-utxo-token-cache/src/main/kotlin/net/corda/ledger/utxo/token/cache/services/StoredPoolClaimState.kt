package net.corda.ledger.utxo.token.cache.services

import net.corda.data.ledger.utxo.token.selection.state.TokenPoolCacheState
import net.corda.ledger.utxo.token.cache.entities.TokenPoolKey

data class StoredPoolClaimState(
    val dbVersion: Int,
    val key: TokenPoolKey,
    val poolState: TokenPoolCacheState
)
