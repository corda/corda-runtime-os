package net.corda.utxo.token.sync.converters.impl

import net.corda.data.identity.HoldingIdentity
import net.corda.data.ledger.utxo.token.selection.data.TokenUnspentSyncCheck
import net.corda.data.ledger.utxo.token.selection.key.TokenPoolCacheKey
import net.corda.data.ledger.utxo.token.selection.state.TokenSyncState
import net.corda.utilities.time.Clock
import net.corda.utxo.token.sync.converters.EntityConverter
import net.corda.utxo.token.sync.entities.FullSyncRequest
import net.corda.utxo.token.sync.entities.TokenPoolKeyRecord
import net.corda.utxo.token.sync.entities.UnspentSyncCheckRequest
import net.corda.utxo.token.sync.entities.WakeUpSyncRequest
import net.corda.utxo.token.sync.services.CurrentSyncState
import net.corda.utxo.token.sync.services.SyncConfiguration
import net.corda.utxo.token.sync.services.impl.CurrentSyncStateImpl
import net.corda.v5.ledger.utxo.StateRef
import net.corda.virtualnode.toCorda

class EntityConverterImpl(
    private val clock: Clock,
    private val syncConfiguration: SyncConfiguration
) : EntityConverter {

    override fun toCurrentState(state: TokenSyncState): CurrentSyncState {
        return CurrentSyncStateImpl(state, clock, this, syncConfiguration)
    }

    override fun toWakeUp(key: HoldingIdentity): WakeUpSyncRequest {
        return WakeUpSyncRequest(key.toCorda())
    }

    override fun toFullSyncRequest(key: HoldingIdentity): FullSyncRequest {
        return FullSyncRequest(key.toCorda())
    }

    override fun toUnspentSyncCheck(
        key: HoldingIdentity,
        unspentSyncCheck: TokenUnspentSyncCheck
    ): UnspentSyncCheckRequest {
        return UnspentSyncCheckRequest(key.toCorda(), unspentSyncCheck.tokenRefs.map { StateRef.parse(it) })
    }

    override fun toTokenPoolKey(
        holdingIdentity: net.corda.virtualnode.HoldingIdentity,
        tokenPoolKey: TokenPoolKeyRecord
    ): TokenPoolCacheKey {
        return TokenPoolCacheKey().apply {
            this.shortHolderId = holdingIdentity.shortHash.toString()
            this.tokenType = tokenPoolKey.tokenType
            this.issuerHash = tokenPoolKey.issuerHash
            this.notaryX500Name = tokenPoolKey.notaryX500Name
            this.symbol = tokenPoolKey.symbol
        }
    }

    override fun toTokenPoolKey(tokenPoolKey: TokenPoolCacheKey): TokenPoolKeyRecord {
        return TokenPoolKeyRecord(
            tokenPoolKey.tokenType,
            tokenPoolKey.issuerHash,
            tokenPoolKey.notaryX500Name,
            tokenPoolKey.symbol
        )
    }
}
