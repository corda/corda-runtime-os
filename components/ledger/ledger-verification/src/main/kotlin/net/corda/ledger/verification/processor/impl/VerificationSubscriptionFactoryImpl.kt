package net.corda.ledger.verification.processor.impl

import net.corda.ledger.verification.processor.VerificationSubscriptionFactory
import net.corda.ledger.utxo.contract.verification.VerifyContractsRequest
import net.corda.ledger.verification.processor.ResponseFactory
import net.corda.ledger.verification.sanbox.VerificationSandboxService
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [VerificationSubscriptionFactory::class])
class VerificationSubscriptionFactoryImpl @Activate constructor(
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = VerificationSandboxService::class)
    private val verificationSandboxService: VerificationSandboxService,
    @Reference(service = ResponseFactory::class)
    private val responseFactory: ResponseFactory
) : VerificationSubscriptionFactory {
    companion object {
        internal const val GROUP_NAME = "verification.ledger.processor"
    }

    override fun create(config: SmartConfig): Subscription<String, VerifyContractsRequest> {
        val subscriptionConfig = SubscriptionConfig(GROUP_NAME, Schemas.Verification.VERIFICATION_LEDGER_PROCESSOR_TOPIC)

        val processor = VerificationRequestProcessor(
            verificationSandboxService,
            VerificationRequestHandlerImpl(responseFactory),
            responseFactory
        )

        return subscriptionFactory.createDurableSubscription(
            subscriptionConfig,
            processor,
            config,
            null
        )
    }
}
