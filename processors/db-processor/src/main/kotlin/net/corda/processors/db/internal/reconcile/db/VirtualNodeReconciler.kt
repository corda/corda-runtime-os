package net.corda.processors.db.internal.reconcile.db

import net.corda.db.connection.manager.DbConnectionManager
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.reconciliation.Reconciler
import net.corda.reconciliation.ReconcilerFactory
import net.corda.reconciliation.ReconcilerReader
import net.corda.reconciliation.ReconcilerWriter
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import org.slf4j.LoggerFactory
import java.util.stream.Stream

class VirtualNodeReconciler(
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    dbConnectionManager: DbConnectionManager,
    private val reconcilerFactory: ReconcilerFactory,
    private val reconcilerReader: ReconcilerReader<HoldingIdentity, VirtualNodeInfo>,
    private val reconcilerWriter: ReconcilerWriter<HoldingIdentity, VirtualNodeInfo>
) : ReconcilerWrapper {
    companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
        private val dependencies = setOf(
            LifecycleCoordinatorName.forComponent<DbConnectionManager>()
        )
    }

    private var dbReconciler: DbReconcilerReader<HoldingIdentity, VirtualNodeInfo>? = null
    private var reconciler: Reconciler? = null

    private val reconciliationContextFactory = {
        Stream.of(ClusterReconciliationContext(dbConnectionManager))
    }

    override fun close() {
        dbReconciler?.stop()
        dbReconciler = null
        reconciler?.stop()
        reconciler = null
    }

    override fun updateInterval(intervalMillis: Long) {
        log.debug("Reconciliation interval set to $intervalMillis ms")

        if (dbReconciler == null) {
            dbReconciler =
                DbReconcilerReader(
                    coordinatorFactory,
                    HoldingIdentity::class.java,
                    VirtualNodeInfo::class.java,
                    dependencies,
                    reconciliationContextFactory,
                    getAllVirtualNodesDBVersionedRecords
                ).also {
                    it.start()
                }
        }

        if (reconciler == null) {
            reconciler = reconcilerFactory.create(
                dbReader = dbReconciler!!,
                kafkaReader = reconcilerReader,
                writer = reconcilerWriter,
                keyClass = HoldingIdentity::class.java,
                valueClass = VirtualNodeInfo::class.java,
                reconciliationIntervalMs = intervalMillis
            ).also { it.start() }
        } else {
            log.info("Updating ${Reconciler::class.java.name}")
            reconciler!!.updateInterval(intervalMillis)
        }
    }
}
