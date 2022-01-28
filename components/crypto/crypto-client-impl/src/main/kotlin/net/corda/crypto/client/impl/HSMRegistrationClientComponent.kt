package net.corda.crypto.client.impl

import net.corda.crypto.CryptoPublishResult
import net.corda.crypto.HSMRegistrationClient
import net.corda.data.crypto.config.HSMConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [HSMRegistrationClient::class])
class HSMRegistrationClientComponent @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory
) : AbstractComponent<HSMRegistrationClientComponent.Resources>(
        coordinatorFactory,
        LifecycleCoordinatorName.forComponent<HSMRegistrationClientComponent>()
), HSMRegistrationClient {
    companion object {
        const val CLIENT_ID = "crypto.registration.hsm"

        private inline val Resources?.instance: HSMRegistrationClientImpl
            get() = this?.registrar ?: throw IllegalStateException("The component haven't been initialised.")
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
            publisher.close()
        }
    }
}