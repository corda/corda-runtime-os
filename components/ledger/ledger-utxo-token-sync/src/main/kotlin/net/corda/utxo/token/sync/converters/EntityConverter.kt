package net.corda.utxo.token.sync.converters

import net.corda.data.identity.HoldingIdentity
import net.corda.data.ledger.utxo.token.selection.data.TokenUnspentSyncCheck
import net.corda.data.ledger.utxo.token.selection.key.TokenPoolCacheKey
import net.corda.data.ledger.utxo.token.selection.state.TokenSyncState
import net.corda.utxo.token.sync.services.CurrentSyncState
import net.corda.utxo.token.sync.entities.FullSyncRequest
import net.corda.utxo.token.sync.entities.TokenPoolKeyRecord
import net.corda.utxo.token.sync.entities.UnspentSyncCheckRequest
import net.corda.utxo.token.sync.entities.WakeUpSyncRequest

interface EntityConverter {
    fun toCurrentState(state: TokenSyncState): CurrentSyncState

    fun toWakeUp(key: HoldingIdentity): WakeUpSyncRequest

    fun toFullSyncRequest(key: HoldingIdentity): FullSyncRequest

    fun toUnspentSyncCheck(key: HoldingIdentity, unspentSyncCheck: TokenUnspentSyncCheck): UnspentSyncCheckRequest

    fun toTokenPoolKey(
        holdingIdentity: net.corda.virtualnode.HoldingIdentity,
        tokenPoolKey: TokenPoolKeyRecord
    ): TokenPoolCacheKey

    fun toTokenPoolKey(tokenPoolKey: TokenPoolCacheKey): TokenPoolKeyRecord
}

