package net.corda.crypto.service.config

import net.corda.crypto.impl.closeGracefully
import net.corda.crypto.impl.config.CryptoLibraryConfigImpl
import net.corda.lifecycle.Lifecycle
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.config.CryptoLibraryConfig
import net.corda.v5.cipher.suite.config.CryptoMemberConfig
import net.corda.v5.cipher.suite.lifecycle.CryptoLifecycleComponent
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger

@Component(service = [MemberConfigPersistence::class])
class MemberConfigPersistenceImpl  @Activate constructor(
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory
) : MemberConfigPersistence, Lifecycle, CryptoLifecycleComponent {
    companion object {
        private val logger: Logger = contextLogger()
    }

    private var impl = Impl(
        subscriptionFactory,
        publisherFactory,
        CryptoLibraryConfigImpl(emptyMap()),
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
        val currentImpl = impl
        impl = Impl(
            subscriptionFactory,
            publisherFactory,
            config,
            logger
        )
        currentImpl.closeGracefully()
    }

    override fun put(memberId: String, entity: CryptoMemberConfig) = impl.put(memberId, entity)

    override fun get(memberId: String): CryptoMemberConfig = impl.get(memberId)

    private class Impl(
        private val subscriptionFactory: SubscriptionFactory,
        private val publisherFactory: PublisherFactory,
        private val config: CryptoLibraryConfig,
        private val logger: Logger
    ) : AutoCloseable {
        fun put(memberId: String, entity: CryptoMemberConfig) {
            TODO("Not yet implemented")
        }

        fun get(memberId: String): CryptoMemberConfig {
            TODO("Not yet implemented")
            /*
        return CryptoMemberConfigImpl(
            getOptionalConfig(memberId) ?: getOptionalConfig(DEFAULT_MEMBER_KEY) ?: emptyMap()
        )
             */
        }

        override fun close() {
            TODO("Not yet implemented")
        }

    }
}