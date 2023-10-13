package net.corda.ledger.verification.processor.impl

import net.corda.data.flow.event.FlowEvent
import net.corda.flow.external.events.responses.factory.ExternalEventResponseFactory
import net.corda.ledger.utxo.verification.TransactionVerificationRequest
import net.corda.ledger.verification.processor.VerificationSubscriptionFactory
import net.corda.ledger.verification.sandbox.VerificationSandboxService
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.subscription.RPCSubscription
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.config.SyncRPCConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.sandboxgroupcontext.CurrentSandboxGroupContext
import net.corda.schema.Schemas
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [VerificationSubscriptionFactory::class])
class VerificationSubscriptionFactoryImpl @Activate constructor(
    @Reference(service = CurrentSandboxGroupContext::class)
    private val currentSandboxGroupContext: CurrentSandboxGroupContext,
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = VerificationSandboxService::class)
    private val verificationSandboxService: VerificationSandboxService,
    @Reference(service = ExternalEventResponseFactory::class)
    private val responseFactory: ExternalEventResponseFactory
) : VerificationSubscriptionFactory {
    companion object {
        internal const val GROUP_NAME = "verification.ledger.processor"
        const val SUBSCRIPTION_NAME = "Verification"
        const val PATH = "/verification"
    }

    override fun create(config: SmartConfig): Subscription<String, TransactionVerificationRequest> {
        val subscriptionConfig = SubscriptionConfig(GROUP_NAME, Schemas.Verification.VERIFICATION_LEDGER_PROCESSOR_TOPIC)

        val processor = VerificationRequestProcessor(
            currentSandboxGroupContext,
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

    override fun createRpcSubscription(): RPCSubscription<TransactionVerificationRequest, FlowEvent> {
        val processor = VerificationRpcRequestProcessor(
            currentSandboxGroupContext,
            verificationSandboxService,
            VerificationRequestHandlerImpl(responseFactory),
            responseFactory,
            TransactionVerificationRequest::class.java,
            FlowEvent::class.java
        )
        val rpcConfig = SyncRPCConfig(SUBSCRIPTION_NAME, PATH)
        return subscriptionFactory.createHttpRPCSubscription(rpcConfig, processor)
    }

}
