package net.corda.entityprocessor.impl

import net.corda.data.flow.event.FlowEvent
import net.corda.data.persistence.EntityRequest
import net.corda.data.uniqueness.UniquenessCheckRequestAvro
import net.corda.entityprocessor.EntityProcessor
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.config.SyncRPCConfig
import org.osgi.service.component.annotations.Component

/**
 * Entity processor.
 * Starts the subscription, which in turn passes the messages to the
 * [net.corda.entityprocessor.impl.internal.EntityMessageProcessor].
 */
@Component(service = [EntityProcessor::class])
class EntityProcessorImpl(
    private val subscription: Subscription<String, EntityRequest>
) :

    EntityProcessor {
    private fun initialiseRpcSubscription() {
        val processor = UniquenessCheckRpcMessageProcessor(
            this,
            externalEventResponseFactory,
            UniquenessCheckRequestAvro::class.java,
            FlowEvent::class.java
        )
        lifecycleCoordinator.createManagedResource(RPC_SUBSCRIPTION) {
            val rpcConfig = SyncRPCConfig(SUBSCRIPTION_NAME, UNIQUENESS_CHECKER_PATH)
            subscriptionFactory.createHttpRPCSubscription(rpcConfig, processor).also {
                it.start()
            }
        }
    }
    override val isRunning: Boolean
        get() = subscription.isRunning

    override fun start() = subscription.start()

    // It is important to call `subscription.close()` rather than `subscription.stop()` as the latter does not remove
    // Lifecycle coordinator from the registry, causing it to appear there in `DOWN` state. This will in turn fail
    // overall Health check's `status` check.
    override fun stop() = subscription.close()
}
