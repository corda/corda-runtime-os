package net.corda.crypto.service.impl.signing

import net.corda.crypto.component.persistence.SigningKeysPersistenceProvider
import net.corda.crypto.impl.closeGracefully
import net.corda.crypto.service.CryptoServiceFactory
import net.corda.crypto.service.SigningService
import net.corda.crypto.service.SigningServiceFactory
import net.corda.crypto.impl.LifecycleDependencies
import net.corda.crypto.service.impl.persistence.SigningKeyCacheImpl
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.CipherSuiteFactory
import net.corda.v5.crypto.exceptions.CryptoServiceLibraryException
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.util.concurrent.ConcurrentHashMap

@Component(service = [SigningServiceFactory::class])
class SigningServiceFactoryImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = CipherSuiteFactory::class)
    private val cipherSuiteFactory: CipherSuiteFactory,
    @Reference(service = SigningKeysPersistenceProvider::class)
    private val persistenceFactory: SigningKeysPersistenceProvider,
    @Reference(service = CryptoServiceFactory::class)
    private val cryptoServiceFactory: CryptoServiceFactory
) : SigningServiceFactory {
    companion object {
        private val logger = contextLogger()
    }

    private val coordinator =
        coordinatorFactory.createCoordinator<SigningServiceFactory>(::eventHandler)

    private var dependencies: LifecycleDependencies? = null

    private var impl: Impl? = null

    override val isRunning: Boolean get() = coordinator.isRunning

    override fun start() {
        logger.info("Starting...")
        coordinator.start()
        coordinator.postEvent(StartEvent())
    }

    override fun stop() {
        logger.info("Stopping...")
        coordinator.postEvent(StopEvent())
        coordinator.stop()
    }

    override fun getInstance(tenantId: String): SigningService =
        impl?.getInstance(tenantId) ?: throw IllegalStateException("The factory is not initialised yet.")

    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        logger.info("Received event {}", event)
        when (event) {
            is StartEvent -> {
                logger.info("Received start event, waiting for UP event from dependencies.")
                dependencies?.close()
                dependencies = LifecycleDependencies(
                    coordinator,
                    SigningKeysPersistenceProvider::class.java,
                    CryptoServiceFactory::class.java
                )
            }
            is StopEvent -> {
                dependencies?.close()
                dependencies = null
                deleteResources()
            }
            is RegistrationStatusChangeEvent -> {
                logger.info("Registration status change received from dependencies: ${event.status.name}.")
                if (dependencies?.areUpAfter(event) == true) {
                    createResources()
                    logger.info("Setting status UP.")
                    coordinator.updateStatus(LifecycleStatus.UP)
                } else {
                    deleteResources()
                    logger.info("Setting status DOWN.")
                    coordinator.updateStatus(LifecycleStatus.DOWN)
                }
            }
            else -> {
                logger.error("Unexpected event $event!")
            }
        }
    }

    private fun deleteResources() {
        val current = impl
        impl = null
        current?.closeGracefully()
    }
    private fun createResources() {
        val current = impl
        impl = Impl(
            cipherSuiteFactory,
            persistenceFactory,
            cryptoServiceFactory
        )
        current?.closeGracefully()
    }


    private class Impl(
        private val cipherSuiteFactory: CipherSuiteFactory,
        private val persistenceFactory: SigningKeysPersistenceProvider,
        private val cryptoServiceFactory: CryptoServiceFactory
    ) : AutoCloseable {
        private val signingServices = ConcurrentHashMap<String, SigningService>()

        fun getInstance(tenantId: String): SigningService {
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

        override fun close() {
            signingServices.clear()
        }
    }
}


