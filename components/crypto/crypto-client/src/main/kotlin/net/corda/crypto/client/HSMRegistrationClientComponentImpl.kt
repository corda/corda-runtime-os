package net.corda.crypto.client

import net.corda.crypto.CryptoPublishResult
import net.corda.crypto.HSMRegistrationClient
import net.corda.crypto.HSMRegistrationClientComponent
import net.corda.crypto.component.lifecycle.AbstractComponent
import net.corda.crypto.impl.closeGracefully
import net.corda.data.crypto.config.HSMConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [HSMRegistrationClientComponent::class])
class HSMRegistrationClientComponentImpl :
    AbstractComponent<HSMRegistrationClientComponentImpl.Resources>(),
    HSMRegistrationClientComponent {
    companion object {
        const val CLIENT_ID = "crypto.registration.hsm"

        private inline val Resources?.instance: HSMRegistrationClient
            get() = this?.registrar ?: throw IllegalStateException("The component haven't been initialised.")
    }

    @Volatile
    @Reference(service = LifecycleCoordinatorFactory::class)
    lateinit var coordinatorFactory: LifecycleCoordinatorFactory

    @Volatile
    @Reference(service = PublisherFactory::class)
    lateinit var publisherFactory: PublisherFactory

    @Activate
    fun activate() {
        setup(
            coordinatorFactory,
            LifecycleCoordinatorName.forComponent<HSMRegistrationClientComponent>()
        )
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
        internal val registrar: HSMRegistrationClientImpl = HSMRegistrationClientImpl(publisher)
        override fun close() {
            publisher.closeGracefully()
        }
    }
}