package net.corda.crypto.impl

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import net.corda.crypto.impl.config.CryptoCacheConfig
import net.corda.crypto.impl.lifecycle.clearCache
import net.corda.crypto.impl.persistence.PersistentCacheFactory
import net.corda.crypto.impl.persistence.SigningKeyCache
import net.corda.crypto.impl.persistence.SigningKeyCacheImpl
import net.corda.crypto.CryptoCategories
import net.corda.crypto.FreshKeySigningService
import net.corda.crypto.SigningService
import net.corda.crypto.impl.config.mngCache
import net.corda.lifecycle.Lifecycle
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.CipherSuiteFactory
import net.corda.v5.cipher.suite.CryptoService
import net.corda.v5.cipher.suite.CryptoServiceContext
import net.corda.v5.cipher.suite.CryptoServiceProvider
import net.corda.v5.cipher.suite.config.CryptoLibraryConfig
import net.corda.v5.cipher.suite.config.CryptoServiceConfig
import net.corda.v5.cipher.suite.lifecycle.CryptoLifecycleComponent
import net.corda.v5.crypto.exceptions.CryptoServiceLibraryException
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@Component(service = [CryptoFactory::class])
class CryptoFactoryImpl @Activate constructor(
    @Reference(service = PersistentCacheFactory::class)
    private val persistenceFactory: PersistentCacheFactory,
    @Reference(service = CipherSuiteFactory::class)
    private val cipherSuiteFactory: CipherSuiteFactory,
    @Reference(service = CryptoServiceProvider::class)
    private val cryptoServiceProviders: List<CryptoServiceProvider<*>>,
) : Lifecycle, CryptoLifecycleComponent, CryptoFactory {
    companion object {
        private val logger: Logger = contextLogger()
    }

    private var libraryConfig: CryptoLibraryConfig? = null

    private val lock = ReentrantLock()

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

    override var isRunning: Boolean = false

    override fun start() = lock.withLock {
        logger.info("Starting...")
        isRunning = true
    }

    override fun stop() = lock.withLock {
        logger.info("Stopping...")
        clearCaches()
        libraryConfig = null
        isRunning = false
    }

    override fun handleConfigEvent(config: CryptoLibraryConfig) = lock.withLock {
        logger.info("Received new configuration...")
        clearCaches()
        libraryConfig = config
    }

    override val cipherSchemeMetadata: CipherSchemeMetadata by lazy(LazyThreadSafetyMode.PUBLICATION) {
        cipherSuiteFactory.getSchemeMap()
    }

    @Suppress("TooGenericExceptionCaught")
    override fun getFreshKeySigningService(memberId: String): FreshKeySigningService = lock.withLock {
        try {
            logger.debug("Getting the fresh key service for memberId=$memberId")
            freshKeyServices.getOrPut(memberId) {
                logger.debug("Creating the fresh key service for memberId=$memberId")
                val ledgerConfig = getServiceConfig(memberId, CryptoCategories.LEDGER)
                val freshKeysConfig = getServiceConfig(memberId, CryptoCategories.FRESH_KEYS)
                val ledgerCryptoService = getCryptoService(memberId, CryptoCategories.LEDGER, ledgerConfig)
                val freshKeysCryptoService = getCryptoService(memberId, CryptoCategories.FRESH_KEYS, freshKeysConfig)
                FreshKeySigningServiceImpl(
                    cache = getSigningKeyCache(memberId, libraryConfig!!.mngCache),
                    cryptoService = ledgerCryptoService,
                    freshKeysCryptoService = freshKeysCryptoService,
                    defaultFreshKeySignatureSchemeCodeName = freshKeysConfig.defaultSignatureScheme,
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
            signingServices.getOrPut("$memberId:$category") {
                logger.debug("Creating the signing service for memberId=$memberId")
                val config = getServiceConfig(memberId, category)
                val cryptoService = getCryptoService(memberId, category, config)
                SigningServiceImpl(
                    cache = getSigningKeyCache(memberId, libraryConfig!!.mngCache),
                    cryptoService = cryptoService,
                    defaultSignatureSchemeCodeName = config.defaultSignatureScheme,
                    schemeMetadata = cipherSchemeMetadata
                )
            }
        } catch (e: Throwable) {
            throw CryptoServiceLibraryException("Failed to get signing service for '$memberId:$category'", e)
        }
    }

    private fun getServiceConfig(memberId: String, category: String): CryptoServiceConfig {
        if (!isConfigured) {
            throw IllegalStateException("The factory is not configured.")
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

    private fun clearCaches() {
        signingCaches.clearCache()
        freshKeyServices.clearCache()
        signingServices.clearCache()
    }
}