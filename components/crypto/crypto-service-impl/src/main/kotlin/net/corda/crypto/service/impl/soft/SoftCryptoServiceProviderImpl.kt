package net.corda.crypto.service.impl.soft

import net.corda.crypto.component.persistence.SoftKeysPersistenceProvider
import net.corda.crypto.impl.LifecycleDependenciesTracker
import net.corda.crypto.impl.LifecycleDependenciesTracker.Companion.track
import net.corda.crypto.service.CryptoServiceProviderWithLifecycle
import net.corda.crypto.service.SoftCryptoServiceConfig
import net.corda.crypto.service.SoftCryptoServiceProvider
import net.corda.crypto.service.impl.persistence.SoftCryptoKeyCache
import net.corda.crypto.service.impl.persistence.SoftCryptoKeyCacheImpl
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.CipherSuiteFactory
import net.corda.v5.cipher.suite.CryptoService
import net.corda.v5.cipher.suite.CryptoServiceContext
import net.corda.v5.cipher.suite.CryptoServiceProvider
import net.corda.v5.crypto.exceptions.CryptoServiceException
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger

@Component(
    service = [
        CryptoServiceProvider::class,
        CryptoServiceProviderWithLifecycle::class,
        SoftCryptoServiceProvider::class
    ]
)
open class SoftCryptoServiceProviderImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = CipherSuiteFactory::class)
    private val cipherSuiteFactory: CipherSuiteFactory,
    @Reference(service = SoftKeysPersistenceProvider::class)
    private val persistenceFactory: SoftKeysPersistenceProvider
) : SoftCryptoServiceProvider, CryptoServiceProvider<SoftCryptoServiceConfig> {

    companion object {
        private val logger: Logger = contextLogger()
    }

    private val lifecycleCoordinator =
        coordinatorFactory.createCoordinator<SoftCryptoServiceProviderImpl>(::eventHandler)

    private var tracker: LifecycleDependenciesTracker? = null

    private var impl: Impl? = null

    override val name: String = "soft"

    override val configType: Class<SoftCryptoServiceConfig> = SoftCryptoServiceConfig::class.java

    override val isRunning: Boolean get() = lifecycleCoordinator.isRunning

    override fun start() {
        logger.info("Starting...")
        lifecycleCoordinator.start()
        lifecycleCoordinator.postEvent(StartEvent())
    }

    override fun stop() {
        logger.info("Stopping...")
        lifecycleCoordinator.postEvent(StopEvent())
        lifecycleCoordinator.stop()
    }

    override fun getInstance(context: CryptoServiceContext<SoftCryptoServiceConfig>): CryptoService =
        impl?.getInstance(context)
            ?: throw CryptoServiceException("Provider haven't been initialised yet.", true)

    @Suppress("UNUSED")
    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        logger.info("Received event {}", event)
        when (event) {
            is StartEvent -> {
                logger.info("Received start event, waiting for UP event from dependencies.")
                tracker?.close()
                tracker = lifecycleCoordinator.track(SoftKeysPersistenceProvider::class.java)
            }
            is StopEvent -> {
                tracker?.close()
                tracker = null
                deleteResources()
            }
            is RegistrationStatusChangeEvent -> {
                if (tracker?.areUpAfter(event, coordinator) == true) {
                    createResources()
                    logger.info("Setting status UP.")
                    this.lifecycleCoordinator.updateStatus(LifecycleStatus.UP)
                } else {
                    deleteResources()
                    logger.info("Setting status DOWN.")
                    this.lifecycleCoordinator.updateStatus(LifecycleStatus.DOWN)
                }
            }
            else -> {
                logger.error("Unexpected event $event!")
            }
        }
    }

    private fun createResources() {
        impl = Impl(cipherSuiteFactory, persistenceFactory)
    }

    private fun deleteResources() {
        impl = null
    }

    private class Impl(
        private val cipherSuiteFactory: CipherSuiteFactory,
        private val persistenceFactory: SoftKeysPersistenceProvider
    ) {
        fun getInstance(context: CryptoServiceContext<SoftCryptoServiceConfig>): CryptoService {
            logger.info(
                "Creating instance of the {} for member {} and category",
                SoftCryptoService::class.java.name,
                context.memberId,
                context.category
            )
            val schemeMetadata = cipherSuiteFactory.getSchemeMap()
            return SoftCryptoService(
                cache = makeCache(context, schemeMetadata),
                schemeMetadata = schemeMetadata,
                hashingService = cipherSuiteFactory.getDigestService()
            )
        }

        private fun makeCache(
            context: CryptoServiceContext<SoftCryptoServiceConfig>,
            schemeMetadata: CipherSchemeMetadata
        ): SoftCryptoKeyCache {
            return SoftCryptoKeyCacheImpl(
                tenantId = context.memberId,
                passphrase = context.config.passphrase,
                salt = context.config.salt,
                schemeMetadata = schemeMetadata,
                persistenceFactory = persistenceFactory
            )
        }
    }
}