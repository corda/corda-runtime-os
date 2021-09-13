package net.corda.crypto.impl

import net.corda.cipher.suite.impl.config.CryptoConfigEvent
import net.corda.cipher.suite.impl.config.CryptoLibraryConfig
import net.corda.cipher.suite.impl.lifecycle.CryptoLifecycleComponent
import net.corda.cipher.suite.impl.lifecycle.CryptoServiceLifecycleEventHandler
import net.corda.crypto.CryptoLibraryFactory
import net.corda.crypto.CryptoLibraryFactoryProvider
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.StopEvent
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.v5.cipher.suite.CipherSuiteFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import kotlin.concurrent.withLock

@Component(service = [CryptoLibraryFactoryProvider::class])
class CryptoLibraryFactoryProviderImpl @Activate constructor(
    @Reference(service = CryptoServiceLifecycleEventHandler::class)
    val cryptoServiceLifecycleEventHandler: CryptoServiceLifecycleEventHandler,
    @Reference(service = CipherSuiteFactory::class)
    private val cipherSuiteFactory: CipherSuiteFactory,
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory
) : CryptoLifecycleComponent(cryptoServiceLifecycleEventHandler), CryptoLibraryFactoryProvider {

    private var libraryConfig: CryptoLibraryConfig? = null

    private val isConfigured: Boolean
        get() = libraryConfig != null

    override fun create(memberId: String, requestingComponent: String): CryptoLibraryFactory {
        if (!isConfigured) {
            throw IllegalStateException("The provider is not configured.")
        }
        return CryptoLibraryFactoryImpl(
            memberId = memberId,
            requestingComponent = requestingComponent,
            cipherSuiteFactory = cipherSuiteFactory,
            publisherFactory = publisherFactory
        )
    }

    override fun stop() = lock.withLock {
        libraryConfig = null
        super.stop()
    }

    override fun handleLifecycleEvent(event: LifecycleEvent) = lock.withLock {
        logger.info("LifecycleEvent received: $event")
        when (event) {
            is CryptoConfigEvent -> {
                logger.info("Received config event {}", event::class.qualifiedName)
                libraryConfig = event.config
            }
            is StopEvent -> {
                stop()
                logger.info("Received stop event")
            }
        }
    }
}