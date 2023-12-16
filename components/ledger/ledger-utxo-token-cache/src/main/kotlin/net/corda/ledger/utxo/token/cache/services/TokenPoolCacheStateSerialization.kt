package net.corda.ledger.utxo.token.cache.services

import net.corda.data.ledger.utxo.token.selection.state.TokenPoolCacheState

interface TokenPoolCacheStateSerialization {

    fun serialize(state: TokenPoolCacheState): ByteArray

    fun deserialize(bytes: ByteArray): TokenPoolCacheState
}
