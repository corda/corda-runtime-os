package net.corda.crypto.client

import net.corda.crypto.clients.CryptoPublishResult
import net.corda.crypto.clients.HSMRegistrarClient
import net.corda.crypto.impl.closeGracefully
import net.corda.data.crypto.config.HSMConfig
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

@Component(service = [HSMRegistrarClientComponent::class])
class HSMRegistrarClientComponentImpl(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory
) : HSMRegistrarClientComponent {
    companion object {
        const val CLIENT_ID = "crypto.registration.hsm"

        private val logger = contextLogger()

        inline val PublisherHolder?.instance: HSMRegistrarClient
            get() = this?.registrar ?: throw IllegalStateException("The component haven't been initialised.")
    }

    private var publisher: PublisherHolder? = null

    private val coordinator = coordinatorFactory.createCoordinator<HSMRegistrarClientComponentImpl> { event, _ ->
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
        logger.info("Starting HSM Registrar Client")
        coordinator.start()
    }

    override fun stop() {
        logger.info("Stopping HSM Registrar Client")
        coordinator.stop()
        closeResources()
    }

    override fun putHSM(config: HSMConfig): CryptoPublishResult =
        publisher.instance.putHSM(config)

    override fun assignHSM(tenantId: String, category: String, defaultSignatureScheme: String): CryptoPublishResult =
        publisher.instance.assignHSM(tenantId, category, defaultSignatureScheme)

    override fun assignSoftHSM(
        tenantId: String,
        category: String,
        passphrase: String,
        defaultSignatureScheme: String
    ): CryptoPublishResult =
        publisher.instance.assignSoftHSM(tenantId, category, passphrase, defaultSignatureScheme)

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
        logger.info("Creating HSM registrar publisher")
        publisher = PublisherHolder(publisherFactory)
    }

    private fun closeResources() {
        publisher?.closeGracefully()
    }

    class PublisherHolder(
        publisherFactory: PublisherFactory
    ) : AutoCloseable {
        private val publisher: Publisher = publisherFactory.createPublisher(PublisherConfig(CLIENT_ID))
        val registrar: HSMRegistrarPublisher = HSMRegistrarPublisher(publisher)
        override fun close() {
            publisher.closeGracefully()
        }
    }
}