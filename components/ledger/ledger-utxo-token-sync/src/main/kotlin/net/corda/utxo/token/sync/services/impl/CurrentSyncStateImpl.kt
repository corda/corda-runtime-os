package net.corda.utxo.token.sync.services.impl

import net.corda.data.ledger.utxo.token.selection.data.TokenFullSyncState
import net.corda.data.ledger.utxo.token.selection.data.TokenPoolPeriodicSyncState
import net.corda.data.ledger.utxo.token.selection.state.TokenSyncMode
import net.corda.data.ledger.utxo.token.selection.state.TokenSyncState
import net.corda.utilities.time.Clock
import net.corda.utxo.token.sync.converters.EntityConverter
import net.corda.utxo.token.sync.entities.CurrentSyncStateType
import net.corda.utxo.token.sync.entities.TokenPoolKeyRecord
import net.corda.utxo.token.sync.services.CurrentSyncState
import net.corda.utxo.token.sync.services.SyncConfiguration
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toCorda
import java.time.Instant

class CurrentSyncStateImpl(
    private val state: TokenSyncState,
    private val clock: Clock,
    private val entityConverter: EntityConverter,
    private val syncConfiguration: SyncConfiguration
) : CurrentSyncState {

    override val mode: CurrentSyncStateType
        get() = convertMode(state.mode)

    override val holdingIdentity: HoldingIdentity
        get() = state.holdingIdentity.toCorda()

    override val nextFullSyncBlockStartOffset: Instant
        get()  = state.fullSyncState.nextBlockStartOffset

    override val nextPeriodCheckBlockStartOffsets: Map<TokenPoolKeyRecord, Instant>
        get() = state.periodicSyncState.map {
           entityConverter.toTokenPoolKey(it.poolKey) to it.nextBlockStartOffset
        }.toMap()

    override fun completeFullSyncBlock(nextBlockStartOffset: Instant)  {
        state.fullSyncState.blocksCompleted++
        state.fullSyncState.nextBlockStartOffset = nextBlockStartOffset
        state.fullSyncState.lastBlockCompletedTimestamp = clock.instant()
        state.nextWakeup = clock.instant()
    }

    override fun fullSyncCompleted() {
        setPeriodicCheckState()
    }

    override fun startFullSync() {
        state.mode = TokenSyncMode.FULL_SYNC
        state.fullSyncState = TokenFullSyncState().apply {
            this.nextBlockStartOffset = Instant.EPOCH
            this.startedTimestamp = clock.instant()
            this.recordsCompleted = 0
            this.lastBlockCompletedTimestamp = Instant.EPOCH
            this.blocksCompleted = 0
        }
    }

    override fun updatePeriodicCheckState(newOffsets: Map<TokenPoolKeyRecord, Instant>) {
        state.periodicSyncState = newOffsets.map { newOffset ->
            TokenPoolPeriodicSyncState.newBuilder()
                .setPoolKey(entityConverter.toTokenPoolKey(holdingIdentity, newOffset.key))
                .setNextBlockStartOffset(newOffset.value)
                .build()
        }
        setPeriodicCheckState()
    }

    override fun getLastFullSyncCompletedTimestamp(): Instant {
        return state.fullSyncState.lastBlockCompletedTimestamp
    }

    override fun toAvro(): TokenSyncState {
        return state
    }

    private fun convertMode(mode: TokenSyncMode): CurrentSyncStateType {
        return if (mode == TokenSyncMode.FULL_SYNC) {
            CurrentSyncStateType.FULL_SYNC
        } else {
            CurrentSyncStateType.PERIODIC_CHECK
        }
    }

    private fun setPeriodicCheckState(){
        state.mode = TokenSyncMode.PERIODIC_CHECK
        state.nextWakeup = clock.instant().plusSeconds(syncConfiguration.minDelayBeforeNextPeriodicSync)
    }
}
