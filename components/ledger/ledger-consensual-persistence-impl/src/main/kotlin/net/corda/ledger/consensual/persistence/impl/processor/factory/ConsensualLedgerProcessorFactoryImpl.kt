package net.corda.ledger.consensual.persistence.impl.processor.factory

import net.corda.flow.external.events.responses.factory.ExternalEventResponseFactory
import net.corda.ledger.consensual.persistence.impl.processor.ConsensualLedgerMessageProcessor
import net.corda.ledger.consensual.persistence.impl.processor.ConsensualLedgerProcessorImpl
import net.corda.ledger.consensual.persistence.processor.ConsensualLedgerProcessor
import net.corda.ledger.consensual.persistence.processor.factory.ConsensualLedgerProcessorFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.persistence.common.EntitySandboxService
import net.corda.persistence.common.PayloadChecker
import net.corda.schema.Schemas
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.cipher.suite.DigestService
import net.corda.v5.cipher.suite.merkle.MerkleTreeProvider
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [ConsensualLedgerProcessorFactory::class])
@Suppress("LongParameterList")
class ConsensualLedgerProcessorFactoryImpl @Activate constructor(
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = EntitySandboxService::class)
    private val entitySandboxService: EntitySandboxService,
    @Reference(service = ExternalEventResponseFactory::class)
    private val externalEventResponseFactory: ExternalEventResponseFactory,
    @Reference(service = MerkleTreeProvider::class)
    private val merkleTreeProvider: MerkleTreeProvider,
    @Reference(service = DigestService::class)
    private val digestService: DigestService,
    @Reference(service = JsonMarshallingService::class)
    private val jsonMarshallingService: JsonMarshallingService
) : ConsensualLedgerProcessorFactory {
    companion object {
        internal const val GROUP_NAME = "persistence.ledger.processor"

    }

    override fun create(config: SmartConfig): ConsensualLedgerProcessor {
        val subscriptionConfig = SubscriptionConfig(GROUP_NAME, Schemas.Persistence.PERSISTENCE_LEDGER_PROCESSOR_TOPIC)

        val processor = ConsensualLedgerMessageProcessor(
            entitySandboxService,
            externalEventResponseFactory,
            merkleTreeProvider,
            digestService,
            jsonMarshallingService,
            PayloadChecker(config)::checkSize
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
