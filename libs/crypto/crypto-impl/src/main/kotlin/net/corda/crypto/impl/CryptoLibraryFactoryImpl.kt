package net.corda.crypto.impl

import net.corda.cipher.suite.impl.DefaultCryptoServiceProvider
import net.corda.crypto.CryptoCategories
import net.corda.crypto.CryptoLibraryFactory
import net.corda.crypto.FreshKeySigningService
import net.corda.crypto.SigningService
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.CipherSuiteFactory
import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.v5.cipher.suite.config.CryptoServiceConfig
import net.corda.v5.cipher.suite.config.CryptoServiceConfigInfo
import net.corda.v5.crypto.DigestService
import net.corda.v5.crypto.SignatureVerificationService
import org.hibernate.SessionFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.util.concurrent.ConcurrentHashMap

@Component
class CryptoLibraryFactoryImpl @Activate constructor(
    @Reference
    private val cipherSuiteFactory: CipherSuiteFactory
) : CryptoLibraryFactory {
    companion object {
        // temporary solution until the persistence is OSGi ready
        fun setup(factory: (() -> SessionFactory)?) {
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

        internal var sessionFactory: (() -> SessionFactory)? = null
    }

    private val signingCaches = ConcurrentHashMap<String, SigningKeyCache>()

    override fun getFreshKeySigningService(
        passphrase: String,
        defaultSchemeCodeName: String,
        freshKeysDefaultSchemeCodeName: String
    ): FreshKeySigningService {
        val cryptoService = cipherSuiteFactory.getCryptoService(
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
        val freshKeysCryptoService = cipherSuiteFactory.getCryptoService(
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
        val cryptoService = cipherSuiteFactory.getCryptoService(
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
}