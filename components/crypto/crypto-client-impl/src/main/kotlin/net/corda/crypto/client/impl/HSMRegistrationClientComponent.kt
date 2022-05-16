package net.corda.crypto.client.impl

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.client.HSMRegistrationClient
import net.corda.crypto.component.impl.AbstractConfigurableComponent
import net.corda.data.crypto.wire.hsm.HSMInfo
import net.corda.data.crypto.wire.hsm.registration.HSMRegistrationRequest
import net.corda.data.crypto.wire.hsm.registration.HSMRegistrationResponse
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.messaging.api.config.toMessagingConfig
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.schema.Schemas
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
    LifecycleCoordinatorName.forComponent<HSMRegistrationClient>(),
    configurationReadService,
    InactiveImpl(),
    setOf(
        LifecycleCoordinatorName.forComponent<ConfigurationReadService>()
    )
), HSMRegistrationClient {
    companion object {
        const val GROUP_NAME = "crypto.hsm.registration"
        const val CLIENT_ID = "crypto.hsm.registration"
    }

    interface Impl : AutoCloseable {
        val registrar: HSMRegistrationClientImpl
    }

    override fun createInactiveImpl(): Impl = InactiveImpl()

    override fun createActiveImpl(event: ConfigChangedEvent): Impl = ActiveImpl(publisherFactory, event)

    override fun assignHSM(tenantId: String, category: String, context: Map<String, String>): HSMInfo =
        impl.registrar.assignHSM(tenantId, category, context)

    override fun assignSoftHSM(tenantId: String, category: String, context: Map<String, String>): HSMInfo =
        impl.registrar.assignSoftHSM(tenantId, category, context)

    override fun findHSM(tenantId: String, category: String): HSMInfo? =
        impl.registrar.findHSM(tenantId, category)

    class InactiveImpl : Impl {
        override val registrar: HSMRegistrationClientImpl
            get() = throw IllegalStateException("The component is in illegal state.")

        override fun close() = Unit
    }

    class ActiveImpl(
        publisherFactory: PublisherFactory,
        event: ConfigChangedEvent
    ) : Impl {
        private val sender: RPCSender<HSMRegistrationRequest, HSMRegistrationResponse> =
            publisherFactory.createRPCSender(
            RPCConfig(
                groupName = GROUP_NAME,
                clientName = CLIENT_ID,
                requestTopic = Schemas.Crypto.RPC_HSM_REGISTRATION_MESSAGE_TOPIC,
                requestType = HSMRegistrationRequest::class.java,
                responseType = HSMRegistrationResponse::class.java
            ),
            event.config.toMessagingConfig()
        ).also { it.start() }

        override val registrar: HSMRegistrationClientImpl = HSMRegistrationClientImpl(sender)

        override fun close() {
            sender.close()
        }
    }
}