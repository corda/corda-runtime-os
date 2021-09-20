package net.corda.crypto.impl

import net.corda.crypto.impl.lifecycle.clearCache
import net.corda.crypto.impl.lifecycle.closeGracefully
import net.corda.crypto.CryptoLibraryFactory
import net.corda.crypto.CryptoLibraryFactoryProvider
import net.corda.crypto.impl.config.isDev
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

    private val factories = HashMap<String, CryptoLibraryFactory>()

    override var isRunning: Boolean = false

    override fun start() = lock.withLock {
        logger.info("Starting...")
        isRunning = true
    }

    override fun stop() = lock.withLock {
        logger.info("Stopping...")
        reset()
        libraryConfig = null
        isRunning = false
    }

    override fun handleConfigEvent(config: CryptoLibraryConfig) = lock.withLock {
        logger.info("Received new configuration...")
        reset()
        libraryConfig = config
    }

    override fun create(requestingComponent: String): CryptoLibraryFactory = lock.withLock {
        if (!isConfigured) {
            throw IllegalStateException("The provider is not configured.")
        }
        val memberId = memberIdProvider.memberId
        if(libraryConfig!!.isDev) {
            createDevCryptoLibraryFactory(memberId, requestingComponent)
        } else {
            createProductionCryptoLibraryFactory(memberId, requestingComponent)
        }
    }

    private fun createProductionCryptoLibraryFactory(
        memberId: String,
        requestingComponent: String
    ): CryptoLibraryFactory {
        val rpcConfig = libraryConfig!!.rpc
        if (signingServiceSender == null) {
            signingServiceSender = publisherFactory.createRPCSender(rpcConfig.signingRpcConfig)
        }
        if (freshKeysServiceSender == null) {
            freshKeysServiceSender = publisherFactory.createRPCSender(rpcConfig.freshKeysRpcConfig)
        }
        return factories.getOrPut(makeFactoryKey(memberId, requestingComponent)) {
            CryptoLibraryFactoryImpl(
                memberId = memberId,
                requestingComponent = requestingComponent,
                clientTimeout = Duration.ofSeconds(rpcConfig.clientTimeout),
                clientRetries = rpcConfig.clientRetries,
                cipherSuiteFactory = cipherSuiteFactory,
                signingServiceSender = signingServiceSender!!,
                freshKeysServiceSender = freshKeysServiceSender!!
            )
        }
    }

    private fun createDevCryptoLibraryFactory(
        memberId: String,
        requestingComponent: String
    ): CryptoLibraryFactory {
        return factories.getOrPut(makeFactoryKey(memberId, requestingComponent)) {
            CryptoLibraryFactoryDevImpl(
                memberId = memberId,
                cipherSuiteFactory = cipherSuiteFactory
            )
        }
    }

    private fun makeFactoryKey(memberId: String, requestingComponent: String) =
        "$memberId:$requestingComponent"

    private fun reset() {
        factories.clearCache()
        signingServiceSender?.closeGracefully()
        freshKeysServiceSender?.closeGracefully()
        signingServiceSender = null
        freshKeysServiceSender = null
    }
}