package net.corda.messaging.mediator.factory

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.crypto.cipher.suite.PlatformDigestService
import net.corda.libs.configuration.SmartConfig
import net.corda.messagebus.api.producer.builder.CordaProducerBuilder
import net.corda.messaging.api.mediator.factory.MessagingClientFactoryFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.util.concurrent.Executor

/**
 * Factory for creating multi-source event mediator messaging clients.
 */
@Component(service = [MessagingClientFactoryFactory::class])
class MessagingClientFactoryFactoryImpl @Activate constructor(
    @Reference(service = CordaProducerBuilder::class)
    private val cordaProducerBuilder: CordaProducerBuilder,
    @Reference(service = CordaAvroSerializationFactory::class)
    private val cordaSerializationFactory: CordaAvroSerializationFactory,
    @Reference(service = PlatformDigestService::class)
    private val platformDigestService: PlatformDigestService
): MessagingClientFactoryFactory {
    override fun createMessageBusClientFactory(
        id: String,
        messageBusConfig: SmartConfig,
    ) = MessageBusClientFactory(
        id,
        messageBusConfig,
        cordaProducerBuilder,
    )

    override fun createRPCClientFactory(
        id: String,
        executor: Executor,
    ) = RPCClientFactory(
        id,
        cordaSerializationFactory,
        platformDigestService,
        executor,
    )
}
