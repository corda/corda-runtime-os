package net.corda.crypto.impl

import net.corda.crypto.impl.config.CryptoConfigEvent
import net.corda.crypto.impl.config.CryptoLibraryConfig
import net.corda.crypto.impl.config.CryptoRpcConfig
import net.corda.crypto.impl.lifecycle.CryptoLifecycleComponent
import net.corda.crypto.impl.lifecycle.CryptoServiceLifecycleEventHandler
import net.corda.crypto.impl.lifecycle.clearCache
import net.corda.crypto.impl.lifecycle.closeGracefully
import net.corda.crypto.CryptoLibraryFactory
import net.corda.crypto.CryptoLibraryFactoryProvider
import net.corda.data.crypto.wire.freshkeys.WireFreshKeysRequest
import net.corda.data.crypto.wire.freshkeys.WireFreshKeysResponse
import net.corda.data.crypto.wire.signing.WireSigningRequest
import net.corda.data.crypto.wire.signing.WireSigningResponse
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.StopEvent
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.v5.cipher.suite.CipherSuiteFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.withLock

@Component(service = [CryptoLibraryFactoryProvider::class])
class CryptoLibraryFactoryProviderImpl @Activate constructor(
    @Reference(service = CryptoServiceLifecycleEventHandler::class)
    val cryptoServiceLifecycleEventHandler: CryptoServiceLifecycleEventHandler,
    @Reference(service = CipherSuiteFactory::class)
    private val cipherSuiteFactory: CipherSuiteFactory,
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory,
    @Reference(service = MemberIdProvider::class)
    private val memberIdProvider: MemberIdProvider
) : CryptoLifecycleComponent(cryptoServiceLifecycleEventHandler), CryptoLibraryFactoryProvider {

    private var libraryConfig: CryptoLibraryConfig? = null

    private val isConfigured: Boolean
        get() = libraryConfig != null

    private var signingServiceSender: RPCSender<WireSigningRequest, WireSigningResponse>? = null
    private var freshKeysServiceSender: RPCSender<WireFreshKeysRequest, WireFreshKeysResponse>? = null

    private val factories = ConcurrentHashMap<String, CryptoLibraryFactory>()

    override fun create(requestingComponent: String): CryptoLibraryFactory = lock.withLock {
        val config = getConfig()
        if(signingServiceSender == null) {
            signingServiceSender = publisherFactory.createRPCSender(config.signingRpcConfig)
        }
        if(freshKeysServiceSender == null) {
            freshKeysServiceSender = publisherFactory.createRPCSender(config.freshKeysRpcConfig)
        }
        val memberId = memberIdProvider.memberId
        return factories.getOrPut("$memberId:$requestingComponent") {
            CryptoLibraryFactoryImpl(
                memberId = memberId,
                requestingComponent = requestingComponent,
                clientTimeout = Duration.ofSeconds(config.clientTimeout),
                clientRetries = config.clientRetries,
                cipherSuiteFactory = cipherSuiteFactory,
                signingServiceSender = signingServiceSender!!,
                freshKeysServiceSender = freshKeysServiceSender!!
            )
        }
    }

    override fun stop() = lock.withLock {
        factories.clearCache()
        signingServiceSender?.closeGracefully()
        freshKeysServiceSender?.closeGracefully()
        signingServiceSender = null
        freshKeysServiceSender = null
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

    private fun getConfig(): CryptoRpcConfig {
        if (!isConfigured) {
            throw IllegalStateException("The provider is not configured.")
        }
        return libraryConfig!!.rpc
    }
}