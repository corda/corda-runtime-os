package net.corda.flow.messaging.mediator

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.data.crypto.wire.ops.flow.FlowOpsRequest
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.output.FlowStatus
import net.corda.data.flow.state.checkpoint.Checkpoint
import net.corda.data.ledger.persistence.LedgerPersistenceRequest
import net.corda.data.ledger.utxo.token.selection.event.TokenPoolCacheEvent
import net.corda.data.persistence.EntityRequest
import net.corda.data.uniqueness.UniquenessCheckRequestAvro
import net.corda.flow.pipeline.factory.FlowEventProcessorFactory
import net.corda.ledger.utxo.verification.TransactionVerificationRequest
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.mediator.MediatorMessage
import net.corda.messaging.api.mediator.MessageRouter
import net.corda.messaging.api.mediator.RoutingDestination.Companion.routeTo
import net.corda.messaging.api.mediator.config.EventMediatorConfigBuilder
import net.corda.messaging.api.mediator.factory.MediatorConsumerFactoryFactory
import net.corda.messaging.api.mediator.factory.MessageRouterFactory
import net.corda.messaging.api.mediator.factory.MessagingClientFactoryFactory
import net.corda.messaging.api.mediator.factory.MultiSourceEventMediatorFactory
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.schema.Schemas.Flow.FLOW_EVENT_TOPIC
import net.corda.schema.Schemas.Flow.FLOW_MAPPER_EVENT_TOPIC
import net.corda.schema.Schemas.Flow.FLOW_STATUS_TOPIC
import net.corda.schema.Schemas.Services.TOKEN_CACHE_EVENT
import net.corda.schema.configuration.BootConfig.CRYPTO_WORKER_REST_ENDPOINT
import net.corda.schema.configuration.BootConfig.PERSISTENCE_WORKER_REST_ENDPOINT
import net.corda.schema.configuration.BootConfig.VERIFICATION_WORKER_REST_ENDPOINT
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [FlowEventMediatorFactory::class])
class FlowEventMediatorFactoryImpl @Activate constructor(
    @Reference(service = FlowEventProcessorFactory::class)
    private val flowEventProcessorFactory: FlowEventProcessorFactory,
    @Reference(service = MediatorConsumerFactoryFactory::class)
    private val mediatorConsumerFactoryFactory: MediatorConsumerFactoryFactory,
    @Reference(service = MessagingClientFactoryFactory::class)
    private val messagingClientFactoryFactory: MessagingClientFactoryFactory,
    @Reference(service = MultiSourceEventMediatorFactory::class)
    private val eventMediatorFactory: MultiSourceEventMediatorFactory,
    @Reference(service = CordaAvroSerializationFactory::class)
    cordaAvroSerializationFactory: CordaAvroSerializationFactory,
) : FlowEventMediatorFactory {
    companion object {
        private const val CONSUMER_GROUP = "FlowEventConsumer"
        private const val MESSAGE_BUS_CLIENT = "MessageBusClient"
        private const val RPC_CLIENT = "RpcClient"
    }

    private val deserializer = cordaAvroSerializationFactory.createAvroDeserializer({}, Any::class.java)

    override fun create(
        configs: Map<String, SmartConfig>,
        messagingConfig: SmartConfig,
    ) = eventMediatorFactory.create(
        createEventMediatorConfig(
            messagingConfig,
            flowEventProcessorFactory.create(configs),
        )
    )

    private fun createEventMediatorConfig(
        messagingConfig: SmartConfig,
        messageProcessor: StateAndEventProcessor<String, Checkpoint, FlowEvent>,
    ) = EventMediatorConfigBuilder<String, Checkpoint, FlowEvent>()
        .name("FlowEventMediator")
        .messagingConfig(messagingConfig)
        .consumerFactories(
            mediatorConsumerFactoryFactory.createMessageBusConsumerFactory(
                FLOW_EVENT_TOPIC, CONSUMER_GROUP, messagingConfig
            ),
        )
        .clientFactories(
            messagingClientFactoryFactory.createMessageBusClientFactory(
                MESSAGE_BUS_CLIENT, messagingConfig
            ),
        )
        .messageProcessor(messageProcessor)
        .messageRouterFactory(createMessageRouterFactory(messagingConfig))
        .build()

    private fun createMessageRouterFactory(messagingConfig: SmartConfig) = MessageRouterFactory { clientFinder ->
        val messageBusClient = clientFinder.find(MESSAGE_BUS_CLIENT)
        val rpcClient = clientFinder.find(RPC_CLIENT)

        fun rpcEndpoint(endpoint: String, path: String) = "${messagingConfig.getString(endpoint)}$path"

        MessageRouter { message ->
            when (val event = message.event()) {
                is EntityRequest -> routeTo(rpcClient, rpcEndpoint(PERSISTENCE_WORKER_REST_ENDPOINT, "/persistence"))
                is FlowMapperEvent -> routeTo(messageBusClient, FLOW_MAPPER_EVENT_TOPIC)
                is FlowOpsRequest -> routeTo(rpcClient, rpcEndpoint(CRYPTO_WORKER_REST_ENDPOINT, "/crypto"))
                is FlowStatus -> routeTo(messageBusClient, FLOW_STATUS_TOPIC)
                is LedgerPersistenceRequest -> routeTo(rpcClient, rpcEndpoint(PERSISTENCE_WORKER_REST_ENDPOINT, "/ledger"))
                is TokenPoolCacheEvent -> routeTo(messageBusClient, TOKEN_CACHE_EVENT)
                is TransactionVerificationRequest -> routeTo(rpcClient, rpcEndpoint(VERIFICATION_WORKER_REST_ENDPOINT, "/verification"))
                is UniquenessCheckRequestAvro -> routeTo(rpcClient, rpcEndpoint(VERIFICATION_WORKER_REST_ENDPOINT, "/uniqueness-checker"))
                else -> {
                    val eventType = event?.let { it::class.java }
                    throw IllegalStateException("No route defined for event type [$eventType]")
                }
            }
        }
    }

    /**
     * Deserializes message payload if it is a [ByteArray] (seems to be the case for external events).
     */
    private fun MediatorMessage<Any>.event(): Any? {
        val event = payload
        return if (event is ByteArray) {
            deserializer.deserialize(event)
        } else {
            event
        }
    }
}