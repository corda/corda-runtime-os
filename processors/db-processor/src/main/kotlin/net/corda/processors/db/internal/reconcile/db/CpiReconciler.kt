package net.corda.processors.db.internal.reconcile.db

import net.corda.db.connection.manager.DbConnectionManager
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.packaging.core.CpiMetadata
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.processors.db.internal.reconcile.db.ReconciliationContext.ClusterReconciliationContext
import net.corda.reconciliation.Reconciler
import net.corda.reconciliation.ReconcilerFactory
import net.corda.reconciliation.ReconcilerReader
import net.corda.reconciliation.ReconcilerWriter
import net.corda.v5.base.util.contextLogger
import javax.persistence.EntityManagerFactory

class CpiReconciler(
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    private val dbConnectionManager: DbConnectionManager,
    private val reconcilerFactory: ReconcilerFactory,
    private val reconcilerReader: ReconcilerReader<CpiIdentifier, CpiMetadata>,
    private val reconcilerWriter: ReconcilerWriter<CpiIdentifier, CpiMetadata>
) : ReconcilerWrapper {
    companion object {
        private val log = contextLogger()
        private val dependencies = setOf(
            LifecycleCoordinatorName.forComponent<DbConnectionManager>()
        )
    }

    private var dbReconciler: DbReconcilerReader<CpiIdentifier, CpiMetadata>? = null
    private var reconciler: Reconciler? = null

    private val entityManagerFactory: EntityManagerFactory
        get() = requireNotNull(_entityManagerFactory) {
            "An attempt was made to try access an entity manager factory for config " +
                    "reconciliation before it was initialized."
        }

    private var _entityManagerFactory: EntityManagerFactory? = null

    private val reconciliationContextFactory = {
        listOf(ClusterReconciliationContext(entityManagerFactory))
    }

    private fun onStatusUp() {
        _entityManagerFactory = dbConnectionManager.getClusterEntityManagerFactory()
    }

    private fun onStatusDown() {
        _entityManagerFactory?.close()
        _entityManagerFactory = null
    }

    override fun close() {
        dbReconciler?.stop()
        dbReconciler = null
        reconciler?.stop()
        reconciler = null
    }

    override fun updateInterval(intervalMillis: Long) {
        log.debug("Cpi info reconciliation interval set to $intervalMillis ms")

        if (dbReconciler == null) {
            dbReconciler =
                DbReconcilerReader(
                    coordinatorFactory,
                    CpiIdentifier::class.java,
                    CpiMetadata::class.java,
                    dependencies,
                    reconciliationContextFactory,
                    getAllCpiInfoDBVersionedRecords,
                    ::onStatusUp,
                    ::onStatusDown
                ).also {
                    it.start()
                }
        }

        if (reconciler == null) {
            reconciler = reconcilerFactory.create(
                dbReader = dbReconciler!!,
                kafkaReader = reconcilerReader,
                writer = reconcilerWriter,
                keyClass = CpiIdentifier::class.java,
                valueClass = CpiMetadata::class.java,
                reconciliationIntervalMs = intervalMillis
            ).also { it.start() }
        } else {
            log.info("Updating Cpi Info ${Reconciler::class.java.name}")
            reconciler!!.updateInterval(intervalMillis)
        }
    }
}
