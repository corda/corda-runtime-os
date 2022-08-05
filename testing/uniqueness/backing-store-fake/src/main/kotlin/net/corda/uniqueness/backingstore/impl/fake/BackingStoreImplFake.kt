package net.corda.uniqueness.backingstore.impl.fake

/**
 * An in-memory backing store implementation, which does not persist any data to permanent storage,
 * and therefore loses all data when the instance of this class is destroyed.
 *
 * Intended to be used as a fake for testing purposes only - DO NOT USE ON A REAL SYSTEM
 */
import net.corda.lifecycle.*
import net.corda.uniqueness.backingstore.BackingStore
import net.corda.uniqueness.common.datamodel.*
import net.corda.v5.base.util.contextLogger
import net.corda.v5.crypto.SecureHash
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.propertytypes.ServiceRanking
import org.slf4j.Logger

@ServiceRanking(Int.MAX_VALUE)
@Component(service = [BackingStore::class])
@Suppress("ForbiddenComment")
open class BackingStoreImplFake @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory
) : BackingStore {

    private companion object {
        private val log: Logger = contextLogger()
    }

    private val lifecycleCoordinator: LifecycleCoordinator = coordinatorFactory
        .createCoordinator<BackingStore>(::eventHandler)

    override val isRunning: Boolean
        get() = lifecycleCoordinator.isRunning

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

    override fun start() {
        log.info("Backing store starting.")
        lifecycleCoordinator.start()
    }

    override fun stop() {
        log.info("Backing store stopping.")
        lifecycleCoordinator.stop()
    }

    @Synchronized
    override fun close() {
        sessionStateData.clear()
        sessionTxnData.clear()
        stop()
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

    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        log.info("Backing store received event $event.")
        when (event) {
            is StartEvent -> {
                log.info("Backing store is UP")
                coordinator.updateStatus(LifecycleStatus.UP)
            }
            is StopEvent -> {
                log.info("Backing store is DOWN")
                coordinator.updateStatus(LifecycleStatus.DOWN)
            }
            is RegistrationStatusChangeEvent -> {
                log.info("Backing store is ${event.status}")
                coordinator.updateStatus(event.status)
            }
            else -> {
                log.warn("Unexpected event $event!")
            }
        }
    }
}
