package net.corda.entityprocessor.impl

import java.nio.ByteBuffer
import net.corda.entityprocessor.EntityProcessorFactory
import net.corda.entityprocessor.FlowPersistenceProcessor
import net.corda.entityprocessor.impl.internal.EntityMessageProcessor
import net.corda.entityprocessor.impl.internal.EntitySandboxService
import net.corda.entityprocessor.impl.internal.exceptions.KafkaMessageSizeException
import net.corda.flow.external.events.responses.factory.ExternalEventResponseFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas
import net.corda.schema.configuration.MessagingConfig
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Suppress("UNUSED")
@Component(service = [EntityProcessorFactory::class])
class EntityProcessorFactoryImpl @Activate constructor(
    @Reference
    private val subscriptionFactory: SubscriptionFactory,
    @Reference
    private val publisherFactory: PublisherFactory,
    @Reference
    private val entitySandboxService: EntitySandboxService,
    @Reference(service = ExternalEventResponseFactory::class)
    private val externalEventResponseFactory: ExternalEventResponseFactory
) : EntityProcessorFactory {
    companion object {
        internal const val GROUP_NAME = "virtual.node.entity.processor"
        private const val CORDA_MESSAGE_OVERHEAD = 1024
    }

    class PayloadChecker(private val maxPayloadSize: Int) {
        companion object {
            private val log = contextLogger()
        }

        fun checkSize(
            bytes: ByteBuffer
        ): ByteBuffer {
            val kb = bytes.array().size / 1024
            if (bytes.array().size > maxPayloadSize) {
                throw KafkaMessageSizeException("Payload $kb kb, exceeds max Kafka payload size ${maxPayloadSize / (1024)} kb")
            }
            if(log.isDebugEnabled)
                log.debug("Payload $kb kb < max Kafka payload size ${maxPayloadSize / (1024)} kb")
            return bytes
        }
    }

    override fun create(config: SmartConfig): FlowPersistenceProcessor {
        val subscriptionConfig = SubscriptionConfig(GROUP_NAME, Schemas.VirtualNode.ENTITY_PROCESSOR)
        // max allowed msg size minus headroom for wrapper message
        val maxPayLoadSize = config.getInt(MessagingConfig.MAX_ALLOWED_MSG_SIZE) - CORDA_MESSAGE_OVERHEAD
        val processor = EntityMessageProcessor(
            entitySandboxService,
            externalEventResponseFactory,
            PayloadChecker(maxPayLoadSize)::checkSize
        )

        val subscription = subscriptionFactory.createDurableSubscription(
            subscriptionConfig,
            processor,
            config,
            null
        )

        return FlowPersistenceProcessorImpl(subscription)
    }

}
