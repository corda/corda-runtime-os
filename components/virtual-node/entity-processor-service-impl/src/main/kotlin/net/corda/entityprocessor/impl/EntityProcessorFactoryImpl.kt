package net.corda.entityprocessor.impl

import net.corda.entityprocessor.FlowPersistenceProcessor
import net.corda.entityprocessor.EntityProcessorFactory
import net.corda.entityprocessor.impl.internal.EntityMessageProcessor
import net.corda.entityprocessor.impl.internal.EntitySandboxService
import net.corda.entityprocessor.impl.internal.exceptions.KafkaMessageSizeException
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas
import net.corda.utilities.time.UTCClock
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.nio.ByteBuffer

@Suppress("UNUSED")
@Component(service = [EntityProcessorFactory::class])
class EntityProcessorFactoryImpl @Activate constructor(
    @Reference
    private val subscriptionFactory: SubscriptionFactory,
    @Reference
    private val publisherFactory: PublisherFactory,
    @Reference
    private val entitySandboxService: EntitySandboxService
) : EntityProcessorFactory {
    companion object {
        internal const val GROUP_NAME = "virtual.node.entity.processor"

        private val log = contextLogger()

        // Temporary until we look this up from Kafka/config.
        private const val MAX_BYTES = 6 * 1024 * 1024
        fun payloadSizeCheck(
            bytes: ByteBuffer
        ): ByteBuffer {
            val kb = bytes.array().size / 1024
            val maxKb = MAX_BYTES / (1024)
            if (bytes.array().size > MAX_BYTES) {
                throw KafkaMessageSizeException("Payload $kb kb, exceeds max Kafka payload size $maxKb kb")
            }
            log.debug("Payload $kb kb < max Kafka payload size $maxKb kb")
            return bytes
        }
    }

    override fun create(config: SmartConfig): FlowPersistenceProcessor {
        val subscriptionConfig = SubscriptionConfig(GROUP_NAME, Schemas.VirtualNode.ENTITY_PROCESSOR)
        val processor = EntityMessageProcessor(entitySandboxService, UTCClock(), EntityProcessorFactoryImpl::payloadSizeCheck)

        val subscription = subscriptionFactory.createDurableSubscription(
            subscriptionConfig,
            processor,
            config,
            null
        )

        return FlowPersistenceProcessorImpl(subscription)
    }

}
