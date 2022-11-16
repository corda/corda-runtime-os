package net.corda.processors.db.internal.reconcile.db

import net.corda.db.connection.manager.DbConnectionManager
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.processors.db.internal.reconcile.db.DbReconcilerReader.GetRecordsErrorEvent
import net.corda.reconciliation.ReconcilerReader
import net.corda.reconciliation.VersionedRecord
import net.corda.utilities.VisibleForTesting
import org.slf4j.LoggerFactory
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory

/**
 * A [DbReconcilerReader] for database data that map to compacted topics data. This class is a [Lifecycle] and therefore
 * has its own lifecycle. What's special about it is, when its public API [getAllVersionedRecords] method gets called,
 * if an error occurs during the call the exception gets captured and its lifecycle state gets notified with a
 * [GetRecordsErrorEvent]. Then depending on if the exception is a transient or not its state should be taken to
 * [LifecycleStatus.DOWN] or [LifecycleStatus.ERROR].
 */
class DbReconcilerReader<K : Any, V : Any>(
    coordinatorFactory: LifecycleCoordinatorFactory,
    private val dbConnectionManager: DbConnectionManager,
    keyClass: Class<K>,
    valueClass: Class<V>,
    private val doGetAllVersionedRecords: (EntityManager) -> List<VersionedRecord<K, V>>
) : ReconcilerReader<K, V>, Lifecycle {

    internal val name = "${DbReconcilerReader::class.java.name}<${keyClass.name}, ${valueClass.name}>"

    private val logger = LoggerFactory.getLogger(name)

    override val lifecycleCoordinatorName = LifecycleCoordinatorName(name)

    private val coordinator = coordinatorFactory.createCoordinator(lifecycleCoordinatorName, ::processEvent)

    private var dbConnectionManagerRegistration: RegistrationHandle? = null

    @VisibleForTesting
    internal var entityManagerFactory: EntityManagerFactory? = null

    @VisibleForTesting
    internal fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> onStartEvent(coordinator)
            is RegistrationStatusChangeEvent -> onRegistrationStatusChangeEvent(event, coordinator)
            is GetRecordsErrorEvent -> onGetRecordsErrorEvent(event, coordinator)
            is StopEvent -> onStopEvent()
        }
    }

    private fun onStartEvent(coordinator: LifecycleCoordinator) {
        dbConnectionManagerRegistration?.close()
        dbConnectionManagerRegistration = coordinator.followStatusChangesByName(
            setOf(
                LifecycleCoordinatorName.forComponent<DbConnectionManager>()
            )
        )
    }

    private fun onStopEvent() {
        closeResources()
    }

    private fun onRegistrationStatusChangeEvent(event: RegistrationStatusChangeEvent, coordinator: LifecycleCoordinator) {
        if (event.status == LifecycleStatus.UP) {
            entityManagerFactory = dbConnectionManager.getClusterEntityManagerFactory()
            coordinator.updateStatus(LifecycleStatus.UP)
        } else {
            coordinator.updateStatus(event.status)
            closeResources()
        }
    }

    @Suppress("unused_parameter")
    private fun onGetRecordsErrorEvent(event: GetRecordsErrorEvent, coordinator: LifecycleCoordinator) {
        logger.warn("Processing a ${GetRecordsErrorEvent::class.java.name}")
        // TODO based on exception determine component's next state i.e if transient exception or not -> DOWN or ERROR
//        when (event.exception) {
//        }
        // For now just stopping it with errored false
        coordinator.postEvent(StopEvent())
    }

    /**
     * [getAllVersionedRecords] is public API for this service i.e. it can be called by other lifecycle services,
     * therefore it must be guarded from thrown exceptions. No exceptions should escape from it, instead an
     * event should be scheduled notifying the service about the error. Then the calling service which should
     * be following this service will get notified of this service's stop event as well.
     */
    override fun getAllVersionedRecords(): List<VersionedRecord<K, V>>? {
        return try {
            val em = entityManagerFactory!!.createEntityManager()
            val currentTransaction = em.transaction
            return try {
                currentTransaction.begin()
                doGetAllVersionedRecords(em)
            } finally {
                // This class only have access to this em and transaction. This is a read only transaction,
                // only used for making streaming DB data possible.
                currentTransaction.rollback()
                em.close()
            }
        } catch (e: Exception) {
            logger.warn("Error while retrieving records for reconciliation", e)
            coordinator.postEvent(GetRecordsErrorEvent(e))
            null
        }
    }

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        coordinator.start()
    }

    override fun stop() {
        coordinator.stop()
        closeResources()
    }

    private fun closeResources() {
        dbConnectionManagerRegistration?.close()
        dbConnectionManagerRegistration = null
        entityManagerFactory = null
    }

    private class GetRecordsErrorEvent(val exception: Exception) : LifecycleEvent
}
