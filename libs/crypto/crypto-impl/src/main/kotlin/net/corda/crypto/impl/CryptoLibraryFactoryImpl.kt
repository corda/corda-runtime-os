package net.corda.crypto.impl

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import net.corda.cipher.suite.impl.CryptoServiceCircuitBreaker
import net.corda.cipher.suite.impl.DefaultCryptoServiceProvider
import net.corda.crypto.CryptoCategories
import net.corda.crypto.CryptoLibraryFactory
import net.corda.crypto.FreshKeySigningService
import net.corda.crypto.SigningService
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.CipherSuiteFactory
import net.corda.v5.cipher.suite.CryptoService
import net.corda.v5.cipher.suite.CryptoServiceContext
import net.corda.v5.cipher.suite.CryptoServiceProvider
import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.v5.cipher.suite.config.CryptoServiceConfig
import net.corda.v5.cipher.suite.config.CryptoServiceConfigInfo
import net.corda.v5.crypto.DigestService
import net.corda.v5.crypto.SignatureVerificationService
import net.corda.v5.crypto.exceptions.CryptoServiceLibraryException
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

@Component(service = [CryptoLibraryFactory::class])
class CryptoLibraryFactoryImpl @Activate constructor(
    @Reference
    private val cipherSuiteFactory: CipherSuiteFactory,

    @Reference
    private val cryptoServiceProviders: List<CryptoServiceProvider<*>>
) : CryptoLibraryFactory {
    companion object {
        // temporary solution until the persistence is OSGi ready
        fun setup(factory: (() -> Any)?) {
            if (factory == null) {
                sessionFactory = null
                DefaultCryptoServiceProvider.sessionFactory
            } else {
                if (sessionFactory == null) {
                    sessionFactory = factory
                }
                if (DefaultCryptoServiceProvider.sessionFactory == null) {
                    DefaultCryptoServiceProvider.sessionFactory = factory
                }
            }
        }

        internal var sessionFactory: (() -> Any)? = null
    }

    private val objectMapper = ObjectMapper()
        .registerModule(JavaTimeModule())
        .registerModule(KotlinModule())
        .enable(MapperFeature.BLOCK_UNSAFE_POLYMORPHIC_BASE_TYPES)
        .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

    private val signingCaches = ConcurrentHashMap<String, SigningKeyCache>()

    override fun getFreshKeySigningService(
        passphrase: String,
        defaultSchemeCodeName: String,
        freshKeysDefaultSchemeCodeName: String
    ): FreshKeySigningService {
        val cryptoService = getCryptoService(
            CryptoServiceConfigInfo(
                category = CryptoCategories.LEDGER,
                effectiveSandboxId = "",
                config = CryptoServiceConfig(
                    serviceName = CryptoServiceConfig.DEFAULT_SERVICE_NAME,
                    serviceConfig = mapOf(
                        "passphrase" to passphrase
                    )
                )
            )
        )
        val freshKeysCryptoService = getCryptoService(
            CryptoServiceConfigInfo(
                category = CryptoCategories.FRESH_KEYS,
                effectiveSandboxId = "",
                config = CryptoServiceConfig(
                    serviceName = CryptoServiceConfig.DEFAULT_SERVICE_NAME,
                    serviceConfig = mapOf(
                        "passphrase" to passphrase
                    )
                )
            )
        )
        return FreshKeySigningServiceImpl(
            cache = createSigningKeyCache(CryptoCategories.LEDGER),
            cryptoService = cryptoService,
            freshKeysCryptoService = freshKeysCryptoService,
            defaultFreshKeySignatureSchemeCodeName = freshKeysDefaultSchemeCodeName,
            schemeMetadata = cipherSuiteFactory.getSchemeMap()
        )
    }

    override fun getSigningService(
        category: String,
        passphrase: String,
        defaultSchemeCodeName: String
    ): SigningService = createSigningService(category, passphrase, defaultSchemeCodeName)

    override fun getSignatureVerificationService(): SignatureVerificationService =
        cipherSuiteFactory.getSignatureVerificationService()

    override fun getKeyEncodingService(): KeyEncodingService =
        cipherSuiteFactory.getSchemeMap()

    override fun getCipherSchemeMetadata(): CipherSchemeMetadata =
        cipherSuiteFactory.getSchemeMap()

    override fun getDigestService(): DigestService =
        cipherSuiteFactory.getDigestService()

    private fun createSigningService(
        category: String,
        passphrase: String,
        defaultSchemeCodeName: String
    ): SigningService {
        require(sessionFactory != null)
        val cryptoService = getCryptoService(
            CryptoServiceConfigInfo(
                category = category,
                effectiveSandboxId = "",
                config = CryptoServiceConfig(
                    serviceName = CryptoServiceConfig.DEFAULT_SERVICE_NAME,
                    serviceConfig = mapOf(
                        "passphrase" to passphrase
                    )
                )
            )
        )
        return SigningServiceImpl(
            cache = createSigningKeyCache(category),
            cryptoService = cryptoService,
            defaultSignatureSchemeCodeName = defaultSchemeCodeName,
            schemeMetadata = cipherSuiteFactory.getSchemeMap()
        )
    }

    private fun createSigningKeyCache(category: String): SigningKeyCache {
        return signingCaches.getOrPut(category) {
            SigningKeyCacheImpl(
                sandboxId = "",
                sessionFactory = sessionFactory!!,
                keyEncoder = getKeyEncodingService()
            )
        }
    }

    @Suppress("UNCHECKED_CAST", "TooGenericExceptionCaught", "MaxLineLength", "ThrowsCount")
    private fun getCryptoService(info: CryptoServiceConfigInfo): CryptoService {
        val provider = cryptoServiceProviders.firstOrNull { it.name == info.config.serviceName } as? CryptoServiceProvider<Any>
            ?: throw CryptoServiceLibraryException("Cannot find ${info.config.serviceName} implementing ${CryptoServiceProvider::class.java.name}")
        try {
            val context = CryptoServiceContext(
                category = info.category,
                sandboxId = info.effectiveSandboxId,
                cipherSuiteFactory = cipherSuiteFactory,
                config = objectMapper.convertValue(info.config.serviceConfig, provider.configType)
            )
            return CryptoServiceCircuitBreaker(
                cryptoService = provider.getInstance(context),
                timeout = Duration.ofSeconds(15)
            )
        } catch (e: CryptoServiceLibraryException) {
            throw e
        } catch (e: Exception) {
            throw CryptoServiceLibraryException("Failed to create implementation of ${CryptoService::class.java.name}", e)
        }
    }
}