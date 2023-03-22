package net.corda.crypto.service.impl

import java.time.Duration
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.cipher.suite.CryptoService
import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.cipher.suite.PlatformDigestService
import net.corda.crypto.component.impl.AbstractConfigurableComponent
import net.corda.crypto.component.impl.DependenciesTracker
import net.corda.crypto.component.impl.LifecycleNameProvider
import net.corda.crypto.component.impl.lifecycleNameAsSet
import net.corda.crypto.config.impl.CryptoHSMConfig
import net.corda.crypto.config.impl.bootstrapHsmId
import net.corda.crypto.config.impl.hsm
import net.corda.crypto.config.impl.toCryptoConfig
import net.corda.crypto.core.CryptoTenants
import net.corda.crypto.core.InvalidParamsException
import net.corda.crypto.impl.decorators.CryptoServiceDecorator
import net.corda.crypto.service.CryptoServiceFactory
import net.corda.crypto.service.CryptoServiceRef
import net.corda.crypto.softhsm.CryptoRepository
import net.corda.crypto.softhsm.CryptoServiceProvider
import net.corda.crypto.softhsm.cryptoRepositoryFactory
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.layeredpropertymap.LayeredPropertyMapFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.orm.JpaEntitiesRegistry
import net.corda.schema.configuration.ConfigKeys.CRYPTO_CONFIG
import net.corda.utilities.debug
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory

/**
 * A high level factory which worker instance which supports only the Soft HSM implementation.
 *
 * This code uses the HSM service to select an HSM implementation for a specific
 * tenantId and category, and then CryptoServiceProvider to make the crypto service,
 * and CryptoServiceDecorator to add retries logic
 */
@Suppress("LongParameterList")
@Component(service = [CryptoServiceFactory::class])
class CryptoServiceFactoryImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    configurationReadService: ConfigurationReadService,
    @Reference(service = DbConnectionManager::class)
    private val dbConnectionManager: DbConnectionManager,
    @Reference(service = JpaEntitiesRegistry::class)
    private val jpaEntitiesRegistry: JpaEntitiesRegistry,
    @Reference(service = VirtualNodeInfoReadService::class)
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
    @Reference(service = KeyEncodingService::class)
    private val keyEncodingService: KeyEncodingService,
    @Reference(service = PlatformDigestService::class)
    private val digestService: PlatformDigestService,
    @Reference(service = LayeredPropertyMapFactory::class)
    private val layeredPropertyMapFactory: LayeredPropertyMapFactory,
    @Reference(service = CryptoServiceProvider::class)
    private val cryptoServiceProvider: CryptoServiceProvider,
) : AbstractConfigurableComponent<CryptoServiceFactoryImpl.Impl>(
    coordinatorFactory = coordinatorFactory,
    myName = LifecycleCoordinatorName.forComponent<CryptoServiceFactory>(),
    configurationReadService = configurationReadService,
    upstream = DependenciesTracker.Default(
        setOf(
            LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
            LifecycleCoordinatorName.forComponent<DbConnectionManager>(),
            LifecycleCoordinatorName.forComponent<VirtualNodeInfoReadService>(),
        ) + ((cryptoServiceProvider as? LifecycleNameProvider)?.lifecycleNameAsSet() ?: emptySet())
    ),
    configKeys = setOf(CRYPTO_CONFIG)
), CryptoServiceFactory {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun createActiveImpl(event: ConfigChangedEvent): Impl = Impl(
        bootConfig = bootConfig
            ?: throw IllegalStateException("The bootstrap configuration haven't been received yet."),
        event = event,
        cryptoRepository = cryptoRepositoryFactory(
            CryptoTenants.CRYPTO,
            event.config.toCryptoConfig(),
            dbConnectionManager,
            jpaEntitiesRegistry,
            virtualNodeInfoReadService,
            keyEncodingService,
            digestService,
            layeredPropertyMapFactory,
        ),
        cryptoServiceProvider = cryptoServiceProvider
    )

    override fun findInstance(tenantId: String, category: String): CryptoServiceRef =
        impl.findInstance(tenantId, category)

    override fun getInstance(hsmId: String): CryptoService =
        impl.getInstance(hsmId)

    override fun bootstrapConfig(config: SmartConfig) {
        lifecycleCoordinator.postEvent(BootstrapConfigProvided(config))
    }

    override fun isReady(): Boolean = bootConfig != null

    class Impl(
        bootConfig: SmartConfig,
        event: ConfigChangedEvent,
        private val cryptoRepository: CryptoRepository,
        private val cryptoServiceProvider: CryptoServiceProvider,
    ) : DownstreamAlwaysUpAbstractImpl() {

        private val hsmId: String

        private val cryptoConfig: SmartConfig
        private val hsmConfig: CryptoHSMConfig

        init {
            cryptoConfig = event.config.toCryptoConfig()
            hsmId = bootConfig.bootstrapHsmId()
            hsmConfig = cryptoConfig.hsm(hsmId)
        }

        private val cryptoService: CryptoService by lazy(LazyThreadSafetyMode.PUBLICATION) {
            val retry = hsmConfig.retry
            val hsm = hsmConfig.hsm
            val cryptoService = cryptoServiceProvider.getInstance(hsm.cfg)
            CryptoServiceDecorator.create(
                cryptoService,
                retry.maxAttempts,
                Duration.ofMillis(retry.attemptTimeoutMills)
            )
        }

        fun findInstance(tenantId: String, category: String): CryptoServiceRef {
            logger.debug { "Getting the crypto service for tenantId '$tenantId', category '$category'." }
            val association = cryptoRepository.findTenantAssociation(tenantId, category)
                ?: throw InvalidParamsException("The tenant '$tenantId' is not configured for category '$category'.")
            if (association.hsmId != hsmId) {
                throw InvalidParamsException(
                    "This hsmId '$hsmId' is not configured to handle tenant '$tenantId' " +
                            "with category '$category' and association '$association'."
                )
            }
            logger.info("Creating {}: association={}", CryptoServiceRef::class.simpleName, association)
            return CryptoServiceRef(
                tenantId = association.tenantId,
                category = association.category,
                masterKeyAlias = association.masterKeyAlias,
                hsmId = association.hsmId,
                instance = cryptoService
            )
        }

        fun getInstance(hsmId: String): CryptoService {
            logger.debug { "Getting the crypto service for hsmId=$hsmId)" }
            if (hsmId != this.hsmId) {
                throw IllegalArgumentException(
                    "The worker is not configured to handle $hsmId, it handles ${this.hsmId}."
                )
            }
            return cryptoService
        }
    }
}