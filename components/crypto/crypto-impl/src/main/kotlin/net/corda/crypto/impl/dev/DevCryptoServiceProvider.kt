package net.corda.crypto.impl.dev

import net.corda.crypto.impl.config.CryptoCacheConfig
import net.corda.crypto.impl.persistence.DefaultCryptoCachedKeyInfo
import net.corda.crypto.impl.persistence.DefaultCryptoKeyCacheImpl
import net.corda.crypto.impl.persistence.DefaultCryptoPersistentKeyInfo
import net.corda.crypto.impl.persistence.PersistentCache
import net.corda.crypto.impl.persistence.SigningKeyCacheImpl
import net.corda.crypto.impl.persistence.SigningPersistentKeyInfo
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.CryptoService
import net.corda.v5.cipher.suite.CryptoServiceContext
import net.corda.v5.cipher.suite.CryptoServiceProvider
import org.osgi.service.component.annotations.Component
import org.slf4j.Logger
import java.util.concurrent.ConcurrentHashMap

@Component(service = [CryptoServiceProvider::class])
class DevCryptoServiceProvider : CryptoServiceProvider<DevCryptoServiceConfiguration>, AutoCloseable {
    companion object {
        const val SERVICE_NAME = "dev"
        const val passphrase = "PASSPHRASE"
        const val salt = "SALT"
        private val logger: Logger = contextLogger()
    }

    private val persistentCacheFactory: InMemoryPersistentCacheFactory = InMemoryPersistentCacheFactory()

    private val devKeysPersistence =
        ConcurrentHashMap<String, PersistentCache<DefaultCryptoCachedKeyInfo, DefaultCryptoPersistentKeyInfo>>()

    private val signingPersistence =
        ConcurrentHashMap<String, PersistentCache<SigningPersistentKeyInfo, SigningPersistentKeyInfo>>()

    override val name: String = SERVICE_NAME

    override val configType: Class<DevCryptoServiceConfiguration> = DevCryptoServiceConfiguration::class.java

    override fun getInstance(context: CryptoServiceContext<DevCryptoServiceConfiguration>): CryptoService {
        logger.info(
            "Creating instance of the {} for member {} and category {}",
            DevCryptoService::class.java.name,
            context.sandboxId,
            context.category
        )
        val cipherSuiteFactory = context.cipherSuiteFactory
        val schemeMetadata = cipherSuiteFactory.getSchemeMap()
        val cryptoServiceCache = DefaultCryptoKeyCacheImpl(
            memberId = context.sandboxId,
            passphrase = passphrase,
            salt = salt,
            schemeMetadata = schemeMetadata,
            persistence = devKeysPersistence.getOrPut(context.sandboxId) {
                persistentCacheFactory.createDefaultCryptoPersistentCache(CryptoCacheConfig.default)
            }
        )
        val signingKeyCache = SigningKeyCacheImpl(
            memberId = context.sandboxId,
            keyEncoder = schemeMetadata,
            persistence = signingPersistence.getOrPut(context.sandboxId) {
                persistentCacheFactory.createSigningPersistentCache(CryptoCacheConfig.default)
            }
        )
        return DevCryptoService(
            keyCache = cryptoServiceCache,
            signingCache = signingKeyCache,
            schemeMetadata = schemeMetadata,
            hashingService = cipherSuiteFactory.getDigestService()
        )
    }

    override fun close() {
        devKeysPersistence.clear()
        signingPersistence.clear()
    }
}