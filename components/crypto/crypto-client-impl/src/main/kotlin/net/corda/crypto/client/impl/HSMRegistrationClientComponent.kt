package net.corda.crypto.client.impl

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.client.CryptoPublishResult
import net.corda.crypto.client.HSMRegistrationClient
import net.corda.crypto.component.impl.AbstractConfigurableComponent
import net.corda.data.crypto.config.HSMConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.messaging.api.config.toMessagingConfig
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
    private val publisherFactory: PublisherFactory,
    @Reference(service = ConfigurationReadService::class)
    configurationReadService: ConfigurationReadService
) : AbstractConfigurableComponent<HSMRegistrationClientComponent.Impl>(
    coordinatorFactory,
    LifecycleCoordinatorName.forComponent<HSMRegistrationClientComponent>(),
    configurationReadService,
    InactiveImpl(),
    setOf(
        LifecycleCoordinatorName.forComponent<ConfigurationReadService>()
    )
), HSMRegistrationClient {
    companion object {
        const val CLIENT_ID = "crypto.registration.hsm"
    }

    interface Impl : AutoCloseable {
        val registrar: HSMRegistrationClientImpl
    }

    override fun createInactiveImpl(): Impl = InactiveImpl()

    override fun createActiveImpl(event: ConfigChangedEvent): Impl = ActiveImpl(publisherFactory, event)

    override fun putHSM(config: HSMConfig): CryptoPublishResult =
        impl.registrar.putHSM(config)

    override fun assignHSM(tenantId: String, category: String, defaultSignatureScheme: String): CryptoPublishResult =
        impl.registrar.assignHSM(tenantId, category, defaultSignatureScheme)

    override fun assignSoftHSM(
        tenantId: String,
        category: String,
        passphrase: String,
        defaultSignatureScheme: String
    ): CryptoPublishResult =
        impl.registrar.assignSoftHSM(tenantId, category, passphrase, defaultSignatureScheme)

    class InactiveImpl : Impl {
        override val registrar: HSMRegistrationClientImpl
            get() = throw IllegalStateException("The component is in illegal state.")

        override fun close() = Unit
    }

    class ActiveImpl(
        publisherFactory: PublisherFactory,
        event: ConfigChangedEvent
    ) : Impl {
        private val publisher: Publisher = publisherFactory.createPublisher(
            PublisherConfig(CLIENT_ID),
            event.config.toMessagingConfig()
        )
        override val registrar: HSMRegistrationClientImpl = HSMRegistrationClientImpl(publisher)
        override fun close() {
            publisher.close()
        }
    }
}