package net.corda.utxo.token.sync.services

import net.corda.data.ledger.utxo.token.selection.state.TokenSyncState
import net.corda.utxo.token.sync.entities.CurrentSyncStateType
import net.corda.utxo.token.sync.entities.TokenPoolKeyRecord
import net.corda.virtualnode.HoldingIdentity
import java.time.Instant

/**
 * The [CurrentSyncState] represents the current state of a virtual nodes UTXO token cache synchronization
 * process.
 *
 * @property mode The current synchronisation mode in operation
 * @property holdingIdentity The holding identity the state is for
 * @property nextFullSyncBlockStartOffset The start point to read the next block of records for a full synchronization
 * @property nextPeriodCheckBlockStartOffsets  The map of start points to read the next block of records per token pool
 * for a periodic check.
 */
interface CurrentSyncState {

    val mode: CurrentSyncStateType

    val holdingIdentity: HoldingIdentity

    val nextFullSyncBlockStartOffset: Instant

    val nextPeriodCheckBlockStartOffsets: Map<TokenPoolKeyRecord, Instant>

    /**
     * Records the completion of a block
     *
     * @param nextBlockStartOffset The starting record timestamp for the next block to be processed
     */
    fun completeFullSyncBlock(nextBlockStartOffset: Instant)

    /**
     * Marks the full synchronization process as completed and switches back to a periodic check.
     */
    fun fullSyncCompleted()

    /**
     * Initializes the state for a full synchronization
     */
    fun startFullSync()

    /**
     * Updates the state with a new set of periodic check block start offsets
     *
     * @param newOffsets A map of the new record start offset timestamp for each token pool
     */
    fun updatePeriodicCheckState(newOffsets: Map<TokenPoolKeyRecord, Instant>)

    /**
     * Gets a UTC timestamp of when the last full sync operation completed successfully
     *
     * @return The current UTC timestamp of the last full sync completion, if no full sync has been completed
     * [Instant.MIN] is returned
     */
    fun getLastFullSyncCompletedTimestamp(): Instant

    /**
     * Gets the Avro representation of the current sync
     */
    fun toAvro(): TokenSyncState
}


