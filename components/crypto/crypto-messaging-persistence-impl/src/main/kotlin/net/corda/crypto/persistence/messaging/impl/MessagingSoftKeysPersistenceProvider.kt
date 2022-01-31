package net.corda.crypto.persistence.messaging.impl

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.persistence.KeyValueMutator
import net.corda.crypto.persistence.KeyValuePersistence
import net.corda.crypto.persistence.CachedSoftKeysRecord
import net.corda.crypto.persistence.SoftKeysPersistenceProvider
import net.corda.crypto.persistence.config.CryptoPersistenceConfig
import net.corda.crypto.persistence.config.softPersistence
import net.corda.data.crypto.persistence.SoftKeysRecord
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.config.toMessagingConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.configuration.ConfigKeys.Companion.BOOT_CONFIG
import net.corda.schema.configuration.ConfigKeys.Companion.CRYPTO_CONFIG
import net.corda.schema.configuration.ConfigKeys.Companion.MESSAGING_CONFIG
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [SoftKeysPersistenceProvider::class])
class MessagingSoftKeysPersistenceProvider @Activate constructor(
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

    @Volatile
    private var configHandle: AutoCloseable? = null

    @Volatile
    private var registrationHandle: RegistrationHandle? = null

    @Volatile
    private var impl: Impl? = null

    override val isRunning: Boolean get() = lifecycleCoordinator.isRunning

    override fun start() {
        logger.info("Starting...")
        lifecycleCoordinator.start()
    }

    override fun stop() {
        logger.info("Stopping...")
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
                logger.info("Received start event, starting wait for UP event from dependencies.")
                registrationHandle?.close()
                registrationHandle = coordinator.followStatusChangesByName(
                    setOf(LifecycleCoordinatorName.forComponent<ConfigurationReadService>())
                )
            }
            is StopEvent -> {
                registrationHandle?.close()
                registrationHandle = null
                configHandle?.close()
                configHandle = null
                deleteResources()
            }
            is RegistrationStatusChangeEvent -> {
                if (event.status == LifecycleStatus.UP) {
                    logger.info("Registering for configuration updates.")
                    configHandle = configurationReadService.registerComponentForUpdates(
                        coordinator,
                        setOf(CRYPTO_CONFIG, MESSAGING_CONFIG, BOOT_CONFIG)
                    )
                } else {
                    configHandle?.close()
                    configHandle = null
                    deleteResources()
                    logger.info("Setting status DOWN.")
                    coordinator.updateStatus(LifecycleStatus.DOWN)
                }
            }
            is ConfigChangedEvent -> {
                createResources(event)
                logger.info("Setting status UP.")
                coordinator.updateStatus(LifecycleStatus.UP)
            }
            else -> {
                logger.warn("Unexpected event $event!")
            }
        }
    }

    private fun createResources(event: ConfigChangedEvent) {
        val messagingConfig = event.config.toMessagingConfig()
        val config = event.config.getValue(CRYPTO_CONFIG)
        val currentImpl = impl
        impl = Impl(
            subscriptionFactory,
            publisherFactory,
            config.softPersistence,
            messagingConfig
        )
        currentImpl?.close()
    }

    private fun deleteResources() {
        val current = impl
        impl = null
        current?.close()
    }

    private class Impl(
        subscriptionFactory: SubscriptionFactory,
        publisherFactory: PublisherFactory,
        private val config: CryptoPersistenceConfig,
        messagingConfig: SmartConfig
    ) : AutoCloseable {
        private val processor = MessagingSoftKeysPersistenceProcessor(
            subscriptionFactory = subscriptionFactory,
            publisherFactory = publisherFactory,
            messagingConfig = messagingConfig
        )

        fun getInstance(
            tenantId: String,
            mutator: KeyValueMutator<CachedSoftKeysRecord, SoftKeysRecord>
        ): KeyValuePersistence<CachedSoftKeysRecord, SoftKeysRecord> {
            logger.info("Getting Kafka persistence instance for tenant={}", tenantId)
            return MessagingKeyValuePersistence(
                processor = processor,
                expireAfterAccessMins = config.expireAfterAccessMins,
                maximumSize = config.maximumSize,
                mutator = mutator
            )
        }

        override fun close() {
            processor.close()
        }
    }
}