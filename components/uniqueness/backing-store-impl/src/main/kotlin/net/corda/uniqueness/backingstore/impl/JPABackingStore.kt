package net.corda.uniqueness.backingstore.impl

/**
 * An in-memory backing store implementation, which does not persist any data to permanent storage,
 * and therefore loses all data when the instance of this class is destroyed.
 *
 * Intended to be used as a fake for testing purposes only - DO NOT USE ON A REAL SYSTEM
 */
import net.corda.uniqueness.backingstore.BackingStore
import net.corda.uniqueness.common.datamodel.*
import net.corda.v5.crypto.SecureHash

@Suppress("ForbiddenComment")
open class JPABackingStore : BackingStore {

    // Data persisted across different transactions
    private val persistedStateData =
        HashMap<UniquenessCheckInternalStateRef, UniquenessCheckInternalStateDetails>()
    private val persistedTxnData =
        HashMap<SecureHash, UniquenessCheckInternalTransactionDetails>()

    // Temporary cache of data created / updated during the current session
    private val sessionStateData =
        HashMap<UniquenessCheckInternalStateRef, UniquenessCheckInternalStateDetails>()
    private val sessionTxnData =
        HashMap<SecureHash, UniquenessCheckInternalTransactionDetails>()

    @Synchronized
    override fun session(block: (BackingStore.Session) -> Unit) = block(SessionImpl())

    @Synchronized
    override fun close() {
        sessionStateData.clear()
        sessionTxnData.clear()
    }

    protected open inner class SessionImpl : BackingStore.Session {

        @Synchronized
        override fun executeTransaction(
            block: (BackingStore.Session, BackingStore.Session.TransactionOps) -> Unit
        ) {
            block(this, TransactionOpsImpl())

            persistedStateData.putAll(sessionStateData)
            persistedTxnData.putAll(sessionTxnData)

            sessionStateData.clear()
            sessionTxnData.clear()
        }

        override fun getStateDetails(states: Collection<UniquenessCheckInternalStateRef>) =
            persistedStateData.filterKeys { states.contains(it) }

        override fun getTransactionDetails(txIds: Collection<SecureHash>) =
            persistedTxnData.filterKeys { txIds.contains(it) }

        protected open inner class TransactionOpsImpl : BackingStore.Session.TransactionOps {

            @Synchronized
            override fun createUnconsumedStates(
                stateRefs: Collection<UniquenessCheckInternalStateRef>
            ) {
                sessionStateData.putAll(stateRefs.map {
                    Pair(it, UniquenessCheckInternalStateDetails(it, null))
                })
            }

            @Synchronized
            override fun consumeStates(
                consumingTxId: SecureHash,
                stateRefs: Collection<UniquenessCheckInternalStateRef>
            ) {

                sessionStateData.putAll(stateRefs.map {
                    // Check session data first in case this has already been updated in this batch
                    val existingState = sessionStateData[it] ?: persistedStateData[it]

                    if (existingState == null) {
                        throw NoSuchElementException(
                            "Could not find existing unspent state for state ref $it"
                        )
                    } else if (existingState.consumingTxId != null &&
                        existingState.consumingTxId != consumingTxId
                    ) {
                        throw IllegalStateException(
                            "State ref $it has already been consumed by transaction $consumingTxId"
                        )
                    }

                    Pair(
                        existingState.stateRef,
                        UniquenessCheckInternalStateDetails(existingState.stateRef, consumingTxId)
                    )
                })
            }

            @Synchronized
            override fun commitTransactions(
                transactionDetails: Collection<Pair<
                        UniquenessCheckInternalRequest, UniquenessCheckInternalResult>>
            ) {
                sessionTxnData.putAll(transactionDetails.map {
                    Pair(
                        it.first.txId,
                        UniquenessCheckInternalTransactionDetails(it.first.txId, it.second)
                    )
                })
            }
        }
    }
}
