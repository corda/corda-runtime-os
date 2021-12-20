package net.corda.crypto.client

import net.corda.crypto.clients.CryptoPublishResult
import net.corda.crypto.clients.KeyRegistrarClient
import net.corda.crypto.component.lifecycle.AbstractComponent
import net.corda.crypto.impl.closeGracefully
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [KeyRegistrarClientComponent::class])
class KeyRegistrarClientComponentImpl(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory
) : AbstractComponent<KeyRegistrarClientComponentImpl.Resources>(
    coordinatorFactory,
    LifecycleCoordinatorName.forComponent<KeyRegistrarClientComponent>()
), KeyRegistrarClientComponent {
    companion object {
        const val CLIENT_ID = "crypto.registration.key"

        inline val Resources?.instance: KeyRegistrarClient
            get() = this?.registrar ?: throw IllegalStateException("The component haven't been initialised.")
    }

    private lateinit var publisherFactory: PublisherFactory

    @Reference(service = PublisherFactory::class)
    fun publisherFactoryRef(ref: PublisherFactory) {
        publisherFactory = ref
        createResources()
    }

    override fun generateKeyPair(
        tenantId: String,
        category: String,
        alias: String,
        context: Map<String, String>
    ): CryptoPublishResult =
        resources.instance.generateKeyPair(tenantId, category, alias, context)

    override fun allocateResources(): Resources = Resources(publisherFactory)

    class Resources(
        publisherFactory: PublisherFactory
    ) : AutoCloseable {
        private val publisher: Publisher = publisherFactory.createPublisher(PublisherConfig(CLIENT_ID))
        val registrar: KeyRegistrarClient = KeyRegistrarPublisher(publisher)
        override fun close() {
            publisher.closeGracefully()
        }
    }
}