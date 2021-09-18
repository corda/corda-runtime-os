package net.corda.crypto.impl

import net.corda.crypto.impl.config.CryptoRpcConfig
import net.corda.crypto.impl.lifecycle.clearCache
import net.corda.crypto.impl.lifecycle.closeGracefully
import net.corda.crypto.CryptoLibraryFactory
import net.corda.crypto.CryptoLibraryFactoryProvider
import net.corda.crypto.impl.config.rpc
import net.corda.data.crypto.wire.freshkeys.WireFreshKeysRequest
import net.corda.data.crypto.wire.freshkeys.WireFreshKeysResponse
import net.corda.data.crypto.wire.signing.WireSigningRequest
import net.corda.data.crypto.wire.signing.WireSigningResponse
import net.corda.lifecycle.Lifecycle
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.CipherSuiteFactory
import net.corda.v5.cipher.suite.config.CryptoLibraryConfig
import net.corda.v5.cipher.suite.lifecycle.CryptoLifecycleComponent
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@Component(service = [CryptoLibraryFactoryProvider::class])
class CryptoLibraryFactoryProviderImpl @Activate constructor(
    @Reference(service = CipherSuiteFactory::class)
    private val cipherSuiteFactory: CipherSuiteFactory,
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory,
    @Reference(service = MemberIdProvider::class)
    private val memberIdProvider: MemberIdProvider
) : Lifecycle, CryptoLifecycleComponent, CryptoLibraryFactoryProvider {
    companion object {
        private val logger: Logger = contextLogger()
    }

    private val lock = ReentrantLock()

    private var libraryConfig: CryptoLibraryConfig? = null

    private val isConfigured: Boolean
        get() = libraryConfig != null

    private var signingServiceSender: RPCSender<WireSigningRequest, WireSigningResponse>? = null

    private var freshKeysServiceSender: RPCSender<WireFreshKeysRequest, WireFreshKeysResponse>? = null

    private val factories = ConcurrentHashMap<String, CryptoLibraryFactory>()

    override var isRunning: Boolean = false

    override fun start() = lock.withLock {
        logger.info("Starting...")
        isRunning = true
    }

    override fun stop() = lock.withLock {
        logger.info("Stopping...")
        factories.clearCache()
        signingServiceSender?.closeGracefully()
        freshKeysServiceSender?.closeGracefully()
        signingServiceSender = null
        freshKeysServiceSender = null
        libraryConfig = null
        isRunning = false
    }

    override fun handleConfigEvent(config: CryptoLibraryConfig) = lock.withLock {
        logger.info("Received new configuration...")
        libraryConfig = config
    }

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

    private fun getConfig(): CryptoRpcConfig {
        if (!isConfigured) {
            throw IllegalStateException("The provider is not configured.")
        }
        return libraryConfig!!.rpc
    }
}