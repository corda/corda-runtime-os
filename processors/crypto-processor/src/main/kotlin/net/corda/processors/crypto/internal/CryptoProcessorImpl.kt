package net.corda.processors.crypto.internal

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.typesafe.config.Config
import net.corda.cache.caffeine.CacheFactoryImpl
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.cipher.suite.PlatformDigestService
import net.corda.crypto.config.impl.ALIAS
import net.corda.crypto.config.impl.CACHING
import net.corda.crypto.config.impl.DEFAULT
import net.corda.crypto.config.impl.DEFAULT_WRAPPING_KEY
import net.corda.crypto.config.impl.EXPIRE_AFTER_ACCESS_MINS
import net.corda.crypto.config.impl.HSM
import net.corda.crypto.config.impl.MAXIMUM_SIZE
import net.corda.crypto.config.impl.PASSPHRASE
import net.corda.crypto.config.impl.SALT
import net.corda.crypto.config.impl.WRAPPING_KEYS
import net.corda.crypto.config.impl.retrying
import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.core.CryptoService
import net.corda.crypto.core.CryptoTenants
import net.corda.crypto.core.SigningKeyInfo
import net.corda.crypto.core.aes.WrappingKey
import net.corda.crypto.core.aes.WrappingKeyImpl
import net.corda.crypto.persistence.HSMStore
import net.corda.crypto.persistence.db.model.CryptoEntities
import net.corda.crypto.persistence.getEntityManagerFactory
import net.corda.crypto.service.HSMService
import net.corda.crypto.service.impl.HSMServiceImpl
import net.corda.crypto.service.impl.bus.CryptoFlowOpsBusProcessor
import net.corda.crypto.service.impl.bus.CryptoOpsBusProcessor
import net.corda.crypto.service.impl.bus.HSMRegistrationBusProcessor
import net.corda.crypto.softhsm.impl.SigningRepositoryImpl
import net.corda.crypto.softhsm.impl.SoftCryptoService
import net.corda.crypto.softhsm.impl.WrappingRepositoryImpl
import net.corda.data.crypto.wire.hsm.registration.HSMRegistrationRequest
import net.corda.data.crypto.wire.hsm.registration.HSMRegistrationResponse
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
import net.corda.messaging.api.subscription.SubscriptionBase
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.orm.JpaEntitiesRegistry
import net.corda.processors.crypto.CryptoProcessor
import net.corda.schema.Schemas
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
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.Provider
import java.security.PublicKey
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

// An OSGi component, with no unit tests; instead, tested by using OGGi and mocked out databases in
// integration tests (CryptoProcessorTests), as well as in various kinds of end to end and other full
// system tests.

