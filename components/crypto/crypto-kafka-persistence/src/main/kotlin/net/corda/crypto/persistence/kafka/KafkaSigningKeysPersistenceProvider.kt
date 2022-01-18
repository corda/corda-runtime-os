package net.corda.crypto.persistence.kafka

import net.corda.crypto.component.persistence.KeyValueMutator
import net.corda.crypto.component.persistence.KeyValuePersistence
import net.corda.crypto.component.persistence.SigningKeysPersistenceProvider
import net.corda.crypto.component.persistence.config.signingPersistence
import net.corda.crypto.impl.closeGracefully
import net.corda.data.crypto.persistence.SigningKeysRecord
import net.corda.lifecycle.Lifecycle
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.config.CryptoLibraryConfig
import net.corda.v5.cipher.suite.lifecycle.CryptoLifecycleComponent
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [SigningKeysPersistenceProvider::class])
class KafkaSigningKeysPersistenceProvider : SigningKeysPersistenceProvider, Lifecycle, CryptoLifecycleComponent {
    companion object {
        private val logger = contextLogger()
    }

    @Volatile
    @Reference(service = SubscriptionFactory::class)
    lateinit var subscriptionFactory: SubscriptionFactory

    @Volatile
    @Reference(service = PublisherFactory::class)
    lateinit var publisherFactory: PublisherFactory

    private var impl: Impl? = null

    override var isRunning: Boolean = false

    override fun start() {
        logger.info("Starting...")
        isRunning = true
    }

    override fun stop() {
        logger.info("Stopping...")
        close()
        isRunning = false
    }

    override fun handleConfigEvent(config: CryptoLibraryConfig) {
        if(!isRunning) {
            throw IllegalStateException("The provider haven't been started yet.")
        }
        logger.info("Received new configuration...")
        instantiate(config)
    }

    private fun instantiate(config: CryptoLibraryConfig) {
        val currentImpl = impl
        impl = Impl(
            subscriptionFactory,
            publisherFactory,
            config
        )
        currentImpl?.closeGracefully()
    }

    override val name: String = "kafka"

    override fun getInstance(
        tenantId: String,
        mutator: KeyValueMutator<SigningKeysRecord, SigningKeysRecord>
    ): KeyValuePersistence<SigningKeysRecord, SigningKeysRecord> =
        impl?.getInstance(tenantId, mutator)
            ?: throw IllegalStateException("The provider haven't been initialised yet.")

    override fun close() {
        val current = impl
        impl = null
        current?.closeGracefully()
    }

    private class Impl(
        subscriptionFactory: SubscriptionFactory,
        publisherFactory: PublisherFactory,
        private val config: CryptoLibraryConfig
    ) : AutoCloseable {
        private val processor = KafkaSigningKeysPersistenceProcessor(
            subscriptionFactory = subscriptionFactory,
            publisherFactory = publisherFactory
        )

        fun getInstance(
            tenantId: String,
            mutator: KeyValueMutator<SigningKeysRecord, SigningKeysRecord>
        ): KeyValuePersistence<SigningKeysRecord, SigningKeysRecord> {
            logger.info("Getting Kafka persistence instance for tenant={}", tenantId)
            val cfg = config.signingPersistence
            return KafkaKeyValuePersistence(
                processor = processor,
                expireAfterAccessMins = cfg.expireAfterAccessMins,
                maximumSize = cfg.maximumSize,
                mutator = mutator
            )
        }

        override fun close() {
            processor.closeGracefully()
        }
    }
}