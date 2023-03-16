package net.corda.processors.db.internal

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.reconcile.ConfigReconcilerReader
import net.corda.configuration.write.publish.ConfigPublishService
import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.cpiinfo.write.CpiInfoWriteService
import net.corda.data.CordaAvroSerializationFactory
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.membership.groupparams.writer.service.GroupParametersWriterService
import net.corda.membership.lib.GroupParametersFactory
import net.corda.membership.mtls.allowed.list.service.AllowedCertificatesReaderWriterService
import net.corda.membership.read.GroupParametersReaderService
import net.corda.orm.JpaEntitiesRegistry
import net.corda.processors.db.internal.reconcile.db.ConfigReconciler
import net.corda.processors.db.internal.reconcile.db.CpiReconciler
import net.corda.processors.db.internal.reconcile.db.GroupParametersReconciler
import net.corda.processors.db.internal.reconcile.db.MgmAllowedCertificateSubjectsReconciler
import net.corda.processors.db.internal.reconcile.db.VirtualNodeReconciler
import net.corda.reconciliation.ReconcilerFactory
import net.corda.schema.configuration.ConfigKeys
import net.corda.schema.configuration.ReconciliationConfig.RECONCILIATION_CONFIG_INTERVAL_MS
import net.corda.schema.configuration.ReconciliationConfig.RECONCILIATION_CPI_INFO_INTERVAL_MS
import net.corda.schema.configuration.ReconciliationConfig.RECONCILIATION_GROUP_PARAMS_INTERVAL_MS
import net.corda.schema.configuration.ReconciliationConfig.RECONCILIATION_MTLS_MGM_ALLOWED_LIST_INTERVAL_MS
import net.corda.schema.configuration.ReconciliationConfig.RECONCILIATION_VNODE_INFO_INTERVAL_MS
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.write.db.VirtualNodeInfoWriteService

/**
 * Container component that holds the reconilcation objects.
 */
@Suppress("LongParameterList")
class Reconcilers(
    coordinatorFactory: LifecycleCoordinatorFactory,
    dbConnectionManager: DbConnectionManager,
    virtualNodeInfoWriteService: VirtualNodeInfoWriteService,
    virtualNodeInfoReadService: VirtualNodeInfoReadService,
    cpiInfoReadService: CpiInfoReadService,
    cpiInfoWriteService: CpiInfoWriteService,
    groupParametersWriterService: GroupParametersWriterService,
    groupParametersReaderService: GroupParametersReaderService,
    configPublishService: ConfigPublishService,
    configBusReconcilerReader: ConfigReconcilerReader,
    reconcilerFactory: ReconcilerFactory,
    cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    jpaEntitiesRegistry: JpaEntitiesRegistry,
    groupParametersFactory: GroupParametersFactory,
    allowedCertificatesReaderWriterService: AllowedCertificatesReaderWriterService,
) {
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
    private val groupParametersReconciler = GroupParametersReconciler(
        cordaAvroSerializationFactory,
        coordinatorFactory,
        dbConnectionManager,
        virtualNodeInfoReadService,
        jpaEntitiesRegistry,
        groupParametersFactory,
        reconcilerFactory,
        groupParametersWriterService,
        groupParametersReaderService,
    )
    private val mgmAllowedCertificateSubjectsReconciler = MgmAllowedCertificateSubjectsReconciler(
        coordinatorFactory,
        dbConnectionManager,
        virtualNodeInfoReadService,
        jpaEntitiesRegistry,
        reconcilerFactory,
        allowedCertificatesReaderWriterService,
        allowedCertificatesReaderWriterService,
    )

    fun stop() {
        cpiReconciler.stop()
        vnodeReconciler.stop()
        configReconciler.stop()
        groupParametersReconciler.stop()
        mgmAllowedCertificateSubjectsReconciler.stop()
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
        smartConfig.updateIntervalWhenKeyIs(RECONCILIATION_GROUP_PARAMS_INTERVAL_MS, groupParametersReconciler::updateInterval)
        smartConfig.updateIntervalWhenKeyIs(
            RECONCILIATION_MTLS_MGM_ALLOWED_LIST_INTERVAL_MS,
            mgmAllowedCertificateSubjectsReconciler::updateInterval,
        )
    }

    /** Convenience function to correctly set the interval when the key actually exists in the config */
    private fun SmartConfig.updateIntervalWhenKeyIs(key: String, action: (Long) -> Unit) {
        if (hasPath(key)) getLong(key).let(action)
    }
}
