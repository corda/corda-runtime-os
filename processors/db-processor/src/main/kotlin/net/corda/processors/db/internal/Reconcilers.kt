package net.corda.processors.db.internal

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.reconcile.ConfigReconcilerReader
import net.corda.configuration.write.publish.ConfigPublishService
import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.cpiinfo.write.CpiInfoWriteService
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.processors.db.internal.reconcile.db.ConfigReconciler
import net.corda.processors.db.internal.reconcile.db.CpiReconciler
import net.corda.processors.db.internal.reconcile.db.VirtualNodeReconciler
import net.corda.reconciliation.ReconcilerFactory
import net.corda.schema.configuration.ConfigKeys
import net.corda.schema.configuration.ReconciliationConfig.RECONCILIATION_CONFIG_INTERVAL_MS
import net.corda.schema.configuration.ReconciliationConfig.RECONCILIATION_CPI_INFO_INTERVAL_MS
import net.corda.schema.configuration.ReconciliationConfig.RECONCILIATION_VNODE_INFO_INTERVAL_MS
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.write.db.VirtualNodeInfoWriteService

/**
 * Container component that holds the reconilcation objects.
 */
@Suppress("LongParameterList")
class Reconcilers constructor(
    coordinatorFactory: LifecycleCoordinatorFactory,
    dbConnectionManager: DbConnectionManager,
    virtualNodeInfoWriteService: VirtualNodeInfoWriteService,
    virtualNodeInfoReadService: VirtualNodeInfoReadService,
    cpiInfoReadService: CpiInfoReadService,
    cpiInfoWriteService: CpiInfoWriteService,
    configPublishService: ConfigPublishService,
    configBusReconcilerReader: ConfigReconcilerReader,
    reconcilerFactory: ReconcilerFactory,
) : AutoCloseable {
    private val cpiReconciler = CpiReconciler(
        coordinatorFactory,
        dbConnectionManager,
        reconcilerFactory,
        cpiInfoReadService,
        cpiInfoWriteService
    )

    private val vnodeReconciler = VirtualNodeReconciler(
        coordinatorFactory,
        dbConnectionManager,
        reconcilerFactory,
        virtualNodeInfoReadService,
        virtualNodeInfoWriteService
    )
    private val configReconciler = ConfigReconciler(
        coordinatorFactory,
        dbConnectionManager,
        reconcilerFactory,
        configBusReconcilerReader,
        configPublishService,
    )

    override fun close() {
        cpiReconciler.close()
        vnodeReconciler.close()
        configReconciler.close()
    }

    /**
     * Special case for config reconciliation - we want to be able to directly set this and trigger it
     * on an ad-hoc basis.  See usage in [DBProcessorImpl].
     */
    fun updateConfigReconciler(intervalMs: Long) = configReconciler.updateInterval(intervalMs)

    fun onConfigChanged(event: ConfigChangedEvent) {
        val smartConfig = event.config[ConfigKeys.RECONCILIATION_CONFIG] ?: return

        smartConfig.updateIntervalWhenKeyIs(RECONCILIATION_CPI_INFO_INTERVAL_MS, cpiReconciler::updateInterval)
        smartConfig.updateIntervalWhenKeyIs(RECONCILIATION_VNODE_INFO_INTERVAL_MS, vnodeReconciler::updateInterval)
        smartConfig.updateIntervalWhenKeyIs(RECONCILIATION_CONFIG_INTERVAL_MS, configReconciler::updateInterval)
    }

    /** Convenience function to correctly set the interval when the key actually exists in the config */
    private fun SmartConfig.updateIntervalWhenKeyIs(key: String, action: (Long) -> Unit) {
        if (hasPath(key)) getLong(key).let(action)
    }
}
