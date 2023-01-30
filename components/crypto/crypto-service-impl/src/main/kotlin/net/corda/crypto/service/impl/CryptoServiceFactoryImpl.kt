package net.corda.crypto.service.impl

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.typesafe.config.ConfigRenderOptions
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.cipher.suite.CryptoService
import net.corda.crypto.cipher.suite.CryptoServiceProvider
import net.corda.crypto.component.impl.AbstractConfigurableComponent
import net.corda.crypto.component.impl.DependenciesTracker
import net.corda.crypto.component.impl.FatalActivationException
import net.corda.crypto.component.impl.LifecycleNameProvider
import net.corda.crypto.component.impl.lifecycleNameAsSet
import net.corda.crypto.config.impl.CryptoHSMConfig
import net.corda.crypto.config.impl.bootstrapHsmId
import net.corda.crypto.config.impl.hsm
import net.corda.crypto.config.impl.toCryptoConfig
import net.corda.crypto.core.InvalidParamsException
import net.corda.crypto.impl.decorators.CryptoServiceDecorator
import net.corda.crypto.service.CryptoServiceFactory
import net.corda.crypto.service.CryptoServiceRef
import net.corda.crypto.service.HSMService
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.schema.configuration.ConfigKeys.CRYPTO_CONFIG
import net.corda.v5.base.util.debug
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import java.time.Duration

/**
 * A crypto worker instance will ever support only one HSM implementation due the HSM client libraries limitations, such
 * as able to connect only to single device of the same make ([cryptoServiceProvider] is expecting single reference).
 *
 * As the Soft HSM will most likely will always be present it's service ranking is set to the smallest possible value
 * so any other HSM implementation can be picked up if present on the OSGi classpath.
 *
 * Even in case of the HTTP and Soft HSMs being able to work together the presence of HTTP client and need to
 * communicate with the outside of the Cord cluster will require different worker setup.
 *
 * The limitation of referencing only single provider will help to solve the problem of the domino logic where otherwise
 * one of providers can be down (but not actually used) and another is up.
 */
@Component(service = [CryptoServiceFactory::class])
class CryptoServiceFactoryImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    configurationReadService: ConfigurationReadService,
    @Reference(service = HSMService::class)
    private val hsmService: HSMService,
    @Reference(service = CryptoServiceProvider::class)
    private val cryptoServiceProvider: CryptoServiceProvider<*>
) : AbstractConfigurableComponent<CryptoServiceFactoryImpl.Impl>(
    coordinatorFactory = coordinatorFactory,
    myName = LifecycleCoordinatorName.forComponent<CryptoServiceFactory>(),
    configurationReadService = configurationReadService,
    upstream = DependenciesTracker.Default(
        setOf(
            LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
            LifecycleCoordinatorName.forComponent<HSMService>()
        ) + ((cryptoServiceProvider as? LifecycleNameProvider)?.lifecycleNameAsSet() ?: emptySet())
    ),
    configKeys = setOf(CRYPTO_CONFIG)
), CryptoServiceFactory {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)

        private val jsonMapper = JsonMapper
            .builder()
            .enable(MapperFeature.BLOCK_UNSAFE_POLYMORPHIC_BASE_TYPES)
            .build()
        private val objectMapper: ObjectMapper = jsonMapper
            .registerModule(JavaTimeModule())
            .registerModule(KotlinModule.Builder().build())
            .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    }

    @Suppress("UNCHECKED_CAST")
    override fun createActiveImpl(event: ConfigChangedEvent): Impl = Impl(
        bootConfig = bootConfig ?: throw IllegalStateException("The bootstrap configuration haven't been received yet."),
        event = event,
        hsmService = hsmService,
        cryptoServiceProvider = cryptoServiceProvider as CryptoServiceProvider<Any>
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
        private val hsmService: HSMService,
        private val cryptoServiceProvider: CryptoServiceProvider<Any>
    ) : DownstreamAlwaysUpAbstractImpl() {

        private val hsmId: String

        private val cryptoConfig: SmartConfig

        private val hsmConfig: CryptoHSMConfig

        init {
            cryptoConfig = event.config.toCryptoConfig()
            hsmId = bootConfig.bootstrapHsmId()
            hsmConfig = cryptoConfig.hsm(hsmId)
            if(hsmConfig.hsm.name != cryptoServiceProvider.name) {
                throw FatalActivationException(
                    "Expected to handle ${hsmConfig.hsm.name} but provided with ${cryptoServiceProvider.name}."
                )
            }
        }

        private val cryptoService: CryptoService by lazy(LazyThreadSafetyMode.PUBLICATION) {
            val retry = hsmConfig.retry
            val hsm = hsmConfig.hsm
            logger.info("json ${hsm.cfg.root().render(ConfigRenderOptions.concise()).toString()} to ${cryptoServiceProvider.configType}")
            val cryptoService = cryptoServiceProvider.getInstance(
                objectMapper.readValue(
                    hsm.cfg.root().render(ConfigRenderOptions.concise()),
                    cryptoServiceProvider.configType
                )
            )
            CryptoServiceDecorator.create(
                cryptoService,
                retry.maxAttempts,
                Duration.ofMillis(retry.attemptTimeoutMills)
            )
        }

        fun findInstance(tenantId: String, category: String): CryptoServiceRef {
            logger.debug { "Getting the crypto service for tenantId '$tenantId', category '$category'." }
            val association = hsmService.findAssignedHSM(tenantId, category)
                ?: throw InvalidParamsException("The tenant '$tenantId' is not configured for category '$category'.")
            if(association.hsmId != hsmId) {
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
            if(hsmId != this.hsmId) {
                throw IllegalArgumentException(
                    "The worker is not configured to handle $hsmId, it handles ${this.hsmId}."
                )
            }
            return cryptoService
        }
    }
}