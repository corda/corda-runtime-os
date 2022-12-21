package net.corda.uniqueness.backingstore.impl.fake

/**
 * An in-memory backing store implementation, which does not persist any data to permanent storage,
 * and therefore loses all data when the instance of this class is destroyed.
 *
 * Intended to be used as a fake for testing purposes only - DO NOT USE ON A REAL SYSTEM
 */
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.uniqueness.backingstore.BackingStore
import net.corda.uniqueness.datamodel.impl.UniquenessCheckStateDetailsImpl
import net.corda.uniqueness.datamodel.internal.UniquenessCheckTransactionDetailsInternal
import net.corda.uniqueness.datamodel.internal.UniquenessCheckRequestInternal
import net.corda.v5.application.uniqueness.model.UniquenessCheckResult
import net.corda.v5.base.util.contextLogger
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.uniqueness.data.UniquenessCheckStateDetails
import net.corda.virtualnode.HoldingIdentity
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

    // Holding id for the current session
    private lateinit var activeHoldingIdentity: HoldingIdentity

    // Data persisted across different transactions, partitioned on holding id
    private val persistedStateData =
        HashMap<HoldingIdentity,
                HashMap<StateRef, UniquenessCheckStateDetails>>()
    private val persistedTxnData =
        HashMap<HoldingIdentity,
                HashMap<SecureHash, UniquenessCheckTransactionDetailsInternal>>()

    // Temporary cache of data created / updated during the current session
    private val sessionStateData =
        HashMap<StateRef, UniquenessCheckStateDetails>()
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

    override fun start() {
        log.info("Backing store starting.")
        lifecycleCoordinator.start()
    }

    override fun stop() {
        log.info("Backing store stopping.")
        lifecycleCoordinator.stop()
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

        override fun getStateDetails(states: Collection<StateRef>) =
            persistedStateData[activeHoldingIdentity]
                ?.filterKeys { states.contains(it) } ?: emptyMap()

        override fun getTransactionDetails(txIds: Collection<SecureHash>) =
            persistedTxnData[activeHoldingIdentity]
                ?.filterKeys { txIds.contains(it) } ?: emptyMap()

        protected open inner class TransactionOpsImpl : BackingStore.Session.TransactionOps {

            @Synchronized
            override fun createUnconsumedStates(
                stateRefs: Collection<StateRef>
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
                stateRefs: Collection<StateRef>
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
