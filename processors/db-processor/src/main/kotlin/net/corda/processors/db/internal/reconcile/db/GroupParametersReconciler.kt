package net.corda.processors.db.internal.reconcile.db

import net.corda.data.CordaAvroSerializationFactory
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.membership.lib.GroupParametersFactory
import net.corda.orm.JpaEntitiesRegistry
import net.corda.processors.db.internal.reconcile.db.query.GroupParametersReconciliationQuery
import net.corda.reconciliation.Reconciler
import net.corda.reconciliation.ReconcilerFactory
import net.corda.reconciliation.ReconcilerReader
import net.corda.reconciliation.ReconcilerWriter
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.v5.membership.GroupParameters
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.read.VirtualNodeInfoReadService

/**
 * Reconciler for handling reconciliation between each vnode vault database on the cluster
 * and the compacted kafka topic for the group parameters.
 */
@Suppress("LongParameterList")
class GroupParametersReconciler(
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    private val dbConnectionManager: DbConnectionManager,
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
    private val jpaEntitiesRegistry: JpaEntitiesRegistry,
    private val cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    private val groupParametersFactory: GroupParametersFactory,
    private val reconcilerFactory: ReconcilerFactory,
    private val reconcilerReader: ReconcilerReader<HoldingIdentity, GroupParameters>,
    private val reconcilerWriter: ReconcilerWriter<HoldingIdentity, GroupParameters>
) : ReconcilerWrapper {
    private companion object {
        val logger = contextLogger()
    }

    private var dbReconciler: DbReconcilerReaderWrapper<HoldingIdentity, GroupParameters>? = null
    private var reconciler: Reconciler? = null

    override fun close() {
        dbReconciler?.stop()
        dbReconciler = null
    }

    override fun updateInterval(intervalMillis: Long) {
        logger.debug { "Group parameters reconciliation interval set to $intervalMillis ms" }

        if (dbReconciler == null) {
            dbReconciler = DbReconcilerReaderWrapper(
                coordinatorFactory,
                VirtualNodeVaultDbReconcilerReader(
                    virtualNodeInfoReadService,
                    dbConnectionManager,
                    jpaEntitiesRegistry,
                    GroupParametersReconciliationQuery(
                        cordaAvroSerializationFactory,
                        groupParametersFactory
                    ),
                    HoldingIdentity::class.java,
                    GroupParameters::class.java
                )
            ).also { it.start() }
        }

        if (reconciler == null) {
        reconciler = reconcilerFactory.create(
                dbReader = dbReconciler!!,
                kafkaReader = reconcilerReader,
                writer = reconcilerWriter,
                keyClass = HoldingIdentity::class.java,
                valueClass = GroupParameters::class.java,
                reconciliationIntervalMs = intervalMillis
            ).also { it.start() }
        } else {
            logger.info("Updating group parameters ${Reconciler::class.java.name}")
            reconciler!!.updateInterval(intervalMillis)
        }
    }
}
