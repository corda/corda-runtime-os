package net.corda.crypto.client

import net.corda.crypto.clients.CryptoPublishResult
import net.corda.crypto.clients.KeyRegistrarClient
import net.corda.crypto.impl.closeGracefully
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [KeyRegistrarClientComponent::class])
class KeyRegistrarClientComponentImpl(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory
) : KeyRegistrarClientComponent {
    companion object {
        const val CLIENT_ID = "crypto.registration.key"

        private val logger = contextLogger()

        inline val PublisherHolder?.instance: KeyRegistrarClient
            get() = this?.registrar ?: throw IllegalStateException("The component haven't been initialised.")
    }

    private var publisher: PublisherHolder? = null

    private val coordinator = coordinatorFactory.createCoordinator<KeyRegistrarClientComponentImpl> { event, _ ->
        handleCoordinatorEvent(event)
    }

    private lateinit var publisherFactory: PublisherFactory

    @Reference(service = PublisherFactory::class)
    fun publisherFactoryRef(ref: PublisherFactory) {
        publisherFactory = ref
        if (isRunning) {
            createResources()
        }
    }

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        logger.info("Starting Key Registrar Client")
        coordinator.start()
    }

    override fun stop() {
        logger.info("Stopping Key Registrar Client")
        coordinator.stop()
        closeResources()
    }


    override fun generateKeyPair(
        tenantId: String,
        category: String,
        alias: String,
        context: Map<String, String>
    ): CryptoPublishResult =
        publisher.instance.generateKeyPair(tenantId, category, alias, context)

    private fun handleCoordinatorEvent(event: LifecycleEvent) {
        logger.info("LifecycleEvent received: $event")
        when (event) {
            is RegistrationStatusChangeEvent -> {
                // No need to check what registration this is as there is only one.
                if (event.status == LifecycleStatus.UP) {
                    createResources()
                } else {
                    closeResources()
                }
            }
        }
    }

    private fun createResources() {
        publisher?.closeGracefully()
        logger.info("Creating Key registrar publisher")
        publisher = PublisherHolder(publisherFactory)
    }

    private fun closeResources() {
        publisher?.closeGracefully()
    }

    class PublisherHolder(
        publisherFactory: PublisherFactory
    ) : AutoCloseable {
        private val publisher: Publisher = publisherFactory.createPublisher(PublisherConfig(CLIENT_ID))
        val registrar: KeyRegistrarClient = KeyRegistrarPublisher(publisher)
        override fun close() {
            publisher.closeGracefully()
        }
    }
}