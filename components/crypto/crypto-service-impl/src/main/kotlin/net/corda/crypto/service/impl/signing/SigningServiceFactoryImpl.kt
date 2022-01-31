package net.corda.crypto.service.impl.signing

import net.corda.crypto.persistence.SigningKeysPersistenceProvider
import net.corda.crypto.service.CryptoServiceFactory
import net.corda.crypto.service.SigningService
import net.corda.crypto.service.SigningServiceFactory
import net.corda.crypto.service.impl.persistence.SigningKeyCacheImpl
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.crypto.exceptions.CryptoServiceLibraryException
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.util.concurrent.ConcurrentHashMap

@Component(service = [SigningServiceFactory::class])
class SigningServiceFactoryImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = CipherSchemeMetadata::class)
    private val schemeMetadata: CipherSchemeMetadata,
    @Reference(service = SigningKeysPersistenceProvider::class)
    private val persistenceFactory: SigningKeysPersistenceProvider,
    @Reference(service = CryptoServiceFactory::class)
    private val cryptoServiceFactory: CryptoServiceFactory
) : SigningServiceFactory {
    companion object {
        private val logger = contextLogger()
    }

    private val lifecycleCoordinator =
        coordinatorFactory.createCoordinator<SigningServiceFactory>(::eventHandler)

    private var registrationHandle: RegistrationHandle? = null

    private var impl: Impl? = null

    override val isRunning: Boolean get() = lifecycleCoordinator.isRunning

    override fun start() {
        logger.info("Starting...")
        lifecycleCoordinator.start()
    }

    override fun stop() {
        logger.info("Stopping...")
        lifecycleCoordinator.stop()
    }

    override fun getInstance(tenantId: String): SigningService =
        impl?.getInstance(tenantId) ?: throw IllegalStateException("The factory is not initialised yet.")

    @Suppress("UNUSED_PARAMETER")
    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        logger.info("Received event {}", event)
        when (event) {
            is StartEvent -> {
                logger.info("Received start event, starting wait for UP event from dependencies.")
                registrationHandle?.close()
                registrationHandle = lifecycleCoordinator.followStatusChangesByName(
                    setOf(LifecycleCoordinatorName.forComponent<SigningKeysPersistenceProvider>())
                )
            }
            is StopEvent -> {
                registrationHandle?.close()
                registrationHandle = null
                deleteResources()
            }
            is RegistrationStatusChangeEvent -> {
                if (event.status == LifecycleStatus.UP) {
                    createResources()
                    logger.info("Setting status UP.")
                    lifecycleCoordinator.updateStatus(LifecycleStatus.UP)
                } else {
                    deleteResources()
                    logger.info("Setting status DOWN.")
                    lifecycleCoordinator.updateStatus(LifecycleStatus.DOWN)
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
        current?.close()
    }

    private fun createResources() {
        val current = impl
        impl = Impl(
            schemeMetadata,
            persistenceFactory,
            cryptoServiceFactory
        )
        current?.close()
    }


    private class Impl(
        private val schemeMetadata: CipherSchemeMetadata,
        private val persistenceFactory: SigningKeysPersistenceProvider,
        private val cryptoServiceFactory: CryptoServiceFactory
    ) : AutoCloseable {
        private val signingServices = ConcurrentHashMap<String, SigningService>()

        fun getInstance(tenantId: String): SigningService {
            return try {
                logger.debug("Getting the signing service for tenant={}", tenantId)
                signingServices.computeIfAbsent(tenantId) {
                    logger.info("Creating the signing service for tenant={}", tenantId)
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