@Suppress("LongParameterList")
@Component(service = [CryptoProcessor::class])
class CryptoProcessorImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = HSMStore::class)
    private val hsmStore: HSMStore,
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
        ::dbConnectionManager,
        ::virtualNodeInfoReadService,
    )

    // We are making the below coordinator visible to be able to test the processor as if it were a `Lifecycle`
    // using `LifecycleTest` API
    // TODO - can we remove VisibleForTesting here? and go back to lifecycleCorodinator being private
    @get:VisibleForTesting
    internal val lifecycleCoordinator = coordinatorFactory.createCoordinator<CryptoProcessor>(dependentComponents, ::eventHandler)

    @Volatile
    private var dependenciesUp: Boolean = false

    private val tmpAssignmentFailureCounter = AtomicInteger(0)

    private lateinit var cryptoService: CryptoService
    private lateinit var hsmService: HSMService

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

    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        logger.trace("Crypto processor received event $event.")
        when (event) {
            is StartEvent -> {
                logger.trace("Crypto processor starting")
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
            }
            is RegistrationStatusChangeEvent -> {
                logger.trace("Registering for configuration updates.")
                configurationReadService.registerComponentForUpdates(coordinator, configKeys)
                if (event.status == LifecycleStatus.UP) {
                    dependenciesUp = true
                } else {
                    dependenciesUp = false
                    setStatus(event.status, coordinator)
                }
            }
            is ConfigChangedEvent -> {
                startCryptoService(event.config.getConfig(CRYPTO_CONFIG))
                startHSMService()
                startBusProcessors(event, coordinator)
                setStatus(LifecycleStatus.UP, coordinator)
            }
        }
    }

    @Suppress("ThrowsCount")
    private fun startCryptoService(config: SmartConfig) {
        logger.info("Creating instance of the {}", SoftCryptoService::class.java.name)
        val cachingConfig = config.getConfig(CACHING)
        val expireAfterAccessMins = cachingConfig.getConfig(EXPIRE_AFTER_ACCESS_MINS).getLong(DEFAULT)
        val maximumSize = cachingConfig.getConfig(MAXIMUM_SIZE).getLong(DEFAULT)
        val hsmConfig = config.getConfig(HSM)
        val keysList: List<Config> = hsmConfig.getConfigList(WRAPPING_KEYS)
        val unmanagedWrappingKeys: Map<String, WrappingKey> =
            keysList.map {
                it.getString(ALIAS) to WrappingKeyImpl.derive(
                    schemeMetadata,
                    it.getString(PASSPHRASE),
                    it.getString(SALT)
                )
            }.toMap()
        val defaultUnmanagedWrappingKeyName = hsmConfig.getString(DEFAULT_WRAPPING_KEY)
        require(unmanagedWrappingKeys.containsKey(defaultUnmanagedWrappingKeyName)) {
            "default key $defaultUnmanagedWrappingKeyName must be in $HSM.$WRAPPING_KEYS"
        }
        // TODO drop this compatibility code that supports name between KEY_MAP_TRANSIENT_NAME and if so disables caching
        val wrappingKeyCache: Cache<String, WrappingKey> = CacheFactoryImpl().build(
            "HSM-Wrapping-Keys-Map",
            Caffeine.newBuilder()
                .expireAfterAccess(expireAfterAccessMins, TimeUnit.MINUTES)
                .maximumSize(maximumSize)
        )

        val privateKeyCache: Cache<PublicKey, PrivateKey> = CacheFactoryImpl().build(
            "HSM-Soft-Keys-Map",
            Caffeine.newBuilder()
                .expireAfterAccess(expireAfterAccessMins, TimeUnit.MINUTES)
                .maximumSize(maximumSize)
        )
        val signingKeyInfoCache: Cache<PublicKey, SigningKeyInfo> = CacheFactoryImpl().build(
            "Signing-Key-Cache",
            Caffeine.newBuilder()
                .expireAfterAccess(expireAfterAccessMins, TimeUnit.MINUTES)
                .maximumSize(maximumSize)
        )
        cryptoService = SoftCryptoService(
            wrappingRepositoryFactory = { tenantId ->
                WrappingRepositoryImpl(
                    entityManagerFactory = getEntityManagerFactory(
                        tenantId = tenantId,
                        dbConnectionManager = dbConnectionManager,
                        virtualNodeInfoReadService = virtualNodeInfoReadService,
                        jpaEntitiesRegistry = jpaEntitiesRegistry
                    ),
                    tenantId = tenantId
                )
            },
            signingRepositoryFactory = { tenantId ->
                SigningRepositoryImpl(
                    entityManagerFactory = getEntityManagerFactory(
                        tenantId = tenantId,
                        dbConnectionManager = dbConnectionManager,
                        virtualNodeInfoReadService = virtualNodeInfoReadService,
                        jpaEntitiesRegistry = jpaEntitiesRegistry
                    ),
                    tenantId = tenantId,
                    keyEncodingService = schemeMetadata,
                    digestService = digestService,
                    layeredPropertyMapFactory = layeredPropertyMapFactory
                )
            },
            schemeMetadata = schemeMetadata,
            digestService = digestService,
            defaultUnmanagedWrappingKeyName = defaultUnmanagedWrappingKeyName,
            unmanagedWrappingKeys = unmanagedWrappingKeys,
            wrappingKeyCache = wrappingKeyCache,
            privateKeyCache = privateKeyCache,
            signingKeyInfoCache = signingKeyInfoCache,
            keyPairGeneratorFactory = { algorithm: String, provider: Provider ->
                KeyPairGenerator.getInstance(algorithm, provider)
            },
            wrappingKeyFactory = {
                WrappingKeyImpl.generateWrappingKey(it)
            },
            hsmStore = hsmStore,
        )
    }

    private fun startHSMService() {
        hsmService = HSMServiceImpl(hsmStore, cryptoService)
        CryptoConsts.Categories.all.forEach { category ->
            CryptoTenants.allClusterTenants.forEach { tenantId ->
                hsmService.assignSoftHSM(tenantId, category)
                logger.trace("Assigned SOFT HSM for $tenantId:$category")
            }
        }
    }

    private fun startBusProcessors(event: ConfigChangedEvent, coordinator: LifecycleCoordinator) {
        val cryptoConfig = event.config.getConfig(CRYPTO_CONFIG)


        // make the processors
        val retryingConfig = cryptoConfig.retrying()
        val flowOpsProcessor =
            CryptoFlowOpsBusProcessor(cryptoService, externalEventResponseFactory, retryingConfig, keyEncodingService)
        val rpcOpsProcessor = CryptoOpsBusProcessor(cryptoService, retryingConfig, keyEncodingService)
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
}