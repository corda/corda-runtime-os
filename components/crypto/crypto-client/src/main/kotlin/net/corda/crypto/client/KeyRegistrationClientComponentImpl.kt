package net.corda.crypto.client

import net.corda.crypto.CryptoPublishResult
import net.corda.crypto.HSMLabelMapComponent
import net.corda.crypto.KeyRegistrationClient
import net.corda.crypto.KeyRegistrationClientComponent
import net.corda.crypto.component.lifecycle.AbstractComponent
import net.corda.crypto.impl.closeGracefully
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [KeyRegistrationClientComponent::class])
class KeyRegistrationClientComponentImpl(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory
) : AbstractComponent<KeyRegistrationClientComponentImpl.Resources>(
    coordinatorFactory,
    LifecycleCoordinatorName.forComponent<KeyRegistrationClientComponent>()
), KeyRegistrationClientComponent {
    companion object {
        const val CLIENT_ID = "crypto.registration.key"

        inline val Resources?.instance: KeyRegistrationClient
            get() = this?.registrar ?: throw IllegalStateException("The component haven't been initialised.")
    }

    @Volatile
    private lateinit var publisherFactory: PublisherFactory

    @Volatile
    private lateinit var labelMap: HSMLabelMapComponent

    @Reference(service = PublisherFactory::class)
    fun publisherFactoryRef(publisherFactory: PublisherFactory) {
        this.publisherFactory = publisherFactory
        createResources()
    }

    @Reference(service = HSMLabelMapComponent::class)
    fun labelMapRef(labelMap: HSMLabelMapComponent) {
        this.labelMap = labelMap
        createResources()
    }

    override fun generateKeyPair(
        tenantId: String,
        category: String,
        alias: String,
        context: Map<String, String>
    ): CryptoPublishResult =
        resources.instance.generateKeyPair(tenantId, category, alias, context)

    override fun allocateResources(): Resources = Resources(publisherFactory, labelMap)

    class Resources(
        publisherFactory: PublisherFactory,
        labelMap: HSMLabelMapComponent
    ) : AutoCloseable {
        private val publisher: Publisher = publisherFactory.createPublisher(PublisherConfig(CLIENT_ID))
        val registrar: KeyRegistrationClient = KeyRegistrationPublisher(publisher, labelMap)
        override fun close() {
            publisher.closeGracefully()
        }
    }
}