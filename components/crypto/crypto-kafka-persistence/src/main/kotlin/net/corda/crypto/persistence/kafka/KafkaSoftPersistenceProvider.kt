package net.corda.crypto.persistence.kafka

import net.corda.crypto.component.persistence.SoftCryptoKeyRecord
import net.corda.crypto.component.persistence.SoftCryptoKeyRecordInfo
import net.corda.crypto.component.persistence.SoftPersistenceProvider
import net.corda.crypto.impl.closeGracefully
import net.corda.crypto.impl.config.softCryptoService
import net.corda.crypto.impl.persistence.KeyValueMutator
import net.corda.crypto.impl.persistence.KeyValuePersistence
import net.corda.lifecycle.Lifecycle
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.config.CryptoLibraryConfig
import net.corda.v5.cipher.suite.lifecycle.CryptoLifecycleComponent
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [SoftPersistenceProvider::class])
class KafkaSoftPersistenceProvider : SoftPersistenceProvider, Lifecycle, CryptoLifecycleComponent {
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
        mutator: KeyValueMutator<SoftCryptoKeyRecordInfo, SoftCryptoKeyRecord>
    ): KeyValuePersistence<SoftCryptoKeyRecordInfo, SoftCryptoKeyRecord> =
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
        private val proxy = KafkaSoftPersistenceProxy(
            subscriptionFactory = subscriptionFactory,
            publisherFactory = publisherFactory
        )

        fun getInstance(
            tenantId: String,
            mutator: KeyValueMutator<SoftCryptoKeyRecordInfo, SoftCryptoKeyRecord>
        ): KeyValuePersistence<SoftCryptoKeyRecordInfo, SoftCryptoKeyRecord> {
            logger.info("Getting Kafka persistence instance for tenant={}", tenantId)
            val cfg = config.softCryptoService
            return KafkaKeyValuePersistence(
                proxy = proxy,
                tenantId = tenantId,
                expireAfterAccessMins = cfg.expireAfterAccessMins,
                maximumSize = cfg.maximumSize,
                mutator = mutator
            )
        }

        override fun close() {
            proxy.closeGracefully()
        }
    }
}