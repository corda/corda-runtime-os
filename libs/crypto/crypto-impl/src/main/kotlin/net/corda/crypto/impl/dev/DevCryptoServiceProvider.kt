package net.corda.crypto.impl.dev

import net.corda.crypto.impl.persistence.DefaultCryptoKeyCache
import net.corda.crypto.impl.persistence.DefaultCryptoKeyCacheImpl
import net.corda.crypto.impl.persistence.KeyValuePersistenceFactoryProvider
import net.corda.crypto.impl.persistence.SigningKeyCache
import net.corda.crypto.impl.persistence.SigningKeyCacheImpl
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.CryptoService
import net.corda.v5.cipher.suite.CryptoServiceContext
import net.corda.v5.cipher.suite.CryptoServiceProvider
import net.corda.v5.crypto.exceptions.CryptoServiceLibraryException
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ReferenceCardinality
import org.osgi.service.component.annotations.ReferencePolicyOption
import org.slf4j.Logger
import java.util.concurrent.ConcurrentHashMap

@Component(service = [CryptoServiceProvider::class])
class DevCryptoServiceProvider @Activate constructor(
    @Reference(
        service = KeyValuePersistenceFactoryProvider::class,
        cardinality = ReferenceCardinality.AT_LEAST_ONE,
        policyOption = ReferencePolicyOption.GREEDY
    )
    private val persistenceProviders: List<KeyValuePersistenceFactoryProvider>
) : CryptoServiceProvider<DevCryptoServiceConfiguration>, AutoCloseable {
    companion object {
        const val SERVICE_NAME = "dev"
        const val passphrase = "PASSPHRASE"
        const val salt = "SALT"
        private val logger: Logger = contextLogger()
    }

    private val devKeysCache =
        ConcurrentHashMap<String, DefaultCryptoKeyCache>()

    private val signingCache =
        ConcurrentHashMap<String, SigningKeyCache>()

    override val name: String = SERVICE_NAME

    override val configType: Class<DevCryptoServiceConfiguration> = DevCryptoServiceConfiguration::class.java

    override fun getInstance(context: CryptoServiceContext<DevCryptoServiceConfiguration>): CryptoService {
        logger.info(
            "Creating instance of the {} for member {} and category {}",
            DevCryptoService::class.java.name,
            context.memberId,
            context.category
        )
        val cipherSuiteFactory = context.cipherSuiteFactory
        val schemeMetadata = cipherSuiteFactory.getSchemeMap()
        val persistenceFactory = persistenceProviders.firstOrNull {
            it.name == InMemoryKeyValuePersistenceFactoryProvider.NAME
        }?.get() ?: throw CryptoServiceLibraryException(
            "There is no provider with the name '${InMemoryKeyValuePersistenceFactoryProvider.NAME}'",
            isRecoverable = false
        )
        val cryptoServiceCache = devKeysCache.getOrPut(context.memberId) {
            DefaultCryptoKeyCacheImpl(
                memberId = context.memberId,
                passphrase = passphrase,
                salt = salt,
                schemeMetadata = schemeMetadata,
                persistenceFactory = persistenceFactory
            )
        }
        val signingKeyCache = signingCache.getOrPut(context.memberId) {
            SigningKeyCacheImpl(
                memberId = context.memberId,
                keyEncoder = schemeMetadata,
                persistenceFactory = persistenceFactory
            )
        }
        return DevCryptoService(
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