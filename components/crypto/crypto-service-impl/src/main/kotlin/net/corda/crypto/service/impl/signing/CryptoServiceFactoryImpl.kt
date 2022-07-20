package net.corda.crypto.service.impl.signing

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.component.impl.AbstractConfigurableComponent
import net.corda.crypto.component.impl.DependenciesTracker
import net.corda.crypto.component.impl.LifecycleNameProvider
import net.corda.crypto.component.impl.lifecycleNameAsSet
import net.corda.crypto.core.Encryptor
import net.corda.crypto.impl.config.CryptoSigningServiceConfig
import net.corda.crypto.impl.config.rootEncryptor
import net.corda.crypto.impl.config.signingService
import net.corda.crypto.impl.config.toCryptoConfig
import net.corda.crypto.impl.decorators.CryptoServiceDecorator
import net.corda.crypto.service.CryptoServiceFactory
import net.corda.crypto.service.CryptoServiceRef
import net.corda.crypto.service.HSMService
import net.corda.data.crypto.wire.hsm.HSMInfo
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.schema.configuration.ConfigKeys.CRYPTO_CONFIG
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.v5.cipher.suite.CryptoService
import net.corda.v5.cipher.suite.CryptoServiceProvider
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.time.Duration
import java.util.concurrent.TimeUnit

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
    private val hsmRegistrar: HSMService,
    @Reference(service = CryptoServiceProvider::class)
    private val cryptoServiceProvider: CryptoServiceProvider<*>
) : AbstractConfigurableComponent<CryptoServiceFactoryImpl.Impl>(
    coordinatorFactory = coordinatorFactory,
    myName = LifecycleCoordinatorName.forComponent<CryptoServiceFactory>(),
    configurationReadService = configurationReadService,
    upstream = DependenciesTracker.Default(
        setOf(LifecycleCoordinatorName.forComponent<HSMService>()) +
                ((cryptoServiceProvider as? LifecycleNameProvider)?.lifecycleNameAsSet() ?: emptySet())
    ),
    configKeys = setOf(CRYPTO_CONFIG)
), CryptoServiceFactory {
    companion object {
        private val logger = contextLogger()
    }

    override fun createActiveImpl(event: ConfigChangedEvent): Impl = Impl(
        event,
        hsmRegistrar,
        cryptoServiceProvider
    )

    override fun getServiceRef(tenantId: String, category: String): CryptoServiceRef =
        impl.getInstance(tenantId, category)

    override fun getInstance(configId: String): CryptoService =
        impl.getInstance(configId)

    class Impl(
        event: ConfigChangedEvent,
        private val hsmRegistrar: HSMService,
        private val cryptoServiceProvider: CryptoServiceProvider<*>
    ) : DownstreamAlwaysUpAbstractImpl() {

        private val cryptoServiceCreateLock = Any()

        @Volatile
        private var cryptoService: CryptoService? = null

        private val encryptor: Encryptor

        private val cacheConfig: CryptoSigningServiceConfig.Cache

        init {
            val cryptoConfig = event.config.toCryptoConfig()
            encryptor = cryptoConfig.rootEncryptor()
            cacheConfig = cryptoConfig.signingService().cryptoRefsCache
        }

        private val cryptoRefs: Cache<Pair<String, String>, CryptoServiceRef> = Caffeine.newBuilder()
            .expireAfterAccess(cacheConfig.expireAfterAccessMins, TimeUnit.MINUTES)
            .maximumSize(cacheConfig.maximumSize)
            .build()

        fun getInstance(tenantId: String, category: String): CryptoServiceRef {
            logger.debug { "Getting the crypto service for tenantId=$tenantId, category=$category)" }
            return cryptoRefs.get(tenantId to category) {
                val association = hsmRegistrar.findAssignedHSM(tenantId, category)
                    ?: throw IllegalStateException("The tenant=$tenantId is not configured for category=$category")
                logger.info(
                    "Creating {}: id={} configId={} (tenantId={}, category={})",
                    CryptoServiceRef::class.simpleName,
                    association.id,
                    association.config.info.id,
                    association.tenantId,
                    association.category
                )
                CryptoServiceRef(
                    tenantId = association.tenantId,
                    category = association.category,
                    masterKeyAlias = association.masterKeyAlias,
                    aliasSecret = association.aliasSecret,
                    associationId = association.id,
                    instance = getCryptoServiceInstance(association.config.info, association.config.serviceConfig)
                )            }
        }

        fun getInstance(configId: String): CryptoService {
            logger.debug { "Getting the crypto service for configId=$configId)" }
            val config = hsmRegistrar.findHSMConfig(configId)
                ?: throw IllegalStateException("The config=$configId is not found.")
            return getCryptoServiceInstance(config.info, config.serviceConfig)
        }

        private fun getCryptoServiceInstance(info: HSMInfo, serviceConfig: ByteArray): CryptoService {
            if (cryptoServiceProvider.name != info.serviceName) {
                throw IllegalStateException("The worker is not configured to handle ${info.serviceName}")
            }
            @Suppress("UNCHECKED_CAST")
            if (cryptoService == null) {
                synchronized(cryptoServiceCreateLock) {
                    if (cryptoService == null) {
                        cryptoService = CryptoServiceDecorator.create(
                            cryptoServiceProvider as CryptoServiceProvider<Any>,
                            encryptor.decrypt(serviceConfig),
                            info.maxAttempts,
                            Duration.ofMillis(info.attemptTimeoutMills)
                        )
                    }
                }
            }
            return cryptoService!!
        }
    }
}