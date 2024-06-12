package net.corda.processors.db.internal

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.chunking.datamodel.ChunkingEntities
import net.corda.chunking.read.ChunkReadService
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.configuration.read.reconcile.ConfigReconcilerReader
import net.corda.configuration.write.ConfigWriteService
import net.corda.configuration.write.publish.ConfigPublishService
import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.cpiinfo.write.CpiInfoWriteService
import net.corda.cpk.read.CpkReadService
import net.corda.cpk.write.CpkWriteService
import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.client.ReconcilerCryptoOpsClient
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.schema.CordaDb
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.datamodel.ConfigurationEntities
import net.corda.libs.configuration.helper.getConfig
import net.corda.libs.cpi.datamodel.CpiEntities
import net.corda.libs.cpi.datamodel.repository.factory.CpiCpkRepositoryFactory
import net.corda.libs.scheduler.datamodel.SchedulerEntities
import net.corda.libs.virtualnode.datamodel.VirtualNodeEntities
import net.corda.libs.virtualnode.datamodel.repository.RequestsIdsRepositoryImpl
import net.corda.lifecycle.DependentComponents
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.membership.certificate.service.CertificatesService
import net.corda.membership.certificates.datamodel.CertificateEntities
import net.corda.membership.client.MemberResourceClient
import net.corda.membership.datamodel.MembershipEntities
import net.corda.membership.group.policy.validation.MembershipGroupPolicyValidator
import net.corda.membership.groupparams.writer.service.GroupParametersWriterService
import net.corda.membership.lib.GroupParametersFactory
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.locally.hosted.identities.LocallyHostedIdentitiesService
import net.corda.membership.locally.hosted.identities.LocallyHostedIdentitiesWriter
import net.corda.membership.mtls.allowed.list.service.AllowedCertificatesReaderWriterService
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.persistence.service.MembershipPersistenceService
import net.corda.membership.read.GroupParametersReaderService
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.orm.JpaEntitiesRegistry
import net.corda.permissions.model.RbacEntities
import net.corda.permissions.storage.reader.PermissionStorageReaderService
import net.corda.permissions.storage.writer.PermissionStorageWriterService
import net.corda.processors.db.DBProcessor
import net.corda.processors.db.internal.schedule.DeduplicationTableCleanUpProcessor
import net.corda.reconciliation.ReconcilerFactory
import net.corda.schema.Schemas.ScheduledTask.SCHEDULED_TASK_TOPIC_DB_PROCESSOR
import net.corda.schema.configuration.BootConfig.BOOT_DB
import net.corda.schema.configuration.BootConfig.INSTANCE_ID
import net.corda.schema.configuration.ConfigKeys
import net.corda.utilities.debug
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.write.db.VirtualNodeInfoWriteService
import net.corda.virtualnode.write.db.VirtualNodeWriteService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory

