package net.corda.crypto.service.persistence

import net.corda.crypto.impl.closeGracefully
import net.corda.crypto.impl.config.CryptoLibraryConfigImpl
import net.corda.crypto.impl.config.CryptoPersistenceConfig
import net.corda.crypto.impl.persistence.KeyValuePersistenceFactory
import net.corda.lifecycle.Lifecycle
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.config.CryptoLibraryConfig
import net.corda.v5.cipher.suite.lifecycle.CryptoLifecycleComponent
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger

@Component(service = [KeyValuePersistenceFactoryProvider::class])
class KafkaKeyValuePersistenceFactoryProvider @Activate constructor(
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory
) : KeyValuePersistenceFactoryProvider, Lifecycle, CryptoLifecycleComponent {
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

    override val name: String = CryptoPersistenceConfig.DEFAULT_FACTORY_NAME

    override fun get(): KeyValuePersistenceFactory = impl.get()

    private class Impl(
        private val subscriptionFactory: SubscriptionFactory,
        private val publisherFactory: PublisherFactory,
        private val config: CryptoLibraryConfig,
        private val logger: Logger
    ) : AutoCloseable {
        private val factory by lazy(LazyThreadSafetyMode.PUBLICATION) {
            logger.info("Creating an instance of {}", KafkaKeyValuePersistenceFactory::class.java.name)
            KafkaKeyValuePersistenceFactory(
                subscriptionFactory = subscriptionFactory,
                publisherFactory = publisherFactory,
                config = config
            )
        }

        fun get(): KeyValuePersistenceFactory = factory

        override fun close() {
            factory.closeGracefully()
        }
    }
}