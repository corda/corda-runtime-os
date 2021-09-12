package net.corda.components.crypto

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import net.corda.components.crypto.services.CryptoServiceCircuitBreaker
import net.corda.components.crypto.config.CryptoCacheConfig
import net.corda.components.crypto.config.CryptoConfigEvent
import net.corda.components.crypto.config.CryptoLibraryConfig
import net.corda.components.crypto.services.FreshKeySigningServiceImpl
import net.corda.components.crypto.services.SigningServiceImpl
import net.corda.components.crypto.services.persistence.PersistentCacheFactory
import net.corda.components.crypto.services.persistence.SigningKeyCache
import net.corda.components.crypto.services.persistence.SigningKeyCacheImpl
import net.corda.crypto.CryptoCategories
import net.corda.crypto.FreshKeySigningService
import net.corda.crypto.SigningService
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.StopEvent
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.CipherSuiteFactory
import net.corda.v5.cipher.suite.CryptoService
import net.corda.v5.cipher.suite.CryptoServiceContext
import net.corda.v5.cipher.suite.CryptoServiceProvider
import net.corda.v5.cipher.suite.config.CryptoServiceConfig
import net.corda.v5.cipher.suite.schemes.ECDSA_SECP256R1_CODE_NAME
import net.corda.v5.crypto.exceptions.CryptoServiceLibraryException
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import kotlin.concurrent.withLock

@Component(service = [CryptoFactory::class])
class CryptoFactoryImpl @Activate constructor(
    @Reference(service = CryptoServiceLifecycleEventHandler::class)
    private val cryptoServiceLifecycleEventHandler: CryptoServiceLifecycleEventHandler,
    @Reference(service = PersistentCacheFactory::class)
    private val persistenceFactory: PersistentCacheFactory,
    @Reference(service = CipherSuiteFactory::class)
    private val cipherSuiteFactory: CipherSuiteFactory,
    @Reference(service = CryptoServiceProvider::class)
    private val cryptoServiceProviders: List<CryptoServiceProvider<*>>,
) : CryptoLifecycleComponent(cryptoServiceLifecycleEventHandler), CryptoFactory {
    private var libraryConfig: CryptoLibraryConfig? = null

    private val isConfigured: Boolean
        get() = libraryConfig != null

    private val signingCaches = HashMap<String, SigningKeyCache>()

    private val freshKeyServices = HashMap<String, FreshKeySigningService>()

    private val signingServices = HashMap<String, SigningService>()

    private val objectMapper = ObjectMapper()
        .registerModule(JavaTimeModule())
        .registerModule(KotlinModule())
        .enable(MapperFeature.BLOCK_UNSAFE_POLYMORPHIC_BASE_TYPES)
        .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)


    override fun stop() = lock.withLock {
        clearCaches()
        libraryConfig = null
        super.stop()
    }

    override val cipherSchemeMetadata: CipherSchemeMetadata by lazy {
        cipherSuiteFactory.getSchemeMap()
    }

    @Suppress("TooGenericExceptionCaught")
    override fun getFreshKeySigningService(memberId: String): FreshKeySigningService = lock.withLock {
        try {
            logger.debug("Getting the fresh key service for memberId=$memberId")
            return freshKeyServices.getOrPut(memberId) {
                logger.debug("Creating the fresh key service for memberId=$memberId")
                val ledgerConfig = getServiceConfig(memberId, CryptoCategories.LEDGER)
                val freshKeysConfig = getServiceConfig(memberId, CryptoCategories.FRESH_KEYS)
                val ledgerCryptoService = getCryptoService(memberId, CryptoCategories.LEDGER, ledgerConfig)
                val freshKeysCryptoService = getCryptoService(memberId, CryptoCategories.FRESH_KEYS, freshKeysConfig)
                FreshKeySigningServiceImpl(
                    cache = getSigningKeyCache(memberId, libraryConfig!!.mngCache),
                    cryptoService = ledgerCryptoService,
                    freshKeysCryptoService = freshKeysCryptoService,
                    defaultFreshKeySignatureSchemeCodeName = ECDSA_SECP256R1_CODE_NAME, //TODO2: freshKeysConfig.defaultSignatureScheme,
                    schemeMetadata = cipherSchemeMetadata
                )
            }
        } catch (e: Throwable) {
            throw CryptoServiceLibraryException("Failed to get fresh key service for '$memberId'", e)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override fun getSigningService(memberId: String, category: String): SigningService = lock.withLock {
        try {
            logger.debug("Getting the signing service for memberId=$memberId")
            return signingServices.getOrPut("$memberId:$category") {
                logger.debug("Creating the signing service for memberId=$memberId")
                val config = getServiceConfig(memberId, category)
                val cryptoService = getCryptoService(memberId, category, config)
                return SigningServiceImpl(
                    cache = getSigningKeyCache(memberId, libraryConfig!!.mngCache),
                    cryptoService = cryptoService,
                    defaultSignatureSchemeCodeName = ECDSA_SECP256R1_CODE_NAME, //TODO2: config.defaultSignatureScheme,
                    schemeMetadata = cipherSchemeMetadata
                )
            }
        } catch (e: Throwable) {
            throw CryptoServiceLibraryException("Failed to get signing service for '$memberId:$category'", e)
        }
    }

    private fun getServiceConfig(memberId: String, category: String): CryptoServiceConfig {
        if (!isConfigured) {
            throw IllegalStateException("The factory haven't been started.")
        }
        return libraryConfig!!.getMember(memberId).getCategory(category)
    }

    @Suppress("UNCHECKED_CAST", "TooGenericExceptionCaught")
    private fun getCryptoService(memberId: String, category: String, config: CryptoServiceConfig): CryptoService {
        logger.debug("Getting the crypto service '${config.serviceName}' for '$memberId:$category'")
        val provider =
            cryptoServiceProviders.firstOrNull { it.name == config.serviceName } as? CryptoServiceProvider<Any>
                ?: throw CryptoServiceLibraryException("Cannot find ${config.serviceName} for '$memberId:$category'}")
        try {
            val context = CryptoServiceContext(
                category = category,
                sandboxId = memberId,
                cipherSuiteFactory = cipherSuiteFactory,
                config = objectMapper.convertValue(config.serviceConfig, provider.configType)
            )
            return CryptoServiceCircuitBreaker(
                cryptoService = provider.getInstance(context),
                timeout = config.timeout
            )
        } catch (e: Throwable) {
            throw CryptoServiceLibraryException(
                "Failed to create ${CryptoService::class.java.name} for '$memberId:$category'",
                e
            )
        }
    }

    private fun getSigningKeyCache(memberId: String, config: CryptoCacheConfig): SigningKeyCache {
        return signingCaches.getOrPut(memberId) {
            SigningKeyCacheImpl(
                memberId = memberId,
                keyEncoder = cipherSchemeMetadata,
                persistence = persistenceFactory.createSigningPersistentCache(config)
            )
        }
    }

    override fun handleLifecycleEvent(event: LifecycleEvent) = lock.withLock {
        logger.info("LifecycleEvent received: $event")
        when (event) {
            is CryptoConfigEvent -> {
                logger.info("Received config event {}", event::class.qualifiedName)
                reset(event.config)
            }
            is StopEvent -> {
                stop()
                logger.info("Received stop event")
            }
        }
    }

    private fun reset(config: CryptoLibraryConfig) {
        clearCaches()
        libraryConfig = config
    }

    private fun clearCaches() {
        signingCaches.clearCache()
        freshKeyServices.clearCache()
        signingServices.clearCache()
    }
}