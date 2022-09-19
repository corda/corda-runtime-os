package net.corda.ledger.persistence.impl

import java.nio.ByteBuffer
import net.corda.ledger.persistence.impl.internal.ConsensualLedgerMessageProcessor
import net.corda.persistence.common.EntitySandboxService
import net.corda.flow.external.events.responses.factory.ExternalEventResponseFactory
import net.corda.ledger.persistence.ConsensualLedgerProcessor
import net.corda.ledger.persistence.ConsensualLedgerProcessorFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.persistence.common.exceptions.KafkaMessageSizeException
import net.corda.schema.Schemas
import net.corda.schema.configuration.MessagingConfig
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.DigestService
import net.corda.v5.crypto.merkle.MerkleTreeFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [ConsensualLedgerProcessorFactory::class])
class ConsensualLedgerProcessorFactoryImpl @Activate constructor(
    @Reference
    private val subscriptionFactory: SubscriptionFactory,
    @Reference
    private val entitySandboxService: EntitySandboxService,
    @Reference(service = ExternalEventResponseFactory::class)
    private val externalEventResponseFactory: ExternalEventResponseFactory,
    @Reference(service = MerkleTreeFactory::class)
    private val merkleTreeFactory: MerkleTreeFactory,
    @Reference(service = DigestService::class)
    private val digestService: DigestService
) : ConsensualLedgerProcessorFactory {
    companion object {
        internal const val GROUP_NAME = "virtual.node.ledger.processor"
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

    override fun create(config: SmartConfig): ConsensualLedgerProcessor {
        // TODO
        val subscriptionConfig = SubscriptionConfig(GROUP_NAME, Schemas.VirtualNode.ENTITY_PROCESSOR)
        // max allowed msg size minus headroom for wrapper message
        val maxPayLoadSize = config.getInt(MessagingConfig.MAX_ALLOWED_MSG_SIZE) - CORDA_MESSAGE_OVERHEAD
        val processor = ConsensualLedgerMessageProcessor(
            entitySandboxService,
            externalEventResponseFactory,
            merkleTreeFactory,
            digestService,
            PayloadChecker(maxPayLoadSize)::checkSize
        )

        val subscription = subscriptionFactory.createDurableSubscription(
            subscriptionConfig,
            processor,
            config,
            null
        )

        return ConsensualLedgerProcessorImpl(subscription)
    }

}
