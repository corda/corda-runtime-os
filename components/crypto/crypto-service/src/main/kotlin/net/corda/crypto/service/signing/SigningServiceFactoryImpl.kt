package net.corda.crypto.service.signing

import net.corda.crypto.component.persistence.SigningKeysPersistenceProvider
import net.corda.crypto.service.persistence.SigningKeyCacheImpl
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.CipherSuiteFactory
import net.corda.v5.crypto.exceptions.CryptoServiceLibraryException
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.util.concurrent.ConcurrentHashMap

@Component(service = [SigningServiceFactory::class])
class SigningServiceFactoryImpl : SigningServiceFactory {
    companion object {
        private val logger = contextLogger()
    }

    @Volatile
    @Reference(service = CipherSuiteFactory::class)
    lateinit var cipherSuiteFactory: CipherSuiteFactory

    @Volatile
    @Reference(service = SigningKeysPersistenceProvider::class)
    lateinit var persistenceFactory: SigningKeysPersistenceProvider

    @Volatile
    @Reference(service = CryptoServiceFactory::class)
    lateinit var cryptoServiceFactory: CryptoServiceFactory

    private var impl: Impl? = null

    @Activate
    fun activate() {
        impl = Impl(
            cipherSuiteFactory,
            persistenceFactory,
            cryptoServiceFactory
        )
    }

    override fun getInstance(tenantId: String): SigningService =
        impl?.getInstance(tenantId) ?: throw IllegalStateException("The factory is not initialised yet.")

    private class Impl(
        private val cipherSuiteFactory: CipherSuiteFactory,
        private val persistenceFactory: SigningKeysPersistenceProvider,
        private val cryptoServiceFactory: CryptoServiceFactory
    ): SigningServiceFactory {
        private val signingServices = ConcurrentHashMap<String, SigningService>()

        override fun getInstance(tenantId: String): SigningService {
            return try {
                logger.debug("Getting the signing service for tenant={}", tenantId)
                signingServices.computeIfAbsent(tenantId) {
                    logger.info("Creating the signing service for tenant={}", tenantId)
                    val schemeMetadata = cipherSuiteFactory.getSchemeMap()
                    SigningServiceImpl(
                        tenantId = tenantId,
                        cache = SigningKeyCacheImpl(
                            tenantId = tenantId,
                            keyEncoder = schemeMetadata,
                            persistenceFactory = persistenceFactory
                        ),
                        cryptoServiceFactory = cryptoServiceFactory,
                        schemeMetadata = schemeMetadata
                    )
                }
            } catch (e: Throwable) {
                throw CryptoServiceLibraryException("Failed to get signing service for $tenantId", e)
            }
        }
    }
}