package net.corda.crypto.client

import net.corda.crypto.CryptoLibraryClientsFactory
import net.corda.crypto.CryptoLibraryClientsFactoryProvider
import net.corda.crypto.CryptoLibrarySandboxClientsFactoryProvider
import net.corda.crypto.component.config.CryptoRpcConfig
import net.corda.crypto.component.config.rpc
import net.corda.crypto.impl.clearCache
import net.corda.crypto.impl.closeGracefully
import net.corda.crypto.impl.config.isDev
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

@Component(
    service = [
        CryptoLibrarySandboxClientsFactoryProvider::class,
        CryptoLibraryClientsFactoryProvider::class,
        CryptoLibraryClientsFactoryProviderImpl::class
    ]
)
open class CryptoLibraryClientsFactoryProviderImpl @Activate constructor(
    @Reference(service = CipherSuiteFactory::class)
    private val cipherSuiteFactory: CipherSuiteFactory,
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory,
    @Reference(service = MemberIdProvider::class)
    private val memberIdProvider: MemberIdProvider
) : Lifecycle, CryptoLifecycleComponent, CryptoLibrarySandboxClientsFactoryProvider,
    CryptoLibraryClientsFactoryProvider {

    private interface Impl : CryptoLibrarySandboxClientsFactoryProvider, CryptoLibraryClientsFactoryProvider

    companion object {
        private val logger: Logger = contextLogger()

        private fun makeFactoryKey(memberId: String, requestingComponent: String) =
            "$memberId:$requestingComponent"
    }

    private var impl: Impl = DevImpl(
        cipherSuiteFactory,
        memberIdProvider,
        logger
    )

    override var isRunning: Boolean = false

    override fun start() {
        logger.info("Starting...")
        isRunning = true
    }

    override fun stop() {
        logger.info("Stopping...")
        impl.closeGracefully()
        isRunning = false
    }

    override fun handleConfigEvent(config: CryptoLibraryConfig) {
        logger.info("Received new configuration...")
        // thread safe, as another thread will go through the same sequence
        val currentImpl = impl
        impl = if (config.isDev) {
            DevImpl(
                cipherSuiteFactory,
                memberIdProvider,
                logger
            )
        } else {
            ProductionImpl(
                cipherSuiteFactory,
                memberIdProvider,
                config.rpc,
                publisherFactory,
                logger
            )
        }
        currentImpl.closeGracefully()
    }

    override fun get(requestingComponent: String): CryptoLibraryClientsFactory =
        impl.get(requestingComponent)

    override fun get(memberId: String, requestingComponent: String): CryptoLibraryClientsFactory =
        impl.get(memberId, requestingComponent)

    private class DevImpl(
        private val cipherSuiteFactory: CipherSuiteFactory,
        private val memberIdProvider: MemberIdProvider,
        private val logger: Logger
    ) : Impl {
        private val factories = ConcurrentHashMap<String, CryptoLibraryClientsFactory>()

        override fun get(requestingComponent: String): CryptoLibraryClientsFactory =
            get(memberIdProvider.memberId, requestingComponent)

        override fun get(memberId: String, requestingComponent: String): CryptoLibraryClientsFactory {
            val key = makeFactoryKey(memberId, requestingComponent)
            logger.warn(
                "Using dev provider to get {} for {}",
                CryptoLibraryClientsFactory::class.java.name,
                key
            )
            return factories.getOrPut(key) {
                logger.warn(
                    "Using dev provider to create {} for {}",
                    CryptoLibraryClientsFactory::class.java.name,
                    key
                )
                CryptoLibraryClientsFactoryDevImpl(
                    memberId = memberId,
                    cipherSuiteFactory = cipherSuiteFactory
                )
            }
        }

        override fun close() {
            factories.clearCache()
        }
    }

    private class ProductionImpl(
        private val cipherSuiteFactory: CipherSuiteFactory,
        private val memberIdProvider: MemberIdProvider,
        private var config: CryptoRpcConfig,
        publisherFactory: PublisherFactory,
        private val logger: Logger
    ) : Impl {
        private var signingServiceSender: RPCSender<WireSigningRequest, WireSigningResponse> =
            publisherFactory.createRPCSender(config.signingRpcConfig)

        private var freshKeysServiceSender: RPCSender<WireFreshKeysRequest, WireFreshKeysResponse> =
            publisherFactory.createRPCSender(config.freshKeysRpcConfig)

        private val factories = ConcurrentHashMap<String, CryptoLibraryClientsFactory>()

        override fun get(requestingComponent: String): CryptoLibraryClientsFactory =
            get(memberIdProvider.memberId, requestingComponent)

        override fun get(memberId: String, requestingComponent: String): CryptoLibraryClientsFactory {
            val key = makeFactoryKey(memberId, requestingComponent)
            logger.debug("Getting {} for {}", CryptoLibraryClientsFactory::class.java.name, key)
            return factories.getOrPut(key) {
                logger.info("Creating {} for {}", CryptoLibraryClientsFactory::class.java.name, key)
                CryptoLibraryClientsFactoryImpl(
                    memberId = memberId,
                    requestingComponent = requestingComponent,
                    clientTimeout = Duration.ofSeconds(config.clientTimeout),
                    clientRetries = config.clientRetries,
                    schemeMetadata = cipherSuiteFactory.getSchemeMap(),
                    signingServiceSender = signingServiceSender,
                    freshKeysServiceSender = freshKeysServiceSender
                )
            }
        }

        override fun close() {
            factories.clearCache()
            signingServiceSender.closeGracefully()
            freshKeysServiceSender.closeGracefully()
        }
    }
}
