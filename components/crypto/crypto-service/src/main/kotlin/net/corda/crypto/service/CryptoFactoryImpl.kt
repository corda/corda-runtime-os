package net.corda.crypto.service

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import net.corda.crypto.impl.config.CryptoPersistenceConfig
import net.corda.crypto.impl.persistence.SigningKeyCache
import net.corda.crypto.impl.persistence.SigningKeyCacheImpl
import net.corda.crypto.CryptoCategories
import net.corda.crypto.CryptoConsts
import net.corda.crypto.SigningService
import net.corda.crypto.impl.CryptoServiceDecorator
import net.corda.crypto.impl.FreshKeySigningServiceImpl
import net.corda.crypto.impl.SigningServiceImpl
import net.corda.crypto.impl.clearCache
import net.corda.crypto.impl.closeGracefully
import net.corda.crypto.impl.config.CryptoLibraryConfigImpl
import net.corda.crypto.impl.config.publicKeys
import net.corda.crypto.impl.persistence.KeyValuePersistenceFactoryProvider
import net.corda.crypto.component.config.MemberConfigReader
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
import org.osgi.service.component.annotations.ReferenceCardinality
import org.osgi.service.component.annotations.ReferencePolicyOption
import org.slf4j.Logger
import java.util.concurrent.ConcurrentHashMap