@Suppress("Unused", "LongParameterList")
@Component(service = [DBProcessor::class])
class DBProcessorImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = DbConnectionManager::class)
    private val dbConnectionManager: DbConnectionManager,
    @Reference(service = JpaEntitiesRegistry::class)
    private val entitiesRegistry: JpaEntitiesRegistry,
    @Reference(service = ConfigWriteService::class)
    private val configWriteService: ConfigWriteService,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = PermissionStorageReaderService::class)
    private val permissionStorageReaderService: PermissionStorageReaderService,
    @Reference(service = PermissionStorageWriterService::class)
    private val permissionStorageWriterService: PermissionStorageWriterService,
    @Reference(service = VirtualNodeWriteService::class)
    private val virtualNodeWriteService: VirtualNodeWriteService,
    @Reference(service = ChunkReadService::class)
    private val chunkReadService: ChunkReadService,
    @Reference(service = CpkWriteService::class)
    private val cpkWriteService: CpkWriteService,
    @Reference(service = CpkReadService::class)
    private val cpkReadService: CpkReadService,
    @Reference(service = CpiInfoReadService::class)
    private val cpiInfoReadService: CpiInfoReadService,
    @Reference(service = CpiInfoWriteService::class)
    private val cpiInfoWriteService: CpiInfoWriteService,
    @Reference(service = ReconcilerFactory::class)
    private val reconcilerFactory: ReconcilerFactory,
    @Reference(service = CertificatesService::class)
    private val certificatesService: CertificatesService,
    @Reference(service = ConfigPublishService::class)
    private val configPublishService: ConfigPublishService,
    @Reference(service = ConfigReconcilerReader::class)
    private val configBusReconcilerReader: ConfigReconcilerReader,
    @Reference(service = VirtualNodeInfoReadService::class)
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
    @Reference(service = VirtualNodeInfoWriteService::class)
    private val virtualNodeInfoWriteService: VirtualNodeInfoWriteService,
    @Reference(service = MembershipPersistenceService::class)
    private val membershipPersistenceService: MembershipPersistenceService,
    @Reference(service = GroupParametersWriterService::class)
    private val groupParametersWriterService: GroupParametersWriterService,
    @Reference(service = GroupParametersReaderService::class)
    private val groupParametersReaderService: GroupParametersReaderService,
    @Reference(service = GroupParametersFactory::class)
    private val groupParametersFactory: GroupParametersFactory,
    @Reference(service = MembershipGroupPolicyValidator::class)
    private val membershipGroupPolicyValidator: MembershipGroupPolicyValidator,
    @Reference(service = AllowedCertificatesReaderWriterService::class)
    private val allowedCertificatesReaderWriterService: AllowedCertificatesReaderWriterService,
    @Reference(service = CordaAvroSerializationFactory::class)
    serializationFactory: CordaAvroSerializationFactory,
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory,
    @Reference(service = MembershipGroupReaderProvider::class)
    private val membershipGroupReaderProvider: MembershipGroupReaderProvider,
    @Reference(service = MemberResourceClient::class)
    private val memberResourceClient: MemberResourceClient,
    @Reference(service = MembershipQueryClient::class)
    private val membershipQueryClient: MembershipQueryClient,
    @Reference(service = MembershipPersistenceClient::class)
    private val membershipPersistenceClient: MembershipPersistenceClient,
    @Reference(service = MemberInfoFactory::class)
    private val memberInfoFactory: MemberInfoFactory,
    @Reference(service = LocallyHostedIdentitiesService::class)
    private val locallyHostedIdentitiesService: LocallyHostedIdentitiesService,
    @Reference(service = LocallyHostedIdentitiesWriter::class)
    private val locallyHostedIdentitiesWriter: LocallyHostedIdentitiesWriter,
    @Reference(service = ReconcilerCryptoOpsClient::class)
    private val reconcilierCryptoOpsClient: ReconcilerCryptoOpsClient,
    @Reference(service = KeyEncodingService::class)
    private val keyEncodingService: KeyEncodingService,
) : DBProcessor {
    init {
        // define the different DB Entity Sets
        //  entities can be in different packages, but all JPA classes must be passed in.
        entitiesRegistry.register(
            CordaDb.CordaCluster.persistenceUnitName,
            ConfigurationEntities.classes
                    + VirtualNodeEntities.classes
                    + ChunkingEntities.classes
                    + CpiEntities.classes
                    + CertificateEntities.clusterClasses
                    + MembershipEntities.clusterClasses
                    + SchedulerEntities.classes
        )
        entitiesRegistry.register(CordaDb.RBAC.persistenceUnitName, RbacEntities.classes)
        entitiesRegistry.register(
            CordaDb.Vault.persistenceUnitName,
            CertificateEntities.vnodeClasses
                    + MembershipEntities.vnodeClasses
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
        private const val REGISTRATION = "REGISTRATION"
        private const val CONFIG = "CONFIG"

        private const val DEDUPLICATION_TABLE_MANAGED_RESOURCE = "DEDUPLICATION_TABLE"
        private const val DEDUPLICATION_TABLE_CLEAN_UP_GROUP = "deduplication.table.clean.up"
    }

    private val dependentComponents = DependentComponents.of(
        ::dbConnectionManager,
        ::configWriteService,
        ::configurationReadService,
        ::permissionStorageReaderService,
        ::permissionStorageWriterService,
        ::virtualNodeWriteService,
        ::chunkReadService,
        ::cpkWriteService,
        ::cpkReadService,
        ::cpiInfoReadService,
        ::cpiInfoWriteService,
        ::certificatesService,
        ::configPublishService,
        ::virtualNodeInfoReadService,
        ::virtualNodeInfoWriteService,
        ::membershipPersistenceService,
        ::groupParametersWriterService,
        ::groupParametersReaderService,
        ::membershipGroupPolicyValidator,
        ::allowedCertificatesReaderWriterService,
        ::membershipGroupReaderProvider,
        ::memberResourceClient,
        ::membershipQueryClient,
        ::membershipPersistenceClient,
        ::locallyHostedIdentitiesService,
        ::locallyHostedIdentitiesWriter,
        ::reconcilierCryptoOpsClient
    )
    private val lifecycleCoordinator = coordinatorFactory.createCoordinator<DBProcessorImpl>(dependentComponents, ::eventHandler)

    private val reconcilers = Reconcilers(
        coordinatorFactory,
        dbConnectionManager,
        virtualNodeInfoWriteService,
        virtualNodeInfoReadService,
        cpiInfoReadService,
        cpiInfoWriteService,
        groupParametersWriterService,
        groupParametersReaderService,
        configPublishService,
        configBusReconcilerReader,
        reconcilerFactory,
        entitiesRegistry,
        groupParametersFactory,
        CpiCpkRepositoryFactory(),
        allowedCertificatesReaderWriterService,
        serializationFactory,
        subscriptionFactory,
        publisherFactory,
        configurationReadService,
        memberInfoFactory,
        locallyHostedIdentitiesService,
        locallyHostedIdentitiesWriter,
        certificatesService.client,
        reconcilierCryptoOpsClient,
        keyEncodingService,
    )

    override fun start(bootConfig: SmartConfig) {
        log.info("DB processor starting.")
        lifecycleCoordinator.start()
        lifecycleCoordinator.postEvent(BootConfigEvent(bootConfig))
    }

    override fun stop() {
        log.info("DB processor stopping.")
        lifecycleCoordinator.stop()
    }

    @Suppress("ComplexMethod", "NestedBlockDepth")
    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        log.debug { "DB processor received event $event." }

        when (event) {
            is StartEvent -> onStartEvent()
            is RegistrationStatusChangeEvent -> onRegistrationStatusChangeEvent(event, coordinator)
            is ConfigChangedEvent -> onConfigChangedEvent(event, coordinator)
            is BootConfigEvent -> onBootConfigEvent(event)
            is StopEvent -> onStopEvent()
            else -> log.error("Unexpected event $event!")
        }
    }

    private fun onBootConfigEvent(event: BootConfigEvent) {
        val bootstrapConfig = event.config
        val instanceId = bootstrapConfig.getInt(INSTANCE_ID)

        log.info("Bootstrapping DB connection Manager")
        dbConnectionManager.bootstrap(bootstrapConfig.getConfig(BOOT_DB))

        log.info("Bootstrapping config publish service")
        configPublishService.bootstrapConfig(bootstrapConfig)

        log.info("Bootstrapping config write service with instance id: $instanceId")
        configWriteService.bootstrapConfig(bootstrapConfig)

        log.info("Bootstrapping config read service")
        configurationReadService.bootstrapConfig(bootstrapConfig)
    }

    private fun onRegistrationStatusChangeEvent(
        event: RegistrationStatusChangeEvent,
        coordinator: LifecycleCoordinator
    ) {
        log.info("DB processor is ${event.status}")
        if (event.status == LifecycleStatus.UP) {
            coordinator.createManagedResource(CONFIG) {
                configurationReadService.registerComponentForUpdates(
                    coordinator,
                    setOf(
                        ConfigKeys.RECONCILIATION_CONFIG,
                        ConfigKeys.MESSAGING_CONFIG
                    )
                )
            }
        }
        coordinator.updateStatus(event.status)
    }

    private fun onConfigChangedEvent(
        event: ConfigChangedEvent,
        coordinator: LifecycleCoordinator
    ) {
        // Creates and starts the rest of the reconcilers
        reconcilers.onConfigChanged(event)

        val messagingConfig = event.config.getConfig(ConfigKeys.MESSAGING_CONFIG)
        coordinator.createManagedResource(DEDUPLICATION_TABLE_MANAGED_RESOURCE) {
            subscriptionFactory.createDurableSubscription(
                SubscriptionConfig(DEDUPLICATION_TABLE_CLEAN_UP_GROUP, SCHEDULED_TASK_TOPIC_DB_PROCESSOR),
                DeduplicationTableCleanUpProcessor(
                    dbConnectionManager,
                    virtualNodeInfoReadService,
                    RequestsIdsRepositoryImpl()
                ),
                messagingConfig,
                null
            )
        }.start()
    }

    private fun onStartEvent() {
        // First Config reconciliation needs to run at least once. It cannot wait for its configuration as
        // it is the one to offer the DB Config (therefore its own configuration too) to `ConfigurationReadService`.
        reconcilers.updateConfigReconciler(3600000)

        lifecycleCoordinator.createManagedResource(REGISTRATION) {
            lifecycleCoordinator.followStatusChangesByName(
                setOf(LifecycleCoordinatorName.forComponent<DbConnectionManager>())
            )
        }
    }

    private fun onStopEvent() {
        reconcilers.stop()
    }

    data class BootConfigEvent(val config: SmartConfig) : LifecycleEvent
}
