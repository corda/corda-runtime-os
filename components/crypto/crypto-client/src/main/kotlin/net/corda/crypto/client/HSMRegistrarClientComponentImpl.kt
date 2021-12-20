package net.corda.crypto.client

import net.corda.crypto.clients.CryptoPublishResult
import net.corda.crypto.clients.HSMRegistrarClient
import net.corda.crypto.component.lifecycle.AbstractComponent
import net.corda.crypto.impl.closeGracefully
import net.corda.data.crypto.config.HSMConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [HSMRegistrarClientComponent::class])
class HSMRegistrarClientComponentImpl(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory
) : AbstractComponent<HSMRegistrarClientComponentImpl.Resources>(
    coordinatorFactory,
    LifecycleCoordinatorName.forComponent<HSMRegistrarClientComponent>()
), HSMRegistrarClientComponent {
    companion object {
        const val CLIENT_ID = "crypto.registration.hsm"

        inline val Resources?.instance: HSMRegistrarClient
            get() = this?.registrar ?: throw IllegalStateException("The component haven't been initialised.")
    }

    private lateinit var publisherFactory: PublisherFactory

    @Reference(service = PublisherFactory::class)
    fun putPublisherFactory(publisherFactory: PublisherFactory) {
        this.publisherFactory = publisherFactory
        createResources()
    }

    override fun putHSM(config: HSMConfig): CryptoPublishResult =
        resources.instance.putHSM(config)

    override fun assignHSM(tenantId: String, category: String, defaultSignatureScheme: String): CryptoPublishResult =
        resources.instance.assignHSM(tenantId, category, defaultSignatureScheme)

    override fun assignSoftHSM(
        tenantId: String,
        category: String,
        passphrase: String,
        defaultSignatureScheme: String
    ): CryptoPublishResult =
        resources.instance.assignSoftHSM(tenantId, category, passphrase, defaultSignatureScheme)


    override fun allocateResources(): Resources = Resources(publisherFactory)

    class Resources(
        publisherFactory: PublisherFactory
    ) : AutoCloseable {
        private val publisher: Publisher = publisherFactory.createPublisher(PublisherConfig(CLIENT_ID))
        val registrar: HSMRegistrarPublisher = HSMRegistrarPublisher(publisher)
        override fun close() {
            publisher.closeGracefully()
        }
    }
}