package net.corda.processors.crypto.internal

import net.corda.configuration.read.ConfigChangedEvent
import java.util.concurrent.atomic.AtomicInteger
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.cipher.suite.PlatformDigestService
import net.corda.crypto.config.impl.retrying
import net.corda.crypto.config.impl.signingService
import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.core.CryptoTenants
import net.corda.crypto.persistence.HSMStore
import net.corda.crypto.persistence.db.model.CryptoEntities
import net.corda.crypto.service.CryptoServiceFactory
import net.corda.crypto.service.HSMService
import net.corda.crypto.service.impl.SigningServiceImpl
import net.corda.crypto.service.impl.bus.CryptoFlowOpsBusProcessor
import net.corda.crypto.service.impl.bus.CryptoOpsBusProcessor
import net.corda.crypto.service.impl.bus.HSMRegistrationBusProcessor
import net.corda.crypto.softhsm.SoftCryptoServiceProvider
import net.corda.crypto.softhsm.impl.SigningRepositoryFactoryImpl
import net.corda.data.crypto.wire.hsm.registration.HSMRegistrationRequest
import net.corda.data.crypto.wire.hsm.registration.HSMRegistrationResponse
import net.corda.data.crypto.wire.ops.flow.FlowOpsRequest
import net.corda.data.crypto.wire.ops.rpc.RpcOpsRequest
import net.corda.data.crypto.wire.ops.rpc.RpcOpsResponse
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.schema.CordaDb
import net.corda.flow.external.events.responses.factory.ExternalEventResponseFactory
import net.corda.layeredpropertymap.LayeredPropertyMapFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.datamodel.ConfigurationEntities
import net.corda.libs.configuration.helper.getConfig
import net.corda.lifecycle.DependentComponents
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.subscription.RPCSubscription
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.SubscriptionBase
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.orm.JpaEntitiesRegistry
import net.corda.processors.crypto.CryptoProcessor
import net.corda.schema.Schemas
import net.corda.schema.configuration.BootConfig.BOOT_CRYPTO
import net.corda.schema.configuration.BootConfig.BOOT_DB
import net.corda.schema.configuration.ConfigKeys.CRYPTO_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.v5.base.annotations.VisibleForTesting
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Suppress("LongParameterList")
@Component(service = [CryptoProcessor::class])
class CryptoProcessorImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = HSMStore::class)
    private val hsmStore: HSMStore,
    @Reference(service = SoftCryptoServiceProvider::class)
    private val softCryptoServiceProvider: SoftCryptoServiceProvider,
    @Reference(service = CryptoServiceFactory::class)
    private val cryptoServiceFactory: CryptoServiceFactory,
    @Reference(service = HSMService::class)
    private val hsmService: HSMService,
    @Reference(service = JpaEntitiesRegistry::class)
    private val jpaEntitiesRegistry: JpaEntitiesRegistry,
    @Reference(service = DbConnectionManager::class)
    private val dbConnectionManager: DbConnectionManager,
    @Reference(service = VirtualNodeInfoReadService::class)
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = ExternalEventResponseFactory::class)
    private val externalEventResponseFactory: ExternalEventResponseFactory,
    @Reference(service = KeyEncodingService::class)
    private val keyEncodingService: KeyEncodingService,
    @Reference(service = LayeredPropertyMapFactory::class)
    private val layeredPropertyMapFactory: LayeredPropertyMapFactory,
    @Reference(service = PlatformDigestService::class)
    private val digestService: PlatformDigestService,
    @Reference(service = CipherSchemeMetadata::class)
    private val schemeMetadata: CipherSchemeMetadata,
) : CryptoProcessor {
    private companion object {
        val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        val configKeys = setOf(
            MESSAGING_CONFIG,
            CRYPTO_CONFIG
        )
        const val FLOW_OPS_SUBSCRIPTION = "FLOW_OPS_SUBSCRIPTION"
        const val RPC_OPS_SUBSCRIPTION = "RPC_OPS_SUBSCRIPTION"
        const val HSM_REG_SUBSCRIPTION = "HSM_REG_SUBSCRIPTION"
    }

    init {
        jpaEntitiesRegistry.register(CordaDb.Crypto.persistenceUnitName, CryptoEntities.classes)
        jpaEntitiesRegistry.register(CordaDb.CordaCluster.persistenceUnitName, ConfigurationEntities.classes)
    }

    private val dependentComponents = DependentComponents.of(
        ::configurationReadService,
        ::hsmStore,
        ::softCryptoServiceProvider,
        ::cryptoServiceFactory,
        ::hsmService,
        ::dbConnectionManager,
        ::virtualNodeInfoReadService,
    )

    // We are making the below coordinator visible to be able to test the processor as if it were a `Lifecycle`
    // using `LifecycleTest` API
    @get:VisibleForTesting
    internal val lifecycleCoordinator =
        coordinatorFactory.createCoordinator<CryptoProcessor>(dependentComponents, ::eventHandler)

    @Volatile
    private var hsmAssociated: Boolean = false

    @Volatile
    private var dependenciesUp: Boolean = false

    private val tmpAssignmentFailureCounter = AtomicInteger(0)

    override val isRunning: Boolean
        get() = lifecycleCoordinator.isRunning

    override fun start(bootConfig: SmartConfig) {
        logger.trace("Crypto processor starting.")
        lifecycleCoordinator.start()
        lifecycleCoordinator.postEvent(BootConfigEvent(bootConfig))
    }

    override fun stop() {
        logger.trace("Crypto processor stopping.")
        lifecycleCoordinator.stop()
    }

    @Suppress("ComplexMethod", "NestedBlockDepth")
    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        logger.trace("Crypto processor received event $event.")
        when (event) {
            is StartEvent -> {
                // No need to register coordinator to follow dependent components.
                // This already happens in `coordinatorFactory.createCoordinator` above.
            }
            is StopEvent -> {
                // Nothing to do
            }
            is BootConfigEvent -> {
                val bootstrapConfig = event.config

                logger.trace("Bootstrapping {}", configurationReadService::class.simpleName)
                configurationReadService.bootstrapConfig(bootstrapConfig)

                logger.trace("Bootstrapping {}", dbConnectionManager::class.simpleName)
                dbConnectionManager.bootstrap(bootstrapConfig.getConfig(BOOT_DB))

                logger.trace("Bootstrapping {}", cryptoServiceFactory::class.simpleName)
                cryptoServiceFactory.bootstrapConfig(bootstrapConfig.getConfig(BOOT_CRYPTO))
            }
            is RegistrationStatusChangeEvent -> {
                logger.trace("Registering for configuration updates.")
                configurationReadService.registerComponentForUpdates(coordinator, configKeys)
                if (event.status == LifecycleStatus.UP) {
                    dependenciesUp = true
                    // TODO only do setStatus once the config is in in ConfigChangedEvent
                    if (hsmAssociated) {
                        setStatus(event.status, coordinator)
                    } else {
                        coordinator.postEvent(AssociateHSM())
                    }
                } else {
                    dependenciesUp = false
                    setStatus(event.status, coordinator)
                }
            }
            is AssociateHSM -> {
                if (dependenciesUp) {
                    if (hsmAssociated) {
                        setStatus(LifecycleStatus.UP, coordinator)
                    } else {
                        val failed = temporaryAssociateClusterWithSoftHSM()
                        if (failed.isNotEmpty()) {
                            if (tmpAssignmentFailureCounter.getAndIncrement() <= 5) {
                                logger.warn(
                                    "Failed to associate: [${failed.joinToString { "${it.first}:${it.second}" }}]" +
                                            ", will retry..."
                                )
                                coordinator.postEvent(AssociateHSM()) // try again
                            } else {
                                logger.error(
                                    "Failed to associate: [${failed.joinToString { "${it.first}:${it.second}" }}]"
                                )
                                setStatus(LifecycleStatus.ERROR, coordinator)
                            }
                        } else {
                            hsmAssociated = true
                            tmpAssignmentFailureCounter.set(0)
                            setStatus(LifecycleStatus.UP, coordinator)
                        }
                    }
                }
            }

            is ConfigChangedEvent -> {
                startBusProcessors(event, coordinator)
            }
        }
    }

    private fun startBusProcessors(event: ConfigChangedEvent, coordinator: LifecycleCoordinator) {
        val cryptoConfig = event.config.getConfig(CRYPTO_CONFIG)

        // first make the signing service object, which both processors will consume
        val signingService = SigningServiceImpl(
            cryptoServiceFactory, SigningRepositoryFactoryImpl(
                dbConnectionManager,
                virtualNodeInfoReadService,
                jpaEntitiesRegistry,
                keyEncodingService,
                digestService,
                layeredPropertyMapFactory
            ),
            schemeMetadata = schemeMetadata,
            digestService = digestService,
            config = cryptoConfig.signingService()
        )

        // make the processors
        val retryingConfig = cryptoConfig.retrying()
        val flowOpsProcessor = CryptoFlowOpsBusProcessor(signingService, externalEventResponseFactory, retryingConfig)
        val rpcOpsProcessor = CryptoOpsBusProcessor(signingService, retryingConfig)
        val hsmRegistrationProcessor = HSMRegistrationBusProcessor(hsmService, retryingConfig)

        // now make and start the subscriptions
        val messagingConfig = event.config.getConfig(MESSAGING_CONFIG)
        val flowGroupName = "crypto.ops.flow"
        coordinator.createManagedResource(FLOW_OPS_SUBSCRIPTION) {
            subscriptionFactory.createDurableSubscription(
                subscriptionConfig = SubscriptionConfig(flowGroupName, Schemas.Crypto.FLOW_OPS_MESSAGE_TOPIC),
                processor = flowOpsProcessor,
                messagingConfig = messagingConfig,
                partitionAssignmentListener = null
            )
        }
        logger.trace("Starting processing on $flowGroupName ${Schemas.Crypto.FLOW_OPS_MESSAGE_TOPIC}")
        coordinator.getManagedResource<SubscriptionBase>(FLOW_OPS_SUBSCRIPTION)!!.start()

        val rpcGroupName = "crypto.ops.rpc"
        val rpcClientName = "crypto.ops.rpc"
        coordinator.createManagedResource(RPC_OPS_SUBSCRIPTION) {
            subscriptionFactory.createRPCSubscription(
                rpcConfig = RPCConfig(
                    groupName = rpcGroupName,
                    clientName = rpcClientName,
                    requestTopic = Schemas.Crypto.RPC_OPS_MESSAGE_TOPIC,
                    requestType = RpcOpsRequest::class.java,
                    responseType = RpcOpsResponse::class.java
                ),
                responderProcessor = rpcOpsProcessor,
                messagingConfig = messagingConfig
            )
        }
        logger.trace("Starting processing on $rpcGroupName ${Schemas.Crypto.RPC_OPS_MESSAGE_TOPIC}")
        coordinator.getManagedResource<SubscriptionBase>(RPC_OPS_SUBSCRIPTION)!!.start()

        val hsmRegGroupName = "crypto.hsm.rpc.registration"
        val hsmRegClientName = "crypto.hsm.rpc.registration"
        coordinator.createManagedResource(HSM_REG_SUBSCRIPTION) {
            subscriptionFactory.createRPCSubscription(
                rpcConfig = RPCConfig(
                    groupName = hsmRegGroupName,
                    clientName = hsmRegClientName,
                    requestTopic = Schemas.Crypto.RPC_HSM_REGISTRATION_MESSAGE_TOPIC,
                    requestType = HSMRegistrationRequest::class.java,
                    responseType = HSMRegistrationResponse::class.java
                ),
                responderProcessor = hsmRegistrationProcessor,
                messagingConfig = messagingConfig
            )
        }
        logger.trace("Starting processing on $hsmRegGroupName ${Schemas.Crypto.RPC_HSM_REGISTRATION_MESSAGE_TOPIC}")
        coordinator.getManagedResource<SubscriptionBase>(HSM_REG_SUBSCRIPTION)!!.start()
    }

    private fun setStatus(status: LifecycleStatus, coordinator: LifecycleCoordinator) {
        logger.trace("Crypto processor is set to be $status")
        coordinator.updateStatus(status)
    }

    private fun temporaryAssociateClusterWithSoftHSM(): List<Pair<String, String>> {
        logger.trace("Assigning SOFT HSM to cluster tenants.")
        val assigned = mutableListOf<Pair<String, String>>()
        val failed = mutableListOf<Pair<String, String>>()
        CryptoConsts.Categories.all.forEach { category ->
            CryptoTenants.allClusterTenants.forEach { tenantId ->
                if (tryAssignSoftHSM(tenantId, category)) {
                    assigned.add(Pair(tenantId, category))
                } else {
                    failed.add(Pair(tenantId, category))
                }
            }
        }
        logger.trace(
            "SOFT HSM assignment is done. Assigned=[{}], failed=[{}]",
            assigned.joinToString { "${it.first}:${it.second}" },
            failed.joinToString { "${it.first}:${it.second}" }
        )
        return failed
    }

    private fun tryAssignSoftHSM(tenantId: String, category: String): Boolean = try {
        logger.trace("Assigning SOFT HSM for $tenantId:$category")
        hsmService.assignSoftHSM(tenantId, category)
        logger.trace("Assigned SOFT HSM for $tenantId:$category")
        true
    } catch (e: Throwable) {
        logger.error("Failed to assign SOFT HSM for $tenantId:$category", e)
        false
    }

    class AssociateHSM : LifecycleEvent
}