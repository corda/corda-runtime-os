package net.corda.crypto.client

import net.corda.crypto.CryptoLibraryClientsFactory
import net.corda.crypto.impl.lifecycle.clearCache
import net.corda.crypto.impl.lifecycle.closeGracefully
import net.corda.crypto.CryptoLibraryClientsFactoryProvider
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

@Component(service = [CryptoLibraryClientsFactoryProvider::class])
class CryptoLibraryClientsFactoryProviderImpl @Activate constructor(
    @Reference(service = CipherSuiteFactory::class)
    private val cipherSuiteFactory: CipherSuiteFactory,
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory,
    @Reference(service = MemberIdProvider::class)
    private val memberIdProvider: MemberIdProvider
) : Lifecycle, CryptoLifecycleComponent, CryptoLibraryClientsFactoryProvider {
    companion object {
        private val logger: Logger = contextLogger()
    }

    private val lock = ReentrantLock()

    private var libraryConfig: CryptoLibraryConfig? = null

    private val isConfigured: Boolean
        get() = libraryConfig != null

    private var signingServiceSender: RPCSender<WireSigningRequest, WireSigningResponse>? = null

    private var freshKeysServiceSender: RPCSender<WireFreshKeysRequest, WireFreshKeysResponse>? = null

    private val factories = HashMap<String, CryptoLibraryClientsFactory>()

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

    override fun create(requestingComponent: String): CryptoLibraryClientsFactory = lock.withLock {
        if (!isConfigured) {
            logger.warn("The provider is not yet configured, ...using the dev factory.")
        }
        val memberId = memberIdProvider.memberId
        if(libraryConfig == null || libraryConfig!!.isDev) {
            createDevFactory(memberId, requestingComponent)
        } else {
            createProductionFactory(memberId, requestingComponent)
        }
    }

    private fun createProductionFactory(
        memberId: String,
        requestingComponent: String
    ): CryptoLibraryClientsFactory {
        val rpcConfig = libraryConfig!!.rpc
        if (signingServiceSender == null) {
            signingServiceSender = publisherFactory.createRPCSender(rpcConfig.signingRpcConfig)
        }
        if (freshKeysServiceSender == null) {
            freshKeysServiceSender = publisherFactory.createRPCSender(rpcConfig.freshKeysRpcConfig)
        }
        return factories.getOrPut(makeFactoryKey(memberId, requestingComponent)) {
            CryptoLibraryClientsFactoryImpl(
                memberId = memberId,
                requestingComponent = requestingComponent,
                clientTimeout = Duration.ofSeconds(rpcConfig.clientTimeout),
                clientRetries = rpcConfig.clientRetries,
                schemeMetadata = cipherSuiteFactory.getSchemeMap(),
                signingServiceSender = signingServiceSender!!,
                freshKeysServiceSender = freshKeysServiceSender!!
            )
        }
    }

    private fun createDevFactory(
        memberId: String,
        requestingComponent: String
    ): CryptoLibraryClientsFactory {
        return factories.getOrPut(makeFactoryKey(memberId, requestingComponent)) {
            CryptoLibraryClientsFactoryDevImpl(
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