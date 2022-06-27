package net.corda.crypto.client.hsm.impl

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.client.hsm.HSMConfigurationClient
import net.corda.crypto.component.impl.AbstractConfigurableComponent
import net.corda.data.crypto.wire.hsm.HSMCategoryInfo
import net.corda.data.crypto.wire.hsm.HSMInfo
import net.corda.data.crypto.wire.hsm.configuration.HSMConfigurationRequest
import net.corda.data.crypto.wire.hsm.configuration.HSMConfigurationResponse
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.libs.configuration.helper.getConfig
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.schema.Schemas
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [HSMConfigurationClient::class])
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
        const val GROUP_NAME = "crypto.hsm.configuration.client"
        const val CLIENT_ID = "crypto.hsm.configuration.client"
    }

    interface Impl : AutoCloseable {
        val registrar: HSMConfigurationClientImpl
    }

    override fun createInactiveImpl(): Impl = InactiveImpl()

    override fun createActiveImpl(event: ConfigChangedEvent): Impl = ActiveImpl(publisherFactory, event)

    override fun putHSM(info: HSMInfo, serviceConfig: ByteArray): String =
        impl.registrar.putHSM(info, serviceConfig)

    override fun linkCategories(configId: String, links: List<HSMCategoryInfo>) =
        impl.registrar.linkCategories(configId, links)

    override fun lookup(filter: Map<String, String>): List<HSMInfo> =
        impl.registrar.lookup(filter)

    override fun getLinkedCategories(configId: String): List<HSMCategoryInfo> =
        impl.registrar.getLinkedCategories(configId)

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
                event.config.getConfig(MESSAGING_CONFIG)
            ).also { it.start() }

        override val registrar: HSMConfigurationClientImpl = HSMConfigurationClientImpl(sender)

        override fun close() {
            sender.close()
        }
    }
}