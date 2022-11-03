package net.corda.processors.db.internal.reconcile.db

import net.corda.db.connection.manager.DbConnectionManager
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.processors.db.internal.reconcile.db.DbReconcilerReaderComponent.GetRecordsErrorEvent
import net.corda.reconciliation.VersionedRecord
import java.util.stream.Stream
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory

/**
 * A [DbReconcilerReader] for database data that map to compacted topics data. This class is a [Lifecycle] and therefore
 * has its own lifecycle. What's special about it is, when its public API [getAllVersionedRecords] method gets called,
 * if an error occurs during the call the exception gets captured and its lifecycle state gets notified with a
 * [GetRecordsErrorEvent]. Then depending on if the exception is a transient or not its state should be taken to
 * [LifecycleStatus.DOWN] or [LifecycleStatus.ERROR].
 */
class ClusterDbReconcilerReader<K : Any, V : Any>(
    coordinatorFactory: LifecycleCoordinatorFactory,
    private val dbConnectionManager: DbConnectionManager,
    keyClass: Class<K>,
    valueClass: Class<V>,
    private val doGetAllVersionedRecords: (EntityManager) -> Stream<VersionedRecord<K, V>>
) : DbReconcilerReaderComponent<K, V>(
    coordinatorFactory
) {

    override val name = "${ClusterDbReconcilerReader::class.java.name}<${keyClass.name}, ${valueClass.name}>"
    override val dependencies = setOf(
        LifecycleCoordinatorName.forComponent<DbConnectionManager>()
    )

    private var entityManagerFactory: EntityManagerFactory? = null

    override fun onStatusUp() {
        entityManagerFactory = dbConnectionManager.getClusterEntityManagerFactory()
    }

    override fun onStatusDown() {
        entityManagerFactory = null
    }

    /**
     * [getAllVersionedRecords] is public API for this service i.e. it can be called by other lifecycle services,
     * therefore it must be guarded from thrown exceptions. No exceptions should escape from it, instead an
     * event should be scheduled notifying the service about the error. Then the calling service which should
     * be following this service will get notified of this service's stop event as well.
     */
    override fun getAllVersionedRecords(): Stream<VersionedRecord<K, V>>? {
        return try {
            val em = entityManagerFactory!!.createEntityManager()
            val currentTransaction = em.transaction
            currentTransaction.begin()
            doGetAllVersionedRecords(em).onClose {
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
}