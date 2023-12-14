package net.corda.processors.db.internal.reconcile.db

import java.util.stream.Stream
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.reconciliation.ReconcilerReader
import net.corda.reconciliation.VersionedRecord
import org.slf4j.LoggerFactory

/**
 * A [DbReconcilerReader] for database data that map to compacted topics data.
 *
 * This class has its own lifecycle. What's special about it is, when its public API [getAllVersionedRecords] method gets called,
 * if an error occurs during the call the exception needs to get captured and its lifecycle to be notified with a
 * [GetRecordsErrorEvent] (depending on if the exception is a transient error or not its lifecycle should
 * be set to [LifecycleStatus.DOWN] or [LifecycleStatus.ERROR]). And then the exception needs to be re-thrown
 * for the reconciler to be notified immediately.
 */
@Suppress("LongParameterList")
class DbReconcilerReader<K : Any, V : Any>(
    coordinatorFactory: LifecycleCoordinatorFactory,
    keyClass: Class<K>,
    valueClass: Class<V>,
    private val dependencies: Set<LifecycleCoordinatorName>,
    private val reconciliationContextFactory: () -> Stream<out ReconciliationContext>,
    private val doGetAllVersionedRecords: (ReconciliationContext) -> Stream<VersionedRecord<K, V>>
) : ReconcilerReader<K, V>, Lifecycle {

    companion object {
        private const val REGISTRATION = "REGISTRATION"
    }

    internal val name = "${DbReconcilerReader::class.java.name}<${keyClass.name}, ${valueClass.name}>"

    private val logger = LoggerFactory.getLogger(name)

    override val lifecycleCoordinatorName = LifecycleCoordinatorName(name)

    private val coordinator = coordinatorFactory.createCoordinator(
        lifecycleCoordinatorName,
        ::processEvent
    )

    private fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> onStartEvent(coordinator)
            is RegistrationStatusChangeEvent -> onRegistrationStatusChangeEvent(event, coordinator)
        }
    }

    private fun onStartEvent(coordinator: LifecycleCoordinator) {
         coordinator.createManagedResource(REGISTRATION) { coordinator.followStatusChangesByName(dependencies) }
    }

    private fun onRegistrationStatusChangeEvent(
        event: RegistrationStatusChangeEvent,
        coordinator: LifecycleCoordinator,
    ) {
        coordinator.updateStatus(event.status)
    }

    /**
     * [getAllVersionedRecords] is public API for this service i.e. it can be called by other lifecycle services,
     * therefore it must be guarded against thrown exceptions. No exceptions should escape from it, instead an
     * event should be scheduled notifying the service about the error. Then the calling service which should
     * be following this service will get notified of this service's stop event as well.
     */
    @Suppress("SpreadOperator")
    override fun getAllVersionedRecords(): Stream<VersionedRecord<K, V>> {
        val streamOfStreams: Stream<Stream<VersionedRecord<K, V>>> = reconciliationContextFactory().map { context ->
            try {
                val currentTransaction = context.getOrCreateEntityManager().transaction
                currentTransaction.begin()
                doGetAllVersionedRecords(context).onClose {
                    // This class only have access to this em and transaction. This is a read only transaction,
                    // only used for making streaming DB data possible.
                    currentTransaction.rollback()
                    context.close()
                }
            } catch (e: Exception) {
                logger.warn("Error while retrieving DB records for reconciliation for ${context.prettyPrint()}", e)
                context.close()
                Stream.empty()
            }
        }
        return streamOfStreams.flatMap { i -> i }
    }

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        coordinator.start()
    }

    override fun stop() {
        coordinator.stop()
    }
}