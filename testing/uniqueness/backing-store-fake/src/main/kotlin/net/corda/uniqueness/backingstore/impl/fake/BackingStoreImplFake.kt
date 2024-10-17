package net.corda.uniqueness.backingstore.impl.fake

/**
 * An in-memory backing store implementation, which does not persist any data to permanent storage,
 * and therefore loses all data when the instance of this class is destroyed.
 *
 * Intended to be used as a fake for testing purposes only - DO NOT USE ON A REAL SYSTEM
 */
import net.corda.ledger.libs.uniqueness.backingstore.BackingStore
import net.corda.uniqueness.datamodel.impl.UniquenessCheckStateDetailsImpl
import net.corda.uniqueness.datamodel.internal.UniquenessCheckRequestInternal
import net.corda.uniqueness.datamodel.internal.UniquenessCheckTransactionDetailsInternal
import net.corda.v5.application.uniqueness.model.UniquenessCheckResult
import net.corda.v5.application.uniqueness.model.UniquenessCheckStateDetails
import net.corda.v5.application.uniqueness.model.UniquenessCheckStateRef
import net.corda.v5.crypto.SecureHash

@Suppress("ForbiddenComment")
open class BackingStoreImplFake : BackingStore {

    private lateinit var activeHoldingIdentity: HoldingIdentity

    // Data persisted across different transactions, partitioned on holding id
    private val persistedStateData =
        HashMap<HoldingIdentity,
                HashMap<UniquenessCheckStateRef, UniquenessCheckStateDetails>>()
    private val persistedTxnData =
        HashMap<HoldingIdentity,
                HashMap<SecureHash, UniquenessCheckTransactionDetailsInternal>>()

    // Temporary cache of data created / updated during the current session
    private val sessionStateData =
        HashMap<UniquenessCheckStateRef, UniquenessCheckStateDetails>()
    private val sessionTxnData =
        HashMap<SecureHash, UniquenessCheckTransactionDetailsInternal>()

    @Synchronized
    override fun session(
        holdingIdentity: HoldingIdentity,
        block: (BackingStore.Session) -> Unit
    ) {
        activeHoldingIdentity = holdingIdentity
        block(SessionImpl())
    }

    protected open inner class SessionImpl : BackingStore.Session {

        @Synchronized
        override fun executeTransaction(
            block: (BackingStore.Session, BackingStore.Session.TransactionOps) -> Unit
        ) {
            block(this, TransactionOpsImpl())

            persistedStateData
                .getOrPut(activeHoldingIdentity) { HashMap() }
                .putAll(sessionStateData)
            persistedTxnData
                .getOrPut(activeHoldingIdentity) { HashMap() }
                .putAll(sessionTxnData)

            sessionStateData.clear()
            sessionTxnData.clear()
        }

        override fun getStateDetails(states: Collection<UniquenessCheckStateRef>) =
            persistedStateData[activeHoldingIdentity]
                ?.filterKeys { states.contains(it) } ?: emptyMap()

        override fun getTransactionDetails(txIds: Collection<SecureHash>) =
            persistedTxnData[activeHoldingIdentity]
                ?.filterKeys { txIds.contains(it) } ?: emptyMap()

        protected open inner class TransactionOpsImpl : BackingStore.Session.TransactionOps {

            @Synchronized
            override fun createUnconsumedStates(
                stateRefs: Collection<UniquenessCheckStateRef>
            ) {
                sessionStateData.putAll(
                    stateRefs.map {
                        Pair(it, UniquenessCheckStateDetailsImpl(it, null))
                    }
                )
            }

            @Synchronized
            override fun consumeStates(
                consumingTxId: SecureHash,
                stateRefs: Collection<UniquenessCheckStateRef>
            ) {
                sessionStateData.putAll(
                    stateRefs.map {
                        // Check session data first in case this has already been updated in this batch
                        val existingState =
                            sessionStateData[it] ?:
                            persistedStateData[activeHoldingIdentity]?.get(it)

                        if (existingState == null) {
                            throw NoSuchElementException(
                                "Could not find existing unspent state for state ref $it"
                            )
                        } else if (existingState.consumingTxId != null &&
                            existingState.consumingTxId != consumingTxId
                        ) {
                            @Suppress("UseCheckOrError")
                            // TODO: Revisit this suppression
                            throw IllegalStateException(
                                "State ref $it has already been consumed by transaction $consumingTxId"
                            )
                        }

                        Pair(
                            existingState.stateRef,
                            UniquenessCheckStateDetailsImpl(existingState.stateRef, consumingTxId)
                        )
                    }
                )
            }

            @Synchronized
            override fun commitTransactions(
                transactionDetails: Collection<Pair<UniquenessCheckRequestInternal, UniquenessCheckResult>>
            ) {
                sessionTxnData.putAll(
                    transactionDetails.map {
                        Pair(
                            it.first.txId,
                            UniquenessCheckTransactionDetailsInternal(it.first.txId, it.second)
                        )
                    }
                )
            }
        }
    }
}
