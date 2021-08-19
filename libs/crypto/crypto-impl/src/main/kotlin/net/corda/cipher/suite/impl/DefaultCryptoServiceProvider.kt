package net.corda.cipher.suite.impl

import net.corda.crypto.impl.caching.SimplePersistentCacheFactory
import net.corda.crypto.impl.caching.SimplePersistentCacheImpl
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.CryptoService
import net.corda.v5.cipher.suite.CryptoServiceContext
import net.corda.v5.cipher.suite.CryptoServiceProvider
import net.corda.v5.cipher.suite.config.CryptoServiceConfig
import net.corda.v5.crypto.exceptions.CryptoServiceException
import org.hibernate.SessionFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component

@Component
class DefaultCryptoServiceProvider @Activate constructor(
    // that should be the service which can provide the persistence
    // @Reference
    // private val persistence: SessionFactory
) : CryptoServiceProvider<DefaultCryptoServiceConfig> {
    companion object {
        // temporary solution until the persistence is OSGi ready
        internal var sessionFactory: (() -> SessionFactory)? = null
    }

    override val name: String = CryptoServiceConfig.DEFAULT_SERVICE_NAME

    override val configType: Class<DefaultCryptoServiceConfig> = DefaultCryptoServiceConfig::class.java

    override fun getInstance(context: CryptoServiceContext<DefaultCryptoServiceConfig>): CryptoService {
        val schemeMetadata = context.cipherSuiteFactory.getSchemeMap()
        return DefaultCryptoService(
            cache = makeCache(context, schemeMetadata),
            schemeMetadata = schemeMetadata,
            hashingService = context.cipherSuiteFactory.getDigestService()
        )
    }

    private fun makeCache(
        context: CryptoServiceContext<DefaultCryptoServiceConfig>,
        schemeMetadata: CipherSchemeMetadata
    ): DefaultKeyCache {
        if (sessionFactory == null) {
            throw CryptoServiceException("The session factory is not initialized.")
        }
        return DefaultKeyCacheImpl(
            sandboxId = context.sandboxId,
            partition = context.config.partition,
            passphrase = context.config.passphrase,
            salt = context.config.salt,
            cacheFactory = object : SimplePersistentCacheFactory<DefaultCachedKey, DefaultCryptoPersistentKey> {
                override fun create() = SimplePersistentCacheImpl<DefaultCachedKey, DefaultCryptoPersistentKey>(
                    DefaultCryptoPersistentKey::class.java,
                    sessionFactory!!()
                )
            },
            schemeMetadata = schemeMetadata
        )
    }
}