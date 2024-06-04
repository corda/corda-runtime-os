package net.corda.processors.db.internal

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.configuration.read.reconcile.ConfigReconcilerReader
import net.corda.configuration.write.publish.ConfigPublishService
import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.cpiinfo.write.CpiInfoWriteService
import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.client.CryptoOpsClient
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.cpi.datamodel.repository.factory.CpiCpkRepositoryFactory
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.membership.certificate.client.DbCertificateClient
import net.corda.membership.groupparams.writer.service.GroupParametersWriterService
import net.corda.membership.lib.GroupParametersFactory
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.locally.hosted.identities.LocallyHostedIdentitiesService
import net.corda.membership.locally.hosted.identities.LocallyHostedIdentitiesWriter
import net.corda.membership.mtls.allowed.list.service.AllowedCertificatesReaderWriterService
import net.corda.membership.read.GroupParametersReaderService
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.orm.JpaEntitiesRegistry
import net.corda.processors.db.internal.reconcile.db.ConfigReconciler
import net.corda.processors.db.internal.reconcile.db.CpiReconciler
import net.corda.processors.db.internal.reconcile.db.GroupParametersReconciler
import net.corda.processors.db.internal.reconcile.db.HostedIdentityReconciler
import net.corda.processors.db.internal.reconcile.db.MemberInfoReconciler
import net.corda.processors.db.internal.reconcile.db.MgmAllowedCertificateSubjectsReconciler
import net.corda.processors.db.internal.reconcile.db.VirtualNodeReconciler
import net.corda.reconciliation.ReconcilerFactory
import net.corda.schema.configuration.ConfigKeys
import net.corda.schema.configuration.ReconciliationConfig.RECONCILIATION_CONFIG_INTERVAL_MS
import net.corda.schema.configuration.ReconciliationConfig.RECONCILIATION_CPI_INFO_INTERVAL_MS
import net.corda.schema.configuration.ReconciliationConfig.RECONCILIATION_GROUP_PARAMS_INTERVAL_MS
import net.corda.schema.configuration.ReconciliationConfig.RECONCILIATION_HOSTED_IDENTITY_INTERVAL_MS
import net.corda.schema.configuration.ReconciliationConfig.RECONCILIATION_MEMBER_INFO_INTERVAL_MS
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
    jpaEntitiesRegistry: JpaEntitiesRegistry,
    groupParametersFactory: GroupParametersFactory,
    cpiCpkRepositoryFactory: CpiCpkRepositoryFactory,
    allowedCertificatesReaderWriterService: AllowedCertificatesReaderWriterService,
    serializationFactory: CordaAvroSerializationFactory,
    subscriptionFactory: SubscriptionFactory,
    publisherFactory: PublisherFactory,
    configurationReadService: ConfigurationReadService,
    memberInfoFactory: MemberInfoFactory,
    hostedIdentityReaderService: LocallyHostedIdentitiesService,
    hostedIdentityWriterService: LocallyHostedIdentitiesWriter,
    certificatesClient: DbCertificateClient,
    cryptoOpsClient: CryptoOpsClient,
    keyEncodingService: KeyEncodingService,
) {
    private val cpiReconciler = CpiReconciler(
        coordinatorFactory,
        dbConnectionManager,
        reconcilerFactory,
        cpiInfoReadService,
        cpiInfoWriteService,
        cpiCpkRepositoryFactory.createCpiMetadataRepository()
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
    private val memberInfoReconciler = MemberInfoReconciler(
        coordinatorFactory,
        dbConnectionManager,
        virtualNodeInfoReadService,
        jpaEntitiesRegistry,
        serializationFactory,
        reconcilerFactory,
        publisherFactory,
        subscriptionFactory,
        configurationReadService,
        memberInfoFactory,
    )

    private val hostedIdentityReconciler = HostedIdentityReconciler(
        coordinatorFactory,
        dbConnectionManager,
        reconcilerFactory,
        hostedIdentityReaderService,
        hostedIdentityWriterService,
        certificatesClient,
        cryptoOpsClient,
        keyEncodingService,
        virtualNodeInfoReadService,
        jpaEntitiesRegistry,
    )

    fun stop() {
        cpiReconciler.stop()
        vnodeReconciler.stop()
        configReconciler.stop()
        groupParametersReconciler.stop()
        mgmAllowedCertificateSubjectsReconciler.stop()
        memberInfoReconciler.stop()
        hostedIdentityReconciler.stop()
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
        smartConfig.updateIntervalWhenKeyIs(
            RECONCILIATION_GROUP_PARAMS_INTERVAL_MS,
            groupParametersReconciler::updateInterval
        )
        smartConfig.updateIntervalWhenKeyIs(
            RECONCILIATION_MTLS_MGM_ALLOWED_LIST_INTERVAL_MS,
            mgmAllowedCertificateSubjectsReconciler::updateInterval,
        )
        smartConfig.updateIntervalWhenKeyIs(RECONCILIATION_MEMBER_INFO_INTERVAL_MS, memberInfoReconciler::updateInterval)
        smartConfig.updateIntervalWhenKeyIs(RECONCILIATION_HOSTED_IDENTITY_INTERVAL_MS, hostedIdentityReconciler::updateInterval)
    }

    /** Convenience function to correctly set the interval when the key actually exists in the config */
    private fun SmartConfig.updateIntervalWhenKeyIs(key: String, action: (Long) -> Unit) {
        if (hasPath(key)) getLong(key).let(action)
    }
}
