package net.corda.crypto.client.impl

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.client.HSMConfigurationClient
import net.corda.crypto.client.HSMRegistrationClient
import net.corda.crypto.component.impl.AbstractConfigurableComponent
import net.corda.data.crypto.config.HSMConfig
import net.corda.data.crypto.wire.hsm.configuration.HSMConfigurationRequest
import net.corda.data.crypto.wire.hsm.configuration.HSMConfigurationResponse
import net.corda.data.crypto.wire.hsm.registration.HSMRegistrationRequest
import net.corda.data.crypto.wire.hsm.registration.HSMRegistrationResponse
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.messaging.api.config.toMessagingConfig
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.schema.Schemas
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [HSMRegistrationClient::class])
class HSMConfigurationClientComponent @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory,
    @Reference(service = ConfigurationReadService::class)
    configurationReadService: ConfigurationReadService
) : AbstractConfigurableComponent<HSMConfigurationClientComponent.Impl>(
    coordinatorFactory,
    LifecycleCoordinatorName.forComponent<HSMConfigurationClient>(),
    configurationReadService,
    InactiveImpl(),
    setOf(
        LifecycleCoordinatorName.forComponent<ConfigurationReadService>()
    )
), HSMConfigurationClient {
    companion object {
        const val GROUP_NAME = "crypto.hsm.configuration"
        const val CLIENT_ID = "crypto.hsm.configuration"
    }

    interface Impl : AutoCloseable {
        val registrar: HSMConfigurationClientImpl
    }

    override fun createInactiveImpl(): Impl = InactiveImpl()

    override fun createActiveImpl(event: ConfigChangedEvent): Impl = ActiveImpl(publisherFactory, event)

    override fun putHSM(config: HSMConfig) =
        impl.registrar.putHSM(config)

    class InactiveImpl : Impl {
        override val registrar: HSMConfigurationClientImpl
            get() = throw IllegalStateException("The component is in illegal state.")

        override fun close() = Unit
    }

    class ActiveImpl(
        publisherFactory: PublisherFactory,
        event: ConfigChangedEvent
    ) : Impl {
        private val sender: RPCSender<HSMConfigurationRequest, HSMConfigurationResponse> =
            publisherFactory.createRPCSender(
                RPCConfig(
                    groupName = GROUP_NAME,
                    clientName = CLIENT_ID,
                    requestTopic = Schemas.Crypto.RPC_HSM_CONFIGURATION_MESSAGE_TOPIC,
                    requestType = HSMConfigurationRequest::class.java,
                    responseType = HSMConfigurationResponse::class.java
                ),
                event.config.toMessagingConfig()
            ).also { it.start() }

        override val registrar: HSMConfigurationClientImpl = HSMConfigurationClientImpl(sender)

        override fun close() {
            sender.close()
        }
    }
}