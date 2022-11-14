package net.corda.utxo.token.sync.services.impl

import net.corda.db.connection.manager.DbConnectionManager
import net.corda.orm.JpaEntitiesSet
import net.corda.utxo.token.sync.entities.TokenPoolKeyRecord
import net.corda.utxo.token.sync.entities.TokenRecord
import net.corda.utxo.token.sync.entities.TokenRefRecord
import net.corda.utxo.token.sync.services.CurrentSyncState
import net.corda.utxo.token.sync.services.SyncConfiguration
import net.corda.utxo.token.sync.services.SyncService
import net.corda.utxo.token.sync.services.UtxoTokenRepository
import net.corda.v5.ledger.utxo.StateRef
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import java.time.Instant
import javax.persistence.EntityManager

class SyncServiceImpl(
    private val utxoTokenRepository: UtxoTokenRepository,
    private val virtualNodeInfoService: VirtualNodeInfoReadService,
    private val dbConnectionManager: DbConnectionManager,
    private val syncConfiguration: SyncConfiguration
) : SyncService {

    override fun validateUnspentTokens(
        holdingIdentity: HoldingIdentity,
        tokensToCheck: List<StateRef>
    ): List<TokenRecord> {
        return guardedEntityManagerCall(holdingIdentity) {
            utxoTokenRepository.getSpentTokensByRef(it, tokensToCheck)
        }
    }

    override fun getNextFullSyncBlock(state: CurrentSyncState): List<TokenRecord> {
        // When running a full synchronization we read blocks of unspent tokens (ordered by insert timestamp). When all
        // records are read the process is complete.
        val nextBlockOfRecords = guardedEntityManagerCall(state.holdingIdentity) {
            utxoTokenRepository.getUnspentTokensFromTimestamp(
                it,
                state.nextFullSyncBlockStartOffset,
                syncConfiguration.fullSyncBlockSize
            )
        }

        // If we have some records update the current state for the next block
        if (nextBlockOfRecords.any()) {
            state.completeFullSyncBlock(nextBlockOfRecords.last().lastModified)
        }

        // If we don't have a full block we can assume we have read all the records and can mark the full sync as
        // completed
        if (nextBlockOfRecords.size < syncConfiguration.fullSyncBlockSize) {
            state.fullSyncCompleted()
        }

        return nextBlockOfRecords
    }

    override fun getNextPeriodCheckBlock(state: CurrentSyncState): Map<TokenPoolKeyRecord, List<TokenRefRecord>> {
        // As we cache tokens by a pool key, we need to ensure we send tokens for pool we potentially have. If we don't
        // do this we could end up with some caches missing out on periodic checks, depending on where data sits in the
        // underlying DB table.
        // To do this we work out the set of pools we have for all unspent tokens and then send a set of state ref to
        // the associated pool cache for it to validate.
        val periodicCheckState = state.nextPeriodCheckBlockStartOffsets

        return guardedEntityManagerCall(state.holdingIdentity) { em ->
            val distinctTokenPools = utxoTokenRepository.getDistinctTokenPools(em)

            // Load a batch for every token pool, if we are tracking this pool already then use the next offset to read
            // from otherwise read from the beginning of the table.
            val records = distinctTokenPools.associateWith { tokenPoolKey ->
                utxoTokenRepository.getUnspentTokenRefsFromTimestamp(
                    em,
                    tokenPoolKey,
                    periodicCheckState.getOrDefault(tokenPoolKey, Instant.EPOCH),
                    syncConfiguration.periodCheckBlockSize
                )
            }

            // Update any tracked offsets, if we have read the all the records from the table then reset the offset back
            // to the beginning of the table.
            val newPeriodicCheckState = records.map {
                it.key to if (it.value.size < syncConfiguration.periodCheckBlockSize) {
                    Instant.EPOCH
                } else {
                    it.value.last().lastModified
                }
            }.toMap()

            state.updatePeriodicCheckState(newPeriodicCheckState)

            records
        }
    }

    private fun <T> guardedEntityManagerCall(holdingIdentity: HoldingIdentity, block: (EntityManager) -> T): T {
        val virtualNode = checkNotNull(virtualNodeInfoService.get(holdingIdentity)) {
            "Could not get virtual node for $holdingIdentity"
        }

        val emf = dbConnectionManager.createEntityManagerFactory(
            virtualNode.vaultDmlConnectionId,
            JpaEntitiesSet.create(virtualNode.vaultDmlConnectionId.toString(), setOf())
        )
        try {
            val em = emf.createEntityManager()
            return block(em)
        } finally {
            emf.close()
        }
    }
}
