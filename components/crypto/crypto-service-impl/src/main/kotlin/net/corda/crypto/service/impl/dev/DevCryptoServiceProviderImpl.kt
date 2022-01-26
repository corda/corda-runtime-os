package net.corda.crypto.service.impl.dev

import net.corda.crypto.persistence.SigningKeysPersistenceProvider
import net.corda.crypto.persistence.SoftKeysPersistenceProvider
import net.corda.crypto.service.impl.persistence.SigningKeyCache
import net.corda.crypto.service.impl.persistence.SigningKeyCacheImpl
import net.corda.crypto.service.impl.persistence.SoftCryptoKeyCache
import net.corda.crypto.service.impl.persistence.SoftCryptoKeyCacheImpl
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.CipherSuiteFactory
import net.corda.v5.cipher.suite.CryptoService
import net.corda.v5.cipher.suite.CryptoServiceContext
import net.corda.v5.cipher.suite.CryptoServiceProvider
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import java.util.concurrent.ConcurrentHashMap

@Component(service = [CryptoServiceProvider::class])
class DevCryptoServiceProviderImpl @Activate constructor(
    @Reference(service = CipherSuiteFactory::class)
    private val cipherSuiteFactory: CipherSuiteFactory,
    @Reference(service = SoftKeysPersistenceProvider::class)
    private val softPersistenceFactory: SoftKeysPersistenceProvider,
    @Reference(service = SigningKeysPersistenceProvider::class,)
    private val signingPersistenceFactory: SigningKeysPersistenceProvider
): CryptoServiceProvider<DevCryptoServiceConfig>, AutoCloseable {
    companion object {
        const val SERVICE_NAME = "dev"
        const val passphrase = "PASSPHRASE"
        const val salt = "SALT"
        private val logger: Logger = contextLogger()
    }

    private val devKeysCache =
        ConcurrentHashMap<String, SoftCryptoKeyCache>()

    private val signingCache =
        ConcurrentHashMap<String, SigningKeyCache>()

    override val name: String = SERVICE_NAME

    override val configType: Class<DevCryptoServiceConfig> = DevCryptoServiceConfig::class.java

    override fun getInstance(context: CryptoServiceContext<DevCryptoServiceConfig>): CryptoService {
        logger.info(
            "Creating instance of the {} for member {} and category {}",
            DevCryptoService::class.java.name,
            context.memberId,
            context.category
        )
        val schemeMetadata = cipherSuiteFactory.getSchemeMap()
        val cryptoServiceCache = devKeysCache.getOrPut(context.memberId) {
            SoftCryptoKeyCacheImpl(
                tenantId = context.memberId,
                passphrase = passphrase,
                salt = salt,
                schemeMetadata = schemeMetadata,
                persistenceFactory = softPersistenceFactory
            )
        }
        val signingKeyCache = signingCache.getOrPut(context.memberId) {
            SigningKeyCacheImpl(
                tenantId = context.memberId,
                keyEncoder = schemeMetadata,
                persistenceFactory = signingPersistenceFactory
            )
        }
        return DevCryptoService(
            tenantId = context.memberId,
            category = context.category,
            keyCache = cryptoServiceCache,
            signingCache = signingKeyCache,
            schemeMetadata = schemeMetadata,
            hashingService = cipherSuiteFactory.getDigestService()
        )
    }

    override fun close() {
        devKeysCache.clear()
        signingCache.clear()
    }
}