package net.corda.crypto.service.impl.signing

import net.corda.crypto.persistence.SigningKeyCacheProvider
import net.corda.crypto.service.CryptoServiceFactory
import net.corda.crypto.service.SigningService
import net.corda.crypto.service.SigningServiceFactory
import net.corda.crypto.service.impl.AbstractComponent
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.crypto.exceptions.CryptoServiceLibraryException
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [SigningServiceFactory::class])
class SigningServiceFactoryImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = CipherSchemeMetadata::class)
    private val schemeMetadata: CipherSchemeMetadata,
    @Reference(service = SigningKeyCacheProvider::class)
    private val cacheProvider: SigningKeyCacheProvider,
    @Reference(service = CryptoServiceFactory::class)
    private val cryptoServiceFactory: CryptoServiceFactory
) : AbstractComponent<SigningServiceFactoryImpl.Impl>(
    coordinatorFactory,
    LifecycleCoordinatorName.forComponent<SigningServiceFactory>(),
    InactiveImpl(),
    setOf(LifecycleCoordinatorName.forComponent<SigningKeyCacheProvider>())
), SigningServiceFactory {
    companion object {
        private val logger = contextLogger()
    }

    interface Impl : AutoCloseable {
        fun getInstance(): SigningService
        override fun close() = Unit
    }

    override fun createActiveImpl(): Impl = ActiveImpl(schemeMetadata, cacheProvider, cryptoServiceFactory)

    override fun createInactiveImpl(): Impl = InactiveImpl()

    override fun getInstance(): SigningService =
        impl.getInstance()

    internal class InactiveImpl : Impl {
        override fun getInstance(): SigningService =
            throw IllegalStateException("The component is in invalid state.")
    }

    internal class ActiveImpl(
        private val schemeMetadata: CipherSchemeMetadata,
        private val cacheProvider: SigningKeyCacheProvider,
        private val cryptoServiceFactory: CryptoServiceFactory
    ) : Impl {
        private val lock = Any()

        @Volatile
        private var signingService: SigningService? = null

        override fun getInstance(): SigningService {
            return try {
                logger.debug("Getting the signing service.")
                if(signingService != null) {
                    signingService!!
                } else {
                    synchronized(lock) {
                        if (signingService != null) {
                            signingService!!
                        } else {
                            logger.info("Creating the signing service.")
                            signingService = SigningServiceImpl(
                                cache = cacheProvider.getInstance(),
                                cryptoServiceFactory = cryptoServiceFactory,
                                schemeMetadata = schemeMetadata
                            )
                            signingService!!
                        }
                    }
                }
            } catch (e: Throwable) {
                throw CryptoServiceLibraryException("Failed to get signing service.", e)
            }
        }
    }
}


