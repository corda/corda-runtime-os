package net.corda.crypto.persistence.kafka

import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.component.persistence.KeyValueMutator
import net.corda.crypto.component.persistence.KeyValuePersistence
import net.corda.crypto.component.persistence.CachedSoftKeysRecord
import net.corda.crypto.component.persistence.SoftKeysPersistenceProvider
import net.corda.crypto.component.persistence.config.CryptoPersistenceConfig
import net.corda.crypto.component.persistence.config.softPersistence
import net.corda.crypto.impl.LifecycleDependenciesTracker
import net.corda.crypto.impl.LifecycleDependenciesTracker.Companion.track
import net.corda.crypto.impl.closeGracefully
import net.corda.data.crypto.persistence.SoftKeysRecord
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.configuration.ConfigKeys.Companion.CRYPTO_CONFIG
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [SoftKeysPersistenceProvider::class])
class KafkaSoftKeysPersistenceProvider @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = SubscriptionFactory::class)
    val subscriptionFactory: SubscriptionFactory,
    @Reference(service = PublisherFactory::class)
    val publisherFactory: PublisherFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService
) : SoftKeysPersistenceProvider {
    companion object {
        private val logger = contextLogger()
    }

    private val lifecycleCoordinator =
        coordinatorFactory.createCoordinator<SoftKeysPersistenceProvider>(::eventHandler)

    private var configHandle: AutoCloseable? = null

    private var tracker: LifecycleDependenciesTracker? = null

    private var impl: Impl? = null

    override val isRunning: Boolean get() = lifecycleCoordinator.isRunning

    override fun start() {
        logger.info("Starting...")
        lifecycleCoordinator.start()
        lifecycleCoordinator.postEvent(StartEvent())
    }

    override fun stop() {
        logger.info("Stopping...")
        lifecycleCoordinator.postEvent(StopEvent())
        lifecycleCoordinator.stop()
    }

    override fun getInstance(
        tenantId: String,
        mutator: KeyValueMutator<CachedSoftKeysRecord, SoftKeysRecord>
    ): KeyValuePersistence<CachedSoftKeysRecord, SoftKeysRecord> =
        impl?.getInstance(tenantId, mutator)
            ?: throw IllegalStateException("The provider haven't been initialised yet.")

    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        logger.info("Received event {}", event)
        when (event) {
            is StartEvent -> {
                logger.info("Received start event, waiting for UP event from dependencies.")
                tracker?.close()
                tracker = lifecycleCoordinator.track(ConfigurationReadService::class.java)
            }
            is StopEvent -> {
                configHandle?.close()
                configHandle = null
                impl?.close()
                impl = null
            }
            is RegistrationStatusChangeEvent -> {
                if (tracker?.areUpAfter(event, coordinator) == true) {
                    logger.info("Registering for configuration updates.")
                    configHandle = configurationReadService.registerForUpdates(::onConfigChange)
                } else {
                    configHandle?.close()
                }
            }
            is NewConfigurationReceivedEvent -> {
                createResources(event.config)
                setStatusUp()
            }
            else -> {
                logger.error("Unexpected event $event!")
            }
        }
    }

    private fun onConfigChange(changedKeys: Set<String>, config: Map<String, SmartConfig>) {
        logger.info("Received configuration update event, changedKeys: $changedKeys")
        if (CRYPTO_CONFIG in changedKeys) {
            lifecycleCoordinator.postEvent(NewConfigurationReceivedEvent(config[CRYPTO_CONFIG]!!))
        }
    }

    private fun createResources(config: SmartConfig) {
        val currentImpl = impl
        impl = Impl(subscriptionFactory, publisherFactory, config.softPersistence)
        currentImpl?.close()
    }

    private fun setStatusUp() {
        logger.info("Setting status UP.")
        lifecycleCoordinator.updateStatus(LifecycleStatus.UP)
    }

    private class Impl(
        subscriptionFactory: SubscriptionFactory,
        publisherFactory: PublisherFactory,
        private val config: CryptoPersistenceConfig
    ) : AutoCloseable {
        private val processor = KafkaSoftKeysPersistenceProcessor(
            subscriptionFactory = subscriptionFactory,
            publisherFactory = publisherFactory
        )

        fun getInstance(
            tenantId: String,
            mutator: KeyValueMutator<CachedSoftKeysRecord, SoftKeysRecord>
        ): KeyValuePersistence<CachedSoftKeysRecord, SoftKeysRecord> {
            logger.info("Getting Kafka persistence instance for tenant={}", tenantId)
            return KafkaKeyValuePersistence(
                processor = processor,
                expireAfterAccessMins = config.expireAfterAccessMins,
                maximumSize = config.maximumSize,
                mutator = mutator
            )
        }

        override fun close() {
            processor.closeGracefully()
        }
    }
}