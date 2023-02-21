package net.corda.crypto.service.impl

import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.cipher.suite.PlatformDigestService
import net.corda.crypto.component.impl.AbstractComponent
import net.corda.crypto.component.impl.DependenciesTracker
import net.corda.crypto.persistence.SigningKeyStore
import net.corda.crypto.service.CryptoServiceFactory
import net.corda.crypto.service.SigningService
import net.corda.crypto.service.SigningServiceFactory
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.v5.base.util.debug
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory

@Component(service = [SigningServiceFactory::class])
class SigningServiceFactoryImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = CipherSchemeMetadata::class)
    private val schemeMetadata: CipherSchemeMetadata,
    @Reference(service = SigningKeyStore::class)
    private val store: SigningKeyStore,
    @Reference(service = CryptoServiceFactory::class)
    private val cryptoServiceFactory: CryptoServiceFactory,
    @Reference(service = PlatformDigestService::class)
    private val digestService: PlatformDigestService
) : AbstractComponent<SigningServiceFactoryImpl.Impl>(
    coordinatorFactory = coordinatorFactory,
    myName = LifecycleCoordinatorName.forComponent<SigningServiceFactory>(),
    upstream = DependenciesTracker.Default(
        setOf(
            LifecycleCoordinatorName.forComponent<SigningKeyStore>(),
            LifecycleCoordinatorName.forComponent<CryptoServiceFactory>()
        )
    )
), SigningServiceFactory {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun createActiveImpl(): Impl = Impl(schemeMetadata, store, cryptoServiceFactory, digestService)

    override fun getInstance(): SigningService =
        impl.getInstance()

    class Impl(
        private val schemeMetadata: CipherSchemeMetadata,
        private val store: SigningKeyStore,
        private val cryptoServiceFactory: CryptoServiceFactory,
        private val digestService: PlatformDigestService
    ) : AbstractImpl {
        private val lock = Any()

        @Volatile
        private var signingService: SigningService? = null

        override fun onRegistrationStatusChange(upstreamIsUp: Boolean) {
            if(!upstreamIsUp) {
                synchronized(lock) {
                    signingService = null
                }
            }
        }

        fun getInstance(): SigningService {
            logger.debug { "Getting the signing service." }
            return if (signingService != null) {
                signingService!!
            } else {
                synchronized(lock) {
                    if (signingService != null) {
                        signingService!!
                    } else {
                        logger.info("Creating the signing service.")
                        signingService = SigningServiceImpl(
                            store = store,
                            cryptoServiceFactory = cryptoServiceFactory,
                            schemeMetadata = schemeMetadata,
                            digestService = digestService
                        )
                        signingService!!
                    }
                }
            }
        }
    }
}