@Component(service = [CryptoFactory::class])
@Suppress("LongParameterList")
class CryptoFactoryImpl @Activate constructor(
    @Reference(service = MemberConfigReader::class)
    private val memberConfigReader: MemberConfigReader,
    @Reference(
        service = KeyValuePersistenceFactoryProvider::class,
        cardinality = ReferenceCardinality.AT_LEAST_ONE,
        policyOption = ReferencePolicyOption.GREEDY
    )
    private val persistenceProviders: List<KeyValuePersistenceFactoryProvider>,
    @Reference(service = CipherSuiteFactory::class)
    private val cipherSuiteFactory: CipherSuiteFactory,
    @Reference(
        service = CryptoServiceProvider::class,
        cardinality = ReferenceCardinality.AT_LEAST_ONE,
        policyOption = ReferencePolicyOption.GREEDY
    )
    private val cryptoServiceProviders: List<CryptoServiceProvider<*>>,
) : Lifecycle, CryptoLifecycleComponent, CryptoFactory {
    companion object {
        private val logger: Logger = contextLogger()
    }

    private var impl = Impl(
        memberConfigReader,
        persistenceProviders,
        cipherSuiteFactory,
        cryptoServiceProviders,
        CryptoLibraryConfigImpl(emptyMap()),
        logger
    )

    override var isRunning: Boolean = false

    override fun start() {
        logger.info("Starting...")
        isRunning = true
    }

    override fun stop() {
        logger.info("Stopping...")
        impl.closeGracefully()
        isRunning = false
    }

    override fun handleConfigEvent(config: CryptoLibraryConfig) {
        logger.info("Received new configuration...")
        val currentImpl = impl
        impl = Impl(
            memberConfigReader,
            persistenceProviders,
            cipherSuiteFactory,
            cryptoServiceProviders,
            config,
            logger
        )
        currentImpl.closeGracefully()
    }

    override val cipherSchemeMetadata: CipherSchemeMetadata get() = impl.cipherSchemeMetadata

    override fun getFreshKeySigningService(memberId: String): FreshKeySigningService =
        impl.getFreshKeySigningService(memberId)

    override fun getSigningService(memberId: String, category: String): SigningService =
        impl.getSigningService(memberId, category)

    private class Impl(
        private val memberConfigReader: MemberConfigReader,
        private val persistenceProviders: List<KeyValuePersistenceFactoryProvider>,
        private val cipherSuiteFactory: CipherSuiteFactory,
        private val cryptoServiceProviders: List<CryptoServiceProvider<*>>,
        private val libraryConfig: CryptoLibraryConfig,
        private val logger: Logger
    ) : CryptoFactory, AutoCloseable {
        private val signingCaches = ConcurrentHashMap<String, SigningKeyCache>()
        private val freshKeyServices = ConcurrentHashMap<String, FreshKeySigningService>()
        private val signingServices = ConcurrentHashMap<String, SigningService>()

        private val jsonMapper = JsonMapper.builder().enable(MapperFeature.BLOCK_UNSAFE_POLYMORPHIC_BASE_TYPES).build()
        private val objectMapper = jsonMapper
            .registerModule(JavaTimeModule())
            .registerModule(KotlinModule.Builder().build())
            .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

        override val cipherSchemeMetadata: CipherSchemeMetadata by lazy(LazyThreadSafetyMode.PUBLICATION) {
            cipherSuiteFactory.getSchemeMap()
        }

        override fun getFreshKeySigningService(memberId: String): FreshKeySigningService =
            try {
                logger.debug("Getting the fresh key service for memberId=$memberId")
                freshKeyServices.getOrPut(memberId) {
                    logger.info("Creating the fresh key service for memberId=$memberId")
                    val ledgerConfig = getServiceConfig(memberId, CryptoConsts.CryptoCategories.LEDGER)
                    val freshKeysConfig = getServiceConfig(memberId, CryptoConsts.CryptoCategories.FRESH_KEYS)
                    val ledgerCryptoService = getCryptoService(memberId, CryptoConsts.CryptoCategories.LEDGER, ledgerConfig)
                    val freshKeysCryptoService = getCryptoService(memberId, CryptoConsts.CryptoCategories.FRESH_KEYS, freshKeysConfig)
                    FreshKeySigningServiceImpl(
                        cache = getSigningKeyCache(memberId, libraryConfig.publicKeys),
                        cryptoService = ledgerCryptoService,
                        freshKeysCryptoService = freshKeysCryptoService,
                        defaultFreshKeySignatureSchemeCodeName = freshKeysConfig.defaultSignatureScheme,
                        schemeMetadata = cipherSchemeMetadata
                    )
                }
            } catch (e: Throwable) {
                throw CryptoServiceLibraryException("Failed to get fresh key service for '$memberId'", e)
            }

        override fun getSigningService(memberId: String, category: String): SigningService =
            try {
                logger.debug("Getting the signing service for memberId=$memberId")
                signingServices.getOrPut("$memberId:$category") {
                    logger.info("Creating the signing service for memberId=$memberId")
                    val config = getServiceConfig(memberId, category)
                    val cryptoService = getCryptoService(memberId, category, config)
                    SigningServiceImpl(
                        cache = getSigningKeyCache(memberId, libraryConfig.publicKeys),
                        cryptoService = cryptoService,
                        defaultSignatureSchemeCodeName = config.defaultSignatureScheme,
                        schemeMetadata = cipherSchemeMetadata
                    )
                }
            } catch (e: Throwable) {
                throw CryptoServiceLibraryException("Failed to get signing service for '$memberId:$category'", e)
            }

        override fun close() {
            signingCaches.clearCache()
            freshKeyServices.clearCache()
            signingServices.clearCache()
        }

        private fun getServiceConfig(memberId: String, category: String): CryptoServiceConfig =
            memberConfigReader.get(memberId).getCategory(category)

        @Suppress("UNCHECKED_CAST")
        private fun getCryptoService(memberId: String, category: String, config: CryptoServiceConfig): CryptoService {
            logger.debug("Getting the crypto service '${config.serviceName}' for '$memberId:$category'")
            val provider =
                cryptoServiceProviders.firstOrNull { it.name == config.serviceName } as? CryptoServiceProvider<Any>
                    ?: throw CryptoServiceLibraryException(
                        "Cannot find ${config.serviceName} for '$memberId:$category'",
                        isRecoverable = false
                    )
            try {
                val context = CryptoServiceContext(
                    category = category,
                    memberId = memberId,
                    cipherSuiteFactory = cipherSuiteFactory,
                    config = objectMapper.convertValue(config.serviceConfig, provider.configType)
                )
                return CryptoServiceDecorator(
                    cryptoService = provider.getInstance(context),
                    timeout = config.timeout,
                    retries = config.retries
                )
            } catch (e: Throwable) {
                throw CryptoServiceLibraryException(
                    "Failed to create ${CryptoService::class.java.name} for '$memberId:$category'",
                    e
                )
            }
        }

        private fun getSigningKeyCache(memberId: String, config: CryptoPersistenceConfig): SigningKeyCache {
            val persistenceFactory = persistenceProviders.firstOrNull {
                it.name == config.factoryName
            }?.get() ?: throw CryptoServiceLibraryException(
                "Cannot find ${config.factoryName}",
                isRecoverable = false
            )
            return signingCaches.getOrPut(memberId) {
                SigningKeyCacheImpl(
                    memberId = memberId,
                    keyEncoder = cipherSchemeMetadata,
                    persistenceFactory = persistenceFactory
                )
            }
        }
    }
}
